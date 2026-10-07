"use strict";

/**
 * Agent CLI registry.
 *
 * Hermes Remote talks to the PC through one HTTP endpoint, so the phone never
 * needs to know which CLI answers. This module is the other half of that deal:
 * it knows how to drive each installed agent CLI in its non-interactive mode.
 *
 * Contract every adapter must honour:
 *
 *   - `probe()` returns whether the CLI is installed and runnable.
 *   - `build(prompt)` returns `{ command, args, stdin }`. The prompt travels
 *     either as an argument or on stdin, never interpolated into a shell
 *     string, so no quoting or injection question ever arises.
 *   - the CLI's ANSWER arrives on stdout; banners, progress and warnings go to
 *     stderr and are ignored.
 *
 * Verified invocation per CLI (probed on the machine this was built on):
 *
 *   claude --print "<prompt>"        answer on stdout,  ~19s
 *   codex exec -  (prompt on stdin)  answer on stdout,  ~17s
 *   grok -p "<prompt>"               answer on stdout,   ~9s
 *   opencode run "<prompt>"          answer on stdout
 *   dsh ...                          see below
 */

const { spawn, spawnSync } = require("child_process");

/** Is `bin` on PATH? */
function hasBin(bin) {
  return resolveBin(bin) !== null;
}

/**
 * Resolve a CLI name to something `spawn` can actually execute.
 *
 * On Windows a globally-installed npm CLI is a set of shims (`opencode`,
 * `opencode.cmd`, `opencode.ps1`). Node's `spawn` without `shell: true` cannot
 * execute the extensionless POSIX shim or the PowerShell one; it needs the
 * `.cmd`. Relying on PATH alone fails with ENOENT, which is exactly what
 * happened on the first probe run, so resolve explicitly and prefer `.cmd`.
 *
 * A `.cmd` is a poor target, though: it forces `shell: true`, and then cmd.exe
 * gets to reinterpret the prompt. So the shim is peeled one layer further by
 * [resolveShimTarget] before it is used.
 *
 * @returns {string|null} executable path, or null when not installed
 */
function resolveBin(bin) {
  if (!bin) return null;

  if (process.platform !== "win32") {
    const r = spawnSync("which", [bin], { encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] });
    return r.status === 0 ? r.stdout.trim().split(/\r?\n/)[0] : null;
  }

  const r = spawnSync("where", [bin], { encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] });
  if (r.status !== 0) return null;

  const hits = r.stdout
    .split(/\r?\n/)
    .map((s) => s.trim())
    .filter(Boolean);
  if (hits.length === 0) return null;

  // Prefer an executable form over the extensionless npm shim / .ps1 wrapper.
  const rank = (p) => {
    const l = p.toLowerCase();
    if (l.endsWith(".exe")) return 0;
    if (l.endsWith(".cmd")) return 1;
    if (l.endsWith(".bat")) return 2;
    if (l.endsWith(".ps1")) return 4;
    return 3; // extensionless POSIX-style shim
  };
  hits.sort((a, b) => rank(a) - rank(b));
  return hits[0];
}

/**
 * Peel a Windows `.cmd`/`.bat` shim down to the real program it forwards to.
 *
 * npm generates these shims as a fixed template whose last line runs a quoted
 * path with `%*` appended:
 *
 *     "%dp0%\node_modules\@opencode\cli\bin\opencode.exe"   %*
 *
 * Running that `.exe` directly means `spawn` needs no shell, which removes
 * cmd.exe from the picture entirely and with it every quoting question. That
 * matters because cmd.exe has no in-quote escape for `%`: a prompt like
 * "100% done" was being corrupted to "100%% done" (and "50!" to "50^^!") by the
 * old unconditional escaping, and the batch-only `%%` doubling does not collapse
 * when the command is run through `cmd /c` instead of from a batch file.
 *
 * Only the npm template is recognised. Anything else — a hand-written shim, a
 * path Node still cannot exec, a target that does not exist — returns null and
 * the caller falls back to the shell path with [quoteForCmd].
 *
 * @returns {string|null} absolute path to a real executable, or null
 */
