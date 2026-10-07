"use strict";

/**
 * A dependency-free linter for this package.
 *
 * Why not ESLint: this package ships to users via `npx`, and a linter is not a
 * reason to grow its dependency tree or slow down `npm install`. Node can parse
 * the files itself, and the rules below are the ones that have actually caught
 * bugs in this codebase.
 *
 * What it checks:
 *  1. Every `.js` file parses (via `node --check`).
 *  2. No duplicated `module.exports` in one file. This is silent and costly:
 *     the last one wins, so an earlier export simply disappears.
 *  3. No `require` of a module that does not exist relative to the file. A
 *     typo here only fails when that line runs, which may be a rare branch.
 *  4. No leftover debug output (`console.log` outside `bin/`, which is a CLI
 *     and prints by design, and outside the `scripts/` probes).
 *  5. No `TODO`/`FIXME` without a reason, and no committed merge markers.
 *
 * Exits non-zero on any finding so it can gate CI.
 */

const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");

const ROOT = path.resolve(__dirname, "..");

/** Directories never worth linting. */
const SKIP_DIRS = new Set(["node_modules", ".git", "coverage"]);

/** Recursively list `.js` files under [dir]. */
function jsFiles(dir) {
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (SKIP_DIRS.has(entry.name)) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...jsFiles(full));
    else if (entry.name.endsWith(".js")) out.push(full);
  }
  return out;
}

const findings = [];
function report(file, line, rule, message) {
  findings.push({ file: path.relative(ROOT, file), line, rule, message });
}

/** Rule 1: the file must parse. */
function checkParses(file) {
  try {
    execFileSync(process.execPath, ["--check", file], { stdio: "pipe" });
  } catch (e) {
    const detail = (e.stderr || "").toString().split("\n").slice(0, 4).join(" ").trim();
    report(file, 0, "parse", detail || "failed to parse");
    return false;
  }
  return true;
}

/** Rules 2-4: source-level checks. */
function checkSource(file, source) {
  const lines = source.split(/\r?\n/);
  const rel = path.relative(ROOT, file);

  // Rule 2: a second module.exports silently discards the first.
  const exportLines = lines
    .map((l, i) => (/^\s*module\.exports\s*=/.test(l) ? i + 1 : 0))
    .filter(Boolean);
  if (exportLines.length > 1) {
    report(
      file,
      exportLines[1],
      "duplicate-exports",
      `${exportLines.length} module.exports assignments; the last wins and the others are lost`,
    );
  }

  lines.forEach((line, idx) => {
    const n = idx + 1;

    // Rule 5: merge conflict markers.
    if (/^<{7}|^={7}$|^>{7}/.test(line)) {
      report(file, n, "conflict-marker", "unresolved merge conflict");
    }

    // Rule 3: relative requires must resolve.
    const req = line.match(/require\(\s*["'](\.[^"']*)["']\s*\)/);
    if (req) {
      const target = path.resolve(path.dirname(file), req[1]);
      const ok =
        fs.existsSync(target) ||
        fs.existsSync(target + ".js") ||
        fs.existsSync(path.join(target, "index.js"));
      if (!ok) {
        report(file, n, "missing-module", `cannot resolve ${req[1]}`);
      }
    }

    // Rule 4: stray console.log in library code.
    //
    // Three places print on purpose and are exempt:
    //   bin/     — the CLI's whole job is to print for a human
    //   scripts/ — diagnostics
    //   test/    — the `probe-*` / `harness-*` files are manual harnesses that
    //              report by printing; they are not part of `npm test`
    const inBin = rel.startsWith("bin" + path.sep);
    const inScripts = rel.startsWith("scripts" + path.sep);
    const isProbe =
      rel.startsWith("test" + path.sep) &&
      /(^|[\\/])(probe|harness)-/.test(rel);
    if (!inBin && !inScripts && !isProbe && /console\.log\(/.test(line)) {
      report(file, n, "stray-log", "console.log in library code; use a logger or remove");
    }

    // Rule 5b: a TODO must say why, not just that.
    const todo = line.match(/\b(TODO|FIXME|XXX)\b(.*)$/);
    if (todo && todo[2].trim().length < 10) {
      report(file, n, "bare-todo", `${todo[1]} without an explanation`);
    }
  });
}

const files = jsFiles(ROOT);
for (const file of files) {
  if (!checkParses(file)) continue;
  checkSource(file, fs.readFileSync(file, "utf8"));
}

if (findings.length === 0) {
  console.log(`lint: ${files.length} files OK`);
  process.exit(0);
}

for (const f of findings) {
  console.log(`${f.file}:${f.line}  ${f.rule}  ${f.message}`);
}
console.log(`\nlint: ${findings.length} problem(s) in ${files.length} files`);
process.exit(1);
