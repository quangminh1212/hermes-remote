"use strict";

/**
 * Does the app's default model work against the agent bridge?
 *
 * The app falls back to ServerConfig.DEFAULT_MODEL ("Claude-Fable.3") when the
 * payload carries no model. When the payload points at the agent BRIDGE rather
 * than the gateway, that id is not one of the agents, so the first request from
 * a freshly-paired phone may be a 404. This probe states the fact.
 */

const bridge = require("../lib/agent-bridge");
const registry = require("../lib/agent-registry");

const APP_DEFAULT_MODEL = "Claude-Fable.3";

(async () => {
  const handle = await bridge.listenBridge({ apiKey: "k" }, 0, "127.0.0.1");

  async function ask(model) {
    const res = await fetch(handle.url + "/v1/chat/completions", {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: "Bearer k" },
      body: JSON.stringify({ model, messages: [{ role: "user", content: "Reply with exactly: OK" }] }),
    });
    const body = await res.text();
    return { status: res.status, body: body.slice(0, 160) };
  }

  console.log(`\nbridge agents : ${registry.available().map((a) => a.id).join(", ")}`);
  console.log(`app default   : ${APP_DEFAULT_MODEL}\n`);

  for (const model of [APP_DEFAULT_MODEL, "claude-fable.3", "claude"]) {
    const r = await ask(model);
    const verdict = r.status === 200 ? "WORKS" : `FAILS (${r.status})`;
    console.log(`  model=${model.padEnd(16)} -> ${verdict}`);
    if (r.status !== 200) console.log(`      ${r.body}`);
  }

  await handle.close();
})();
