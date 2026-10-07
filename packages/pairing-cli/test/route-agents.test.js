"use strict";

/**
 * Tests for the pairing CLI's chat routing decision.
 *
 * This exists because of a real bug. `routeAgents` used to be inlined in
 * `main()`, and it asked only "are any agent ids known?" — so when the registry
 * was reduced to a single passthrough adapter, the CLI started an agent bridge
 * and pointed the phone's QR code at it. A passthrough cannot answer chat; it
 * replies "point the app at the gateway", so the phone's first message failed
 * with a 400 and nothing in the console explained why.
 *
 * The rule under test: a bridge is worth starting only when at least one
 * adapter spawns a real child process.
 */

const test = require("node:test");
const assert = require("node:assert/strict");

const { routeAgents } = require("../bin/cli");
const registry = require("../lib/agent-registry");

test("a spawnable adapter means the bridge is used", () => {
  const r = routeAgents([{ id: "claude" }, { id: "codex" }]);
  assert.deepEqual(r.agents, ["claude", "codex"]);
  assert.deepEqual(r.spawnable, ["claude", "codex"]);
  assert.equal(r.useBridge, true);
  assert.equal(r.notice, "agents_bridge");
});

test("a passthrough alone means the gateway is used, not the bridge", () => {
  // The regression case: hermes is a valid agent NAME but not a spawnable one.
  const r = routeAgents([{ id: "hermes", passthrough: true }]);
  assert.deepEqual(r.agents, ["hermes"]);
  assert.deepEqual(r.spawnable, []);
  assert.equal(r.useBridge, false);
  assert.equal(r.notice, "agents_gateway_only");
});

test("a mix uses the bridge, and only the spawnable ids count for it", () => {
  const r = routeAgents([
    { id: "hermes", passthrough: true },
    { id: "claude" },
  ]);
  assert.deepEqual(r.agents, ["hermes", "claude"]);
  assert.deepEqual(r.spawnable, ["claude"]);
  assert.equal(r.useBridge, true);
  assert.equal(r.notice, "agents_bridge");
});

test("an explicit passthrough:false counts as spawnable", () => {
  const r = routeAgents([{ id: "x", passthrough: false }]);
  assert.equal(r.useBridge, true);
});

test("nothing available reports the empty case", () => {
  const r = routeAgents([]);
  assert.deepEqual(r.agents, []);
  assert.deepEqual(r.spawnable, []);
  assert.equal(r.useBridge, false);
  assert.equal(r.notice, "agents_none");
});

test("a malformed input is treated as empty rather than throwing", () => {
  // The registry is local, but the CLI should not crash on a bad shape: a
  // thrown error here would abort pairing with no usable message.
  for (const bad of [null, undefined, "nope", 0]) {
    const r = routeAgents(bad);
    assert.deepEqual(r.agents, []);
    assert.equal(r.useBridge, false);
    assert.equal(r.notice, "agents_none");
  }
});

test("notice is always one of the three lines the CLI knows how to print", () => {
  // Guards against a rename of the notice key, which would print "undefined".
  const known = new Set(["agents_bridge", "agents_gateway_only", "agents_none"]);
  const cases = [
    [{ id: "a" }],
    [{ id: "hermes", passthrough: true }],
    [],
  ];
  for (const c of cases) {
    assert.ok(known.has(routeAgents(c).notice), `unknown notice: ${routeAgents(c).notice}`);
  }
});

test("every notice key routeAgents can return exists in all locales", () => {
  const { translator } = require("../lib/i18n");
  const notices = [
    routeAgents([{ id: "a" }]).notice,
    routeAgents([{ id: "hermes", passthrough: true }]).notice,
    routeAgents([]).notice,
  ];
  for (const lang of ["en", "vi", "zh"]) {
    const t = translator(lang);
    for (const key of notices) {
      const line = t(key, "hermes");
      assert.ok(
        line && !line.includes("undefined"),
        `${lang}.${key} rendered as ${JSON.stringify(line)}`,
      );
    }
  }
});

test("the shipped registry routes to the gateway, not the bridge", () => {
  // This is the assertion that would have caught the original bug. It reads the
  // real registry, so re-adding a spawning adapter flips it and forces a
  // deliberate update.
  const r = routeAgents(registry.available());
  if (r.spawnable.length === 0) {
    assert.equal(r.useBridge, false);
    assert.equal(r.notice, "agents_gateway_only");
  } else {
    assert.equal(r.useBridge, true);
  }
});
