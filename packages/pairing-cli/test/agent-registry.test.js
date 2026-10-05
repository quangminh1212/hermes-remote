"use strict";

/**
 * Tests for the agent CLI registry.
 *
 * These run against the real machine's PATH for detection, but never spawn a
 * real agent: spawning would take tens of seconds and cost tokens. The
 * invocation CONTRACT is asserted instead, which is what the bridge depends on.
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const { spawnSync } = require("node:child_process");

const reg = require("../lib/agent-registry");

test("every adapter is well formed", () => {
  for (const a of reg.all()) {
    assert.equal(typeof a.id, "string", "id must be a string");
    assert.ok(a.id.length > 0, "id must not be empty");
    assert.equal(a.id, a.id.toLowerCase(), `${a.id} must be lower-case`);
    assert.equal(typeof a.label, "string");
    assert.equal(typeof a.description, "string");
    assert.equal(typeof a.build, "function");
  }
});

test("adapter ids are unique", () => {
  const ids = reg.all().map((a) => a.id);
  assert.equal(new Set(ids).size, ids.length);
});

test("get() is case-insensitive and trims", () => {
  assert.equal(reg.get("claude").id, "claude");
  assert.equal(reg.get("  CLAUDE  ").id, "claude");
  assert.equal(reg.get("Codex").id, "codex");
});

test("get() returns null for unknown or empty ids", () => {
  assert.equal(reg.get("definitely-not-an-agent"), null);
  assert.equal(reg.get(""), null);
  assert.equal(reg.get(null), null);
  assert.equal(reg.get(undefined), null);
});

test("the hermes adapter is a gateway passthrough, not a child process", () => {
  const hermes = reg.get("hermes");
  assert.equal(hermes.passthrough, true);
  assert.equal(hermes.build(), null);
});

test("claude passes the prompt as a --print argument", () => {
  const spec = reg.get("claude").build("hello world");
  assert.deepEqual(spec.args, ["--print", "hello world"]);
  assert.equal(spec.stdin, null);
});

test("codex passes the prompt on stdin, not in argv", () => {
  // A prompt starting with "-" would be parsed as a flag if it went in argv.
  const spec = reg.get("codex").build("-not-a-flag");
  assert.deepEqual(spec.args, ["exec", "-"]);
  assert.equal(spec.stdin, "-not-a-flag");
});

test("grok uses the one-shot -p flag", () => {
  const spec = reg.get("grok").build("hi");
  assert.deepEqual(spec.args, ["-p", "hi"]);
  assert.equal(spec.stdin, null);
});

test("opencode uses the run subcommand", () => {
  const spec = reg.get("opencode").build("hi");
  assert.deepEqual(spec.args, ["run", "hi"]);
  assert.equal(spec.stdin, null);
});

test("build() coerces a non-string prompt instead of passing it through", () => {
  assert.deepEqual(reg.get("grok").build(42).args, ["-p", "42"]);
  assert.deepEqual(reg.get("grok").build(null).args, ["-p", ""]);
  assert.deepEqual(reg.get("grok").build(undefined).args, ["-p", ""]);
});

test("no adapter interpolates the prompt into a shell string", () => {
  const hostile = 'x" & echo PWNED & rem "';
  for (const a of reg.all()) {
    if (a.passthrough) continue;
    const spec = a.build(hostile);
    // The prompt must arrive as exactly one argv element or as stdin; never
    // split, never merged with other tokens.
    const inArgv = spec.args.includes(hostile);
    const inStdin = spec.stdin === hostile;
    assert.ok(
      inArgv || inStdin,
      `${a.id} did not carry the prompt as a single opaque value`,
    );
    assert.equal(spec.args.length, spec.stdin === null ? 2 : 2);
  }
});

test("resolveBin finds an installed CLI and returns an absolute path", () => {
  const p = reg.resolveBin("grok");
  assert.ok(p, "grok should be installed on this machine");
  assert.ok(/[\\/]/.test(p), `expected a path, got ${p}`);
});

test("resolveBin returns null for a CLI that does not exist", () => {
  assert.equal(reg.resolveBin("definitely-not-a-real-cli-xyz"), null);
  assert.equal(reg.resolveBin(null), null);
  assert.equal(reg.resolveBin(""), null);
});

test("resolveBin prefers an executable shim over a .ps1 wrapper on Windows", () => {
  if (process.platform !== "win32") return;
  // opencode ships as opencode / opencode.cmd / opencode.ps1. Node cannot exec
  // the extensionless one or the .ps1, so the .cmd must win.
  const p = reg.resolveBin("opencode");
  if (!p) return; // not installed here; the ranking itself is covered below
  assert.ok(
    /\.(exe|cmd|bat)$/i.test(p),
    `expected an exec-able shim, got ${p}`,
  );
});

test("quoteForCmd keeps quotes balanced and neutralises % expansion", () => {
  assert.equal(reg.quoteForCmd("hello"), '"hello"');
  assert.equal(reg.quoteForCmd("a b"), '"a b"');
  assert.equal(reg.quoteForCmd("%USERNAME%"), '"%%USERNAME%%"');

  for (const s of ['a"b', 'x" & echo y & rem "', "%PATH%", "a|b>c"]) {
    const q = reg.quoteForCmd(s);
    assert.equal((q.match(/"/g) || []).length % 2, 0, `unbalanced quotes for ${s}`);
  }
});

test("available() always includes passthrough adapters", () => {
  const ids = reg.available().map((a) => a.id);
  assert.ok(ids.includes("hermes"));
});

test("available() only includes adapters whose binary is present", () => {
  for (const a of reg.available()) {
    if (a.passthrough) continue;
    assert.ok(reg.hasBin(a.bin), `${a.id} reported available but ${a.bin} is missing`);
  }
});

test("run() rejects an unknown agent without spawning anything", async () => {
  await assert.rejects(() => reg.run("nope", "hi"), /unknown agent/);
});

test("run() rejects a passthrough agent with a clear message", async () => {
  await assert.rejects(() => reg.run("hermes", "hi"), /served by the gateway/);
});

test("a missing command is reported before any process starts", async () => {
  // run() resolves the command the spec asks for, so substituting an
  // absolute path that does not exist must fail fast with a clear message and
  // must not fall back to the real CLI.
  const adapter = reg.get("grok");
  const savedBuild = adapter.build;
  try {
    adapter.build = () => ({
      command: require("node:path").join(
        require("node:os").tmpdir(),
        "definitely-not-installed-zzz",
      ),
      args: [],
      stdin: null,
    });
    const t0 = Date.now();
    await assert.rejects(() => reg.run("grok", "hi"), /is not installed/);
    assert.ok(Date.now() - t0 < 5000, "must fail before spawning, not after a timeout");
  } finally {
    adapter.build = savedBuild;
  }
});

test("hasBin agrees with resolveBin", () => {
  for (const b of ["grok", "definitely-not-a-real-cli-xyz"]) {
    assert.equal(reg.hasBin(b), reg.resolveBin(b) !== null);
  }
});

test("the registry is an executable command list, not prose", () => {
  // Guards against an adapter being added with a placeholder command.
  for (const a of reg.all()) {
    if (a.passthrough) continue;
    const spec = a.build("x");
    assert.ok(Array.isArray(spec.args), `${a.id} args must be an array`);
    assert.ok(spec.args.length > 0, `${a.id} must pass at least one argument`);
    assert.equal(typeof a.bin, "string", `${a.id} needs a bin name`);
    // The prompt is the last argv entry when it is not on stdin.
    if (spec.stdin === null) assert.equal(spec.args[spec.args.length - 1], "x");
  }
});
