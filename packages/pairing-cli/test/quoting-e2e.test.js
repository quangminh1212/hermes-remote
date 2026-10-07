"use strict";

/**
 * End-to-end quoting tests for the Windows .cmd shim path.
 *
 * Why this file exists
 * --------------------
 * `quoteForCmd()` is hand-rolled because a global npm CLI on Windows is a .cmd
 * shim, which Node can only run with `shell: true` - and `shell: true` with an
 * args array concatenates unescaped (DEP0190). The original unit test asserted
 * the *shape* of quoteForCmd()'s output but never ran it through a real .cmd,
 * so it recorded `"%USERNAME%" -> "%%USERNAME%%"` as correct.
 *
 * It was not. `%%` is only collapsed to `%` by the batch-file parser; passed
 * through `cmd /c` from Node there is no batch context, so the doubling reaches
 * the child verbatim. Proven with a real shim: the model was asked to echo
 * "100% ^50! done" and answered "100%% ^50^^! done", because that is genuinely
 * the string it received.
 *
 * These tests therefore do the thing the old one did not: build a throwaway
 * .cmd shim, run the registry's real spawn path against it, and compare the argv
 * the child actually saw against the prompt we asked for. A round-trip mismatch
 * is a failure no matter how the escaping looks.
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const registry = require("../lib/agent-registry");

const IS_WIN = process.platform === "win32";

/**
 * Create a temp dir with a .cmd shim that forwards to a .js entry point.
 *
 * This drives the *fallback* spawn path: the shim line carries extra argv
 * (`node "<script>" %*`), so resolveShimTarget() correctly refuses to peel it
 * and the registry falls back to quoteForCmd().
 *
 * The peeled no-shell path needs a real .exe that echoes its own argv, which
 * cannot be fabricated portably, so it is exercised by [peeledArgv] against the
 * CLI actually installed on this machine instead.
 *
 * Returns { dir, cmd } or null when not on Windows.
 */
function makeShim() {
  if (!IS_WIN) return null;
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "hermes-quote-"));

  fs.writeFileSync(
    path.join(dir, "echo-argv.js"),
    "process.stdout.write(JSON.stringify(process.argv.slice(2)));\n",
    "utf8",
  );

  fs.writeFileSync(
    path.join(dir, "shim.cmd"),
    '@echo off\r\nnode "%~dp0echo-argv.js" %*\r\n',
    "ascii",
  );

  return { dir, cmd: path.join(dir, "shim.cmd") };
}

/**
 * The first installed CLI whose shim peels to a real .exe.
 *
 * Used to drive the no-shell path with genuine software rather than a
 * synthetic fixture, so the test cannot pass on a contrived setup.
 *
 * @returns {{shim:string, exe:string, id:string}|null}
 */
function findPeelableCli() {
  if (!IS_WIN) return null;
  for (const a of registry.all()) {
    if (a.passthrough) continue;
    const bin = registry.resolveBin(a.bin);
    if (!bin) continue;
    const exe = registry.resolveShimTarget(bin);
    if (exe) return { shim: bin, exe, id: a.id };
  }
  return null;
}

/**
 * Run a prompt through the registry's real spawn machinery via a .cmd shim,
 * and return the argv the child actually received.
 *
 * `run()` is the production code path, so this exercises whichever branch the
 * registry takes rather than re-implementing it.
 *
 * @param {string} prompt
 * @param {string} command  path to the .cmd shim
 */
async function argvSeenBy(prompt, command) {
  const adapter = registry.get("hermes");
  const savedPassthrough = adapter.passthrough;
  adapter.passthrough = false;
  const savedBuild = adapter.build;
  try {
    adapter.build = () => ({
      command,
      args: [String(prompt ?? "")],
      stdin: null, // forces the argv path, the one that was broken
    });
    const res = await registry.run("hermes", prompt, { timeoutMs: 20000 });
    return JSON.parse(res.text);
  } finally {
    adapter.build = savedBuild;
  }
}

