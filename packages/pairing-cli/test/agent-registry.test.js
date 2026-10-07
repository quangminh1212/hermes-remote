"use strict";

/**
 * Tests for the agent CLI registry.
 *
 * The registry now holds exactly one adapter — the Hermes gateway passthrough —
 * because the bridge stopped fanning out to local coding CLIs. So this file has
 * two jobs:
 *
 *  1. Pin what the single adapter must be.
 *  2. Guard the machinery that would still be needed to add a spawning adapter
 *     back: `resolveBin`, `quoteForCmd`, `stripAnsi`, and `run`'s failure
 *     ordering. That code is still load-bearing for `run`, and deleting its
 *     tests because no shipped adapter currently exercises it would leave the
 *     next person to add one with no safety net.
 *
 * Detection is tested against this machine's real PATH, but nothing here spawns
 * an agent: that takes tens of seconds and costs tokens.
 */

const test = require("node:test");
const assert = require("node:assert/strict");

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

test("the registry ships exactly one adapter: hermes", () => {
  // Deliberate, not incidental. The bridge used to expose claude, codex, grok
  // and opencode as well; if one of them reappears, that is a product decision
  // and it should have to change this test on purpose.
  assert.deepEqual(reg.all().map((a) => a.id), ["hermes"]);
});

test("the shipped adapters are exactly the ones available on this machine", () => {
  // Every shipped adapter is a passthrough, so no CLI presence can add or
  // remove one. This would fail if a `bin`-based adapter were added back
  // without also being installed here.
  assert.deepEqual(reg.available().map((a) => a.id), ["hermes"]);
});

test("get() is case-insensitive and trims", () => {
  assert.equal(reg.get("hermes").id, "hermes");
  assert.equal(reg.get("  HERMES  ").id, "hermes");
  assert.equal(reg.get("Hermes").id, "hermes");
});

test("get() returns null for unknown or empty ids", () => {
  assert.equal(reg.get("definitely-not-an-agent"), null);
  assert.equal(reg.get(""), null);
  assert.equal(reg.get(null), null);
  assert.equal(reg.get(undefined), null);
});

test("the CLI adapters that were removed are now unknown", () => {
  // Regression guard for the removal itself: these ids must not silently
  // resolve, because the phone would then show a picker entry that cannot run.
  for (const gone of ["claude", "codex", "grok", "opencode"]) {
    assert.equal(reg.get(gone), null, `${gone} should no longer be registered`);
  }
});

test("the hermes adapter is a gateway passthrough, not a child process", () => {
  const hermes = reg.get("hermes");
  assert.equal(hermes.passthrough, true);
  assert.equal(hermes.bin, null);
  assert.equal(hermes.build(), null);
});

test("run() rejects an unknown agent without spawning anything", async () => {
  await assert.rejects(() => reg.run("nope", "hi"), /unknown agent/);
});

test("run() rejects a removed CLI id as unknown, not as a crash", async () => {
  await assert.rejects(() => reg.run("claude", "hi"), /unknown agent/);
});

test("run() rejects a passthrough agent with a clear message", async () => {
  await assert.rejects(() => reg.run("hermes", "hi"), /served by the gateway/);
});

test("resolveBin finds an installed CLI and returns an absolute path", () => {
  // `node` is the one executable guaranteed present in any environment that
  // can run this suite.
  const p = reg.resolveBin(process.platform === "win32" ? "node" : "node");
  assert.ok(p, "node should be resolvable on this machine");
  assert.ok(/[\\/]/.test(p), `expected a path, got ${p}`);
});

test("resolveBin returns null for a CLI that does not exist", () => {
  assert.equal(reg.resolveBin("definitely-not-a-real-cli-xyz"), null);
  assert.equal(reg.resolveBin(null), null);
  assert.equal(reg.resolveBin(""), null);
});

test("hasBin agrees with resolveBin", () => {
  for (const b of ["node", "definitely-not-a-real-cli-xyz"]) {
    assert.equal(reg.hasBin(b), reg.resolveBin(b) !== null);
  }
});

test("quoteForCmd keeps quotes balanced and neutralises % expansion", () => {
  assert.equal(reg.quoteForCmd("hello"), '"hello"');
  assert.equal(reg.quoteForCmd("a b"), '"a b"');
  // A bare % is literal to cmd.exe; only a %NAME% lookup needs breaking.
  assert.equal(reg.quoteForCmd("50% done"), '"50% done"');
  assert.ok(!/%%/.test(reg.quoteForCmd("%USERNAME%")));

  for (const s of ['a"b', 'x" & echo y & rem "', "%PATH%", "a|b>c"]) {
    const q = reg.quoteForCmd(s);
    assert.equal((q.match(/"/g) || []).length % 2, 0, `unbalanced quotes for ${s}`);
  }
});

test("quoteForCmd does not double an already-literal percent", () => {
  // The bug this guards: an earlier version rewrote % to %% unconditionally,
  // which corrupted every prompt containing a percentage sign.
  assert.equal(reg.quoteForCmd("final 100% check 5!"), '"final 100% check 5!"');
});

test("stripAnsi removes colour codes a CLI writes when not a TTY", () => {
  assert.equal(
    reg.stripAnsi("\u001b[91m\u001b[1mError:\u001b[0m You must provide a message"),
    "Error: You must provide a message",
  );
  assert.equal(reg.stripAnsi("\u001b[31mred\u001b[0m plain"), "red plain");
  assert.equal(reg.stripAnsi("no codes here"), "no codes here");
  assert.ok(!/\u001b/.test(reg.stripAnsi("\u001b[2J\u001b[Hcleared\u001b[0m")));
});

test("stripAnsi leaves a clean multi-line answer untouched", () => {
  const answer = "Line one\nLine two\n\n- bullet\n- bullet";
  assert.equal(reg.stripAnsi(answer), answer);
});

test("a missing command is reported before any process starts", async () => {
  // run() resolves the command the spec asks for. This exercises that path even
  // though no shipped adapter spawns: it is the failure mode a future spawning
  // adapter would hit first, and it must fail fast rather than after a timeout.
  const adapter = reg.get("hermes");
  const savedBuild = adapter.build;
  const savedPassthrough = adapter.passthrough;
  try {
    adapter.passthrough = false;
    adapter.build = () => ({
      command: require("node:path").join(
        require("node:os").tmpdir(),
        "definitely-not-installed-zzz",
      ),
      args: [],
      stdin: null,
    });
    const t0 = Date.now();
    await assert.rejects(() => reg.run("hermes", "hi"), /is not installed/);
    assert.ok(Date.now() - t0 < 5000, "must fail before spawning, not after a timeout");
  } finally {
    adapter.build = savedBuild;
    adapter.passthrough = savedPassthrough;
  }
});
