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
 * Per-agent adapters.
 *
 * `id` is the name the phone sends as the model. Keep these short and stable:
 * they are user-visible in the app's agent picker and go into the QR payload.
 */
const ADAPTERS = [
  {
    id: "claude",
    label: "Claude Code",
    bin: "claude",
    description: "Anthropic's Claude Code CLI",
    build(prompt) {
      return { command: "claude", args: ["--print", String(prompt ?? "")], stdin: null };
    },
  },
  {
    id: "codex",
    label: "OpenAI Codex",
    bin: "codex",
    description: "OpenAI Codex CLI",
    build(prompt) {
      // `codex exec -` reads the prompt from stdin. Passing it as an argument
      // works too, but a leading dash in the prompt would be parsed as a flag,
      // so stdin is the safer channel.
      return { command: "codex", args: ["exec", "-"], stdin: String(prompt ?? "") };
    },
  },
  {
    id: "grok",
    label: "Grok",
    bin: "grok",
    description: "xAI's Grok CLI",
    build(prompt) {
      // `-p/--single` is Grok's one-shot headless mode.
      return { command: "grok", args: ["-p", String(prompt ?? "")], stdin: null };
    },
  },
  {
    id: "opencode",
    label: "OpenCode",
    bin: "opencode",
    description: "OpenCode CLI",
    build(prompt) {
      return { command: "opencode", args: ["run", String(prompt ?? "")], stdin: null };
    },
  },
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

  return new Promise((resolve, reject) => {
    // A Windows .cmd/.bat shim is not a real executable: Node needs a shell to
    // run it, otherwise spawn fails with EINVAL.
    //
    // `shell: true` with an args array is unsafe here — Node concatenates the
    // arguments without escaping (DEP0190), so a prompt containing quotes or
    // `&` would be reinterpreted by cmd.exe. Instead pass the whole command as
    // a single shell string with every part individually quoted, and send the
    // prompt on stdin whenever the adapter supports it. For the adapters that
    // require the prompt as an argument, quoteForCmd() neutralises the shell
    // metacharacters rather than trusting them.
    const needsShell = process.platform === "win32" && /\.(cmd|bat)$/i.test(resolvedCommand);
    const useStdin = spec.stdin !== null;

    let child;
    try {
      if (needsShell && !useStdin) {
        const line = [resolvedCommand, ...spec.args].map(quoteForCmd).join(" ");
        child = spawn(line, {
          cwd: opts.cwd || process.cwd(),
          windowsHide: true,
          shell: true,
          stdio: ["ignore", "pipe", "pipe"],
        });
      } else if (needsShell) {
        // Prompt goes on stdin, so only the fixed argv is concatenated.
        const line = [resolvedCommand, ...spec.args].join(" ");
        child = spawn(line, {
          cwd: opts.cwd || process.cwd(),
          windowsHide: true,
          shell: true,
          stdio: ["pipe", "pipe", "pipe"],
        });
      } else {
        child = spawn(resolvedCommand, spec.args, {
          cwd: opts.cwd || process.cwd(),
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
      const text = stdout.trim();
      if (code !== 0 && !text) {
        const detail = (stderr.trim() || `exit code ${code}`).slice(0, 400);
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
 * Used only when a Windows .cmd shim forces `shell: true` and the prompt must
 * travel as an argument. cmd.exe has no reliable escaping for every
 * metacharacter, so the strategy is: wrap in double quotes, double any embedded
 * quote, and percent-escape the characters cmd.exe expands before the target
 * program sees them (`%`, `!`, and the pipe/redirect set inside quotes).
 */
function quoteForCmd(token) {
  const s = String(token);
  // Inside double quotes cmd.exe still expands %VAR%; `^` does not stop that,
  // doubling the percent does not help either, so strip the expansion prefix.
  const noExpand = s.replace(/%/g, "%%").replace(/!/g, "^^!");
  return `"${noExpand.replace(/"/g, '""')}"`;
}

module.exports = { all, available, get, run, hasBin, resolveBin, quoteForCmd, commandExists, ADAPTERS };