/**
 * Ask a real installed CLI to echo a prompt, using the registry's real spawn
 * path, and return its answer.
 *
 * A real installed CLI is used as the oracle here, because it echoes short
 * literal instructions faithfully, which makes it a good oracle for "what
 * string did the child actually receive".
 *
 * @param {number} [timeoutMs] the CLI can take minutes under load; the default
 *   is generous because a timeout here is indistinguishable from a real failure.
 */
async function peeledEcho(prompt, timeoutMs = 300000) {
  const target = findPeelableCli();
  if (!target) return null;
  const adapter = registry.get("hermes");
  const savedPassthrough = adapter.passthrough;
  adapter.passthrough = false;
  const savedBuild = adapter.build;
  try {
    adapter.build = () => ({
      command: target.shim,
      args: ["run", String(prompt)],
      stdin: null,
    });
    const res = await registry.run("hermes", prompt, { timeoutMs });
    return res.text;
  } finally {
    adapter.build = savedBuild;
  }
}

/**
 * Whether the live-CLI tests should run.
 *
 * They are opt-in (CHAIN_E2E=1) even though they are valuable, for two reasons:
 * each one spawns a real model and takes tens of seconds, and the CLI is an
 * LLM that occasionally ignores "reply with exactly …". A test that fails
 * because a model chose different wording trains people to ignore failures, so
 * the load-bearing assertions live in the deterministic unit tests above and
 * these are an extra end-to-end confirmation to run on demand.
 */
const LIVE = process.env.CHAIN_E2E === "1";

// ---------------------------------------------------------------------------
// The peeled path: a real .exe behind the shim, spawned with no shell.
//
// Driven against the CLI actually installed on this machine, because the whole
// point of peeling is that cmd.exe never sees the prompt. A synthetic fixture
// could not prove that as convincingly.
// ---------------------------------------------------------------------------

test("a peeled CLI echoes a percent sign and a bang intact", { skip: !IS_WIN || !LIVE }, async () => {
  const target = findPeelableCli();
  if (!target) return; // no peelable CLI installed; the unit tests still run
  // This exact prompt used to come back as "100%% ^50^^! done".
  const answer = await peeledEcho("Reply with exactly this text and nothing else: 100% ^50! done");
  assert.ok(answer, "expected an answer");
  assert.ok(!/%%/.test(answer), `percent was doubled: ${JSON.stringify(answer)}`);
  assert.ok(!/\^\^/.test(answer), `caret was doubled: ${JSON.stringify(answer)}`);
  assert.ok(/100%/.test(answer), `percent lost: ${JSON.stringify(answer)}`);
  assert.ok(/50!/.test(answer), `bang lost: ${JSON.stringify(answer)}`);
});

test("a peeled CLI echoes shell metacharacters without executing them", { skip: !IS_WIN || !LIVE }, async () => {
  const target = findPeelableCli();
  if (!target) return;
  const answer = await peeledEcho(
    'Reply with exactly this text and nothing else: A & B | C > D < E "quoted"',
  );
  assert.ok(answer, "expected an answer");
  assert.ok(!/pwned|not recognized|cannot find/i.test(answer), `shell acted: ${answer}`);
  assert.ok(/A\s*&\s*B/.test(answer) && /C\s*>\s*D/.test(answer), `text mangled: ${answer}`);
});

test("a peeled CLI is not handed an expanded environment variable", { skip: !IS_WIN || !LIVE }, async () => {
  const target = findPeelableCli();
  if (!target) return;
  process.env.HERMES_QUOTE_PROBE = "LEAKED_PROBE_VALUE";
  try {
    const answer = await peeledEcho(
      "Reply with exactly this text and nothing else: value=%HERMES_QUOTE_PROBE%",
    );
    assert.ok(answer, "expected an answer");
    assert.ok(
      !answer.includes("LEAKED_PROBE_VALUE"),
      `env var expanded into the prompt: ${answer}`,
    );
  } finally {
    delete process.env.HERMES_QUOTE_PROBE;
  }
});