function resolveShimTarget(shimPath) {
  if (process.platform !== "win32") return null;
  if (!/\.(cmd|bat)$/i.test(shimPath)) return null;

  let text;
  try {
    text = require("fs").readFileSync(shimPath, "utf8");
  } catch (_) {
    return null;
  }

  // The target is the executable on a line ending in %*, which is how the npm
  // template passes the caller's arguments through. Anything between the quoted
  // path and %* is fixed extra argv (a .js entry point, say), which we cannot
  // reproduce without knowing it is one, so such a line is ignored below.
  const matches = [...text.matchAll(/^\s*"([^"]+)"\s*(.*?)\s*%\*\s*$/gm)];
  if (matches.length === 0) return null;

  // Last match wins: the template opens with a GOTO/EXIT preamble and the
  // actual invocation is the final such line.
  const match = matches[matches.length - 1];
  const raw = match[1];
  const extraArgs = match[2];

  // Only a bare passthrough is safe to peel. If the template injects its own
  // arguments (e.g. `node ".../cli.js" %*`) then the real executable is `node`
  // and the .js is an argument, which this function cannot express — the caller
  // gets null and falls back to the quoted shell path.
  if (extraArgs) return null;

  // %dp0% / %~dp0% is the shim's own directory (npm sets it via CALL :find_dp0).
  const dir = require("path").dirname(shimPath);
  const expanded = raw
    .replace(/%~dp0%?/gi, dir)
    .replace(/%dp0%/gi, dir);

  const resolved = require("path").isAbsolute(expanded)
    ? expanded
    : require("path").resolve(dir, expanded);

  if (!/\.exe$/i.test(resolved)) return null;

  try {
    return require("fs").statSync(resolved).isFile() ? resolved : null;
  } catch (_) {
    return null;
  }
}

/**
 * Per-agent adapters.
 *
 * `id` is the name the phone sends as the model. Keep these short and stable:
 * they are user-visible in the app's agent picker and go into the QR payload.
 *
 * This list holds exactly one entry on purpose. The bridge used to fan out to
 * whichever coding CLIs were installed (claude, codex, grok, opencode), which
 * meant four spawn paths, four flag conventions and four failure modes to keep
 * working — on a phone, where the user cannot see the child process or its
 * stderr. Hermes Remote is a remote control for the Hermes gateway, so the
 * gateway is the only backend; anything else was surface area without a user.
 *
 * Adding an entry back is cheap if that changes: the adapter contract below is
 * still the same, and `run()` still spawns whatever `build()` describes.
 */
const ADAPTERS = [
  {
    id: "hermes",
    label: "Hermes (direct)",
    bin: null,
    description: "The Hermes gateway itself, via HTTP",
    // Not a child process: the gateway is already an HTTP API. Marked so the
    // bridge can route this id back to the gateway instead of spawning.
    passthrough: true,
    build() {
      return null;
    },
  },
];

/** All adapters, whether or not they are installed. */
function all() {
  return ADAPTERS.slice();
}

/** Adapters whose CLI is actually present on this machine. */
function available() {
  return ADAPTERS.filter((a) => a.passthrough || (a.bin && hasBin(a.bin)));
}

/** Look up one adapter by id. Returns null when unknown. */
function get(id) {
  const want = String(id || "").trim().toLowerCase();
  if (!want) return null;
  return ADAPTERS.find((a) => a.id === want) || null;
}

/**
 * Run one agent and resolve with its answer.
 *
 * @param {string} id       adapter id ("claude", "codex", …)
 * @param {string} prompt   the user's text
 * @param {{timeoutMs?:number, cwd?:string}} [opts]
 * @returns {Promise<{text:string, agent:string, durationMs:number}>}
 */
function run(id, prompt, opts = {}) {
  const adapter = get(id);
  if (!adapter) return Promise.reject(new Error(`unknown agent "${id}"`));
  if (adapter.passthrough) {
    return Promise.reject(new Error(`agent "${id}" is served by the gateway, not the bridge`));
  }

  const timeoutMs = opts.timeoutMs || 10 * 60 * 1000;
  const spec = adapter.build(String(prompt ?? ""));
  const started = Date.now();

  // The spec owns the command it wants run; `adapter.bin` is only the name used
  // for detection. Resolving the spec's command (not the adapter's bin) keeps
  // the two concerns separate, lets a spec use an absolute path, and means a
  // test can substitute an executable without the registry second-guessing it.
  //
  // Resolving rather than using the bare name matters on Windows: the bare name
  // can be an extensionless npm shim Node cannot exec (see resolveBin).
  const specCommand = spec.command || adapter.bin;
  // An absolute/relative path is used as given, but it still has to exist:
  // otherwise the failure surfaces later as an opaque ENOENT from spawn.
  const resolvedCommand = /[\\/]/.test(specCommand)
    ? specCommand
    : resolveBin(specCommand);
  if (!resolvedCommand || !commandExists(resolvedCommand)) {
    return Promise.reject(
      new Error(`${adapter.id} is not installed (${specCommand} not found)`),
    );
  }

  // Prefer the real executable behind the shim: spawning it needs no shell, so
  // cmd.exe never sees the prompt and no escaping is required. Falls back to
  // the shim itself when it is not a recognisable npm wrapper.
  const directCommand = resolveShimTarget(resolvedCommand) || resolvedCommand;

  return new Promise((resolve, reject) => {
    // A Windows .cmd/.bat shim is not a real executable: Node needs a shell to
    // run it, otherwise spawn fails with EINVAL.
    //
    // This is the fallback path only — [resolveShimTarget] above normally
    // removes the shell from the equation. When it cannot, `shell: true` with
    // an args array is unsafe, because Node concatenates the arguments without
    // escaping (DEP0190), so the whole command goes as one string with each
    // part individually quoted via [quoteForCmd].
    const needsShell = process.platform === "win32" && /\.(cmd|bat)$/i.test(directCommand);
    const useStdin = spec.stdin !== null;

    let child;
    try {
      // Ask CLIs not to colourise at all. opencode honours NO_COLOR (the
      // informal standard), and FORCE_COLOR=0 overrides the TTY heuristics some
      // tools use. This is belt-and-braces: [stripAnsi] still cleans whatever
      // slips through on stderr.
      const cleanEnv = { ...process.env, NO_COLOR: "1", FORCE_COLOR: "0" };

      if (needsShell && !useStdin) {
        const line = [directCommand, ...spec.args].map(quoteForCmd).join(" ");
        child = spawn(line, {
          cwd: opts.cwd || process.cwd(),
          env: cleanEnv,
          windowsHide: true,
          shell: true,
          stdio: ["ignore", "pipe", "pipe"],
        });
      } else if (needsShell) {
        // Prompt goes on stdin, so only the fixed argv is concatenated.
        const line = [directCommand, ...spec.args].join(" ");
        child = spawn(line, {
          cwd: opts.cwd || process.cwd(),
          env: cleanEnv,
          windowsHide: true,
          shell: true,
          stdio: ["pipe", "pipe", "pipe"],
        });
      } else {
        child = spawn(directCommand, spec.args, {
          cwd: opts.cwd || process.cwd(),
          env: cleanEnv,
          windowsHide: true,
          stdio: [useStdin ? "pipe" : "ignore", "pipe", "pipe"],
        });
      }
    } catch (err) {
      return reject(new Error(`could not start ${adapter.id}: ${err.message}`));
    }

    let stdout = "";
    let stderr = "";
    let settled = false;

    const done = (fn, arg) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      fn(arg);
    };

    const timer = setTimeout(() => {
      try {
        child.kill();
      } catch (_) {
        /* already gone */
      }
      done(reject, new Error(`${adapter.id} timed out after ${Math.round(timeoutMs / 1000)}s`));
    }, timeoutMs);

    child.stdout.on("data", (c) => (stdout += c));
    child.stderr.on("data", (c) => (stderr += c));

    child.on("error", (err) => {
      done(reject, new Error(`${adapter.id} could not run (${err.message}); is ${adapter.bin} on PATH?`));
    });

    child.on("close", (code) => {
      // Some CLIs colour their output even when it is not a TTY — opencode
      // writes "\e[91m\e[1mError:\e[0m …" to stderr. Those bytes would travel
      // to the phone inside the error text and render as literal junk, so strip
      // them here rather than letting each client deal with it.
      const text = stripAnsi(stdout).trim();
      if (code !== 0 && !text) {
        const detail = (stripAnsi(stderr).trim() || `exit code ${code}`).slice(0, 400);
        return done(reject, new Error(`${adapter.id} failed: ${detail}`));
      }
      if (!text) {
        return done(reject, new Error(`${adapter.id} produced no output`));
      }
      done(resolve, { text, agent: adapter.id, durationMs: Date.now() - started });
    });

    if (spec.stdin !== null) {
      child.stdin.on("error", () => {
        /* the CLI may close stdin early; not fatal */
      });
      child.stdin.end(spec.stdin);
    }
  });
}