// ---------------------------------------------------------------------------
// The fallback path: a shim that cannot be peeled, so quoteForCmd() runs.
// Nothing here may corrupt the prompt or let the shell act on it.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// The fallback path: a shim that cannot be peeled, so quoteForCmd() runs.
// Nothing here may corrupt the prompt or let the shell act on it.
// ---------------------------------------------------------------------------

test("fallback: a lone percent is not doubled", { skip: !IS_WIN }, async () => {
  const shim = makeShim();
  try {
    const argv = await argvSeenBy("100% ^50! done", shim.cmd);
    assert.deepEqual(argv, ["100% ^50! done"]);
  } finally {
    fs.rmSync(shim.dir, { recursive: true, force: true });
  }
});

test("fallback: an exclamation mark is not doubled", { skip: !IS_WIN }, async () => {
  const shim = makeShim();
  try {
    const argv = await argvSeenBy("Hello! Wow!", shim.cmd);
    assert.deepEqual(argv, ["Hello! Wow!"]);
  } finally {
    fs.rmSync(shim.dir, { recursive: true, force: true });
  }
});

test("fallback: an env var does not expand and keeps its percent signs", { skip: !IS_WIN }, async () => {
  const shim = makeShim();
  try {
    process.env.HERMES_QUOTE_PROBE = "LEAKED";
    const argv = await argvSeenBy("value=%HERMES_QUOTE_PROBE%", shim.cmd);
    const seen = JSON.stringify(argv);
    // The secret must never reach the child.
    assert.ok(!seen.includes("LEAKED"), `env var expanded: ${seen}`);
    // Both percent signs must survive; only the caret used to break the lookup
    // may appear. Assert on the percent count rather than exact equality,
    // because cmd.exe has no in-quote escape that both blocks %NAME% expansion
    // and disappears (documented on quoteForCmd).
    assert.equal((seen.match(/%/g) || []).length, 2, `percent lost: ${seen}`);
    assert.ok(/HERMES_QUOTE_PROBE/.test(seen), `name lost: ${seen}`);
  } finally {
    delete process.env.HERMES_QUOTE_PROBE;
    fs.rmSync(shim.dir, { recursive: true, force: true });
  }
});

test("fallback: metacharacters cannot break out", { skip: !IS_WIN }, async () => {
  const shim = makeShim();
  try {
    const hostile = 'x" & echo PWNED & rem "';
    const argv = await argvSeenBy(hostile, shim.cmd);
    assert.deepEqual(argv, [hostile]);
  } finally {
    fs.rmSync(shim.dir, { recursive: true, force: true });
  }
});

test("fallback: pipes, redirects and quotes stay in one argument", { skip: !IS_WIN }, async () => {
  const shim = makeShim();
  try {
    for (const p of ["A & B | C > D < E", 'he said "hello" ok', "semi;colon;chain", "a ^ b"]) {
      const argv = await argvSeenBy(p, shim.cmd);
      assert.deepEqual(argv, [p], `did not round-trip: ${JSON.stringify(p)}`);
    }
  } finally {
    fs.rmSync(shim.dir, { recursive: true, force: true });
  }
});

// ---------------------------------------------------------------------------
// quoteForCmd invariants, now stated correctly.
// ---------------------------------------------------------------------------

test("quoteForCmd wraps a token in double quotes", () => {
  assert.equal(registry.quoteForCmd("hello"), '"hello"');
  assert.equal(registry.quoteForCmd("a b"), '"a b"');
});