/**
 * Remove ANSI/VT100 escape sequences from CLI output.
 *
 * Covers SGR colour codes, Claude-Fable movement, and the OSC sequences some tools
 * use for window titles. The phone renders answers as plain text, so these
 * bytes are never wanted and always look like corruption when they get through.
 */
function stripAnsi(s) {
  if (typeof s !== "string") return s;
  return s
    // CSI: ESC [ … final-byte
    .replace(/\u001b\[[0-9;?]*[ -/]*[@-~]/g, "")
    // OSC: ESC ] … BEL or ESC \
    .replace(/\u001b\][^\u0007\u001b]*(?:\u0007|\u001b\\)/g, "")
    // Any remaining lone escapes and vertical-tab/backspace noise.
    .replace(/\u001b[@-Z\\-_]/g, "")
    .replace(/[\u0008\u000b\u000c]/g, "");
}

/**
 * Does an explicit path point at something runnable?
 *
 * Checked with the filesystem rather than by attempting to execute, so a
 * missing command fails immediately and predictably instead of surfacing later
 * as an ENOENT from whichever spawn path was taken.
 */
function commandExists(p) {
  try {
    return require("fs").statSync(p).isFile();
  } catch (_) {
    return false;
  }
}

/**
 * Quote one token for cmd.exe.
 *
 * Fallback path only: [resolveShimTarget] normally removes the shell entirely.
 * This is used when a `.cmd` could not be peeled, so the prompt has to survive
 * cmd.exe's parsing.
 *
 * The strategy is to wrap the token in double quotes and double any embedded
 * quote, which handles whitespace, `&`, `|`, `<`, `>`, `;` and `^`. Inside
 * double quotes cmd.exe treats those as literal, and `^`/`!` are NOT escape
 * characters there — cmd.exe only consumes `^` outside quotes and only expands
 * `!` when delayed expansion is enabled, which it is not for a plain
 * `cmd /c` spawn.
 *
 * `%` is the one real hazard: `%NAME%` IS expanded even inside quotes, so a
 * prompt containing `%PATH%` would leak the environment into the child. There
 * is no in-quote escape that both blocks the expansion and disappears — `^%`
 * and `%NAME^%` both stop the expansion but leave the caret in the delivered
 * text, and `%%` only collapses when parsed from a *batch file*, not when the
 * command is run through `cmd /c`. So the expansion is broken by inserting a
 * caret inside the name, which prevents lookup; the stray caret is the lesser
 * evil and only appears for prompts that literally contain `%NAME%`.
 *
 * A lone `%` (e.g. "50% done") is not special to cmd.exe and is passed through
 * untouched. Doubling it — as this function used to — was the bug: it corrupted
 * every prompt containing a percent sign.
 */
function quoteForCmd(token) {
  const s = String(token);
  // Break `%NAME%` lookups without touching a bare `%`. The caret sits between
  // the name characters, so cmd.exe finds no variable to expand.
  const noExpand = s.replace(/%([A-Za-z_][A-Za-z0-9_]*)%/g, "%$1^%");
  return `"${noExpand.replace(/"/g, '""')}"`;
}

module.exports = {
  all,
  available,
  get,
  run,
  hasBin,
  resolveBin,
  resolveShimTarget,
  quoteForCmd,
  stripAnsi,
  commandExists,
  ADAPTERS,
};