test("quoteForCmd doubles embedded quotes so they stay balanced", () => {
  for (const s of ['a"b', 'x" & echo y & rem "', "a|b>c", "%PATH%"]) {
    const q = registry.quoteForCmd(s);
    assert.equal((q.match(/"/g) || []).length % 2, 0, `unbalanced quotes for ${s}`);
  }
});

test("quoteForCmd leaves a lone percent alone but escapes a %VAR% reference", () => {
  // A bare % is not special to cmd.exe; doubling it corrupts the prompt.
  assert.equal(registry.quoteForCmd("50% done"), '"50% done"');
  assert.equal(registry.quoteForCmd("100%"), '"100%"');
  // A real variable reference IS expanded even inside quotes, so the lookup
  // must be broken - but not with the batch-only %% doubling, which does not
  // collapse on a `cmd /c` path.
  const escaped = registry.quoteForCmd("%USERNAME%");
  assert.ok(!/%%/.test(escaped), `used the batch-only %% doubling: ${escaped}`);
  assert.ok(/USERNAME/.test(escaped), "name must survive");
  assert.equal((escaped.match(/%/g) || []).length, 2, "both percents must survive");
});

test("quoteForCmd does not double carets or exclamations", () => {
  assert.equal(registry.quoteForCmd("a ^ b"), '"a ^ b"');
  assert.equal(registry.quoteForCmd("ok!"), '"ok!"');
  assert.equal(registry.quoteForCmd("50! done"), '"50! done"');
});

test("quoteForCmd always returns a balanced, non-empty quoted token", () => {
  const inputs = ["", " ", "%", "!", "^", '"', "%%", "!!", "%A%", "A^B!C"];
  for (const s of inputs) {
    const q = registry.quoteForCmd(s);
    assert.ok(q.startsWith('"') && q.endsWith('"'), `not quoted: ${JSON.stringify(s)}`);
    assert.equal((q.match(/"/g) || []).length % 2, 0, `unbalanced for ${JSON.stringify(s)}`);
  }
});

// ---------------------------------------------------------------------------
// resolveShimTarget: the peeling itself.
// ---------------------------------------------------------------------------

test("resolveShimTarget peels a real installed npm shim to its .exe", { skip: !IS_WIN }, () => {
  // Uses whatever the machine actually has, rather than a fixture, so this
  // fails if npm's shim template ever diverges from what the parser expects.
  const target = findPeelableCli();
  if (!target) return; // nothing peelable installed here
  assert.ok(target.exe, `expected a .exe behind ${target.shim}`);
  assert.ok(/\.exe$/i.test(target.exe), `expected a .exe, got ${target.exe}`);
  assert.ok(fs.existsSync(target.exe), `peeled target must exist: ${target.exe}`);
});

test("resolveShimTarget refuses a shim that forwards to a .js via node", { skip: !IS_WIN }, () => {
  const shim = makeShim();
  try {
    // The shim line is `node "…echo-argv.js" %*`: the real executable is `node`
    // and the .js is an argument, which peeling cannot express, so it must
    // decline rather than guess and drop the script.
    assert.equal(registry.resolveShimTarget(shim.cmd), null);
  } finally {
    fs.rmSync(shim.dir, { recursive: true, force: true });
  }
});

test("resolveShimTarget returns null for non-shim input", { skip: !IS_WIN }, () => {
  assert.equal(registry.resolveShimTarget("C:\\Windows\\System32\\cmd.exe"), null);
  assert.equal(registry.resolveShimTarget(require("node:os").tmpdir()), null);
});

test("every installed CLI is either peelable or a real executable", { skip: !IS_WIN }, () => {
  for (const a of registry.all()) {
    if (a.passthrough) continue;
    const bin = registry.resolveBin(a.bin);
    if (!bin) continue; // not installed on this machine
    const isExe = /\.exe$/i.test(bin);
    const peeled = registry.resolveShimTarget(bin);
    // Each CLI must end up on a path spawn can run without a shell, otherwise
    // the prompt goes through cmd.exe and this whole file is academic.
    assert.ok(
      isExe || peeled,
      `${a.id}: ${bin} is a shim with no peelable .exe - would need the shell`,
    );
  }
});

