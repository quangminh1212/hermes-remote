"use strict";

/**
 * End-to-end proof that the bridge makes the chosen CLI answer in the
 * language of the question.
 *
 * Starts the real bridge against the real installed CLIs, asks the same thing
 * in three languages, and prints each reply. No stubbing: this is the check
 * that the whole chain (HTTP -> bridge -> adapter -> CLI) holds.
 */

const bridge = require("C:/Dev/Hermes_Android/packages/pairing-cli/lib/agent-bridge");
const registry = require("C:/Dev/Hermes_Android/packages/pairing-cli/lib/agent-registry");
const { detectScript } = require("C:/Dev/Hermes_Android/packages/pairing-cli/lib/i18n");

const KEY = "test-key-e2e";

const QUESTIONS = [
  { lang: "en", text: "In one short sentence, what is a hexadecimal number?" },
  { lang: "vi", text: "Trong một câu ngắn, số thập lục phân là gì?" },
  { lang: "zh", text: "用一句话简单说明，什么是十六进制数？" },
];

async function main() {
  const agent = process.argv[2] || "claude";
  console.log("Dung agent: " + agent);
  console.log("Agent co san: " + registry.available().map((a) => a.id).join(", "));
  console.log("");

  const handle = await bridge.listenBridge(
    { apiKey: KEY, defaultAgent: agent, timeoutMs: 120000 },
    0,
    "127.0.0.1",
  );
  const base = handle.url;
  console.log("Bridge: " + base);
  console.log("");

  const health = await (await fetch(base + "/health")).json();
  console.log("Health agents: " + health.agents.join(", "));

  const modelsRes = await fetch(base + "/v1/models", { headers: { Authorization: "Bearer " + KEY } });
  const models = await modelsRes.json();
  console.log("Models: " + ((models.data || []).map((m) => m.id).join(", ") || JSON.stringify(models).slice(0, 160)));
  console.log("");

  let worst = "OK";
  for (const q of QUESTIONS) {
    const expected = detectScript(q.text);
    const started = Date.now();
    const res = await fetch(base + "/v1/chat/completions", {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: "Bearer " + KEY },
      body: JSON.stringify({
        model: agent,
        messages: [{ role: "user", content: q.text }],
        stream: false,
      }),
    });
    const body = await res.json();
    const secs = ((Date.now() - started) / 1000).toFixed(1);

    if (!res.ok) {
      console.log("[" + q.lang + "] HTTP " + res.status + " -> " + JSON.stringify(body).slice(0, 200));
      worst = "CO LOI";
      continue;
    }

    const reply = body.choices[0].message.content;
    const got = detectScript(reply);
    // English is "no script evidence", so a null detection is correct there.
    const match = expected === null ? got === null : got === expected;
    if (!match) worst = "LECH NGON NGU";

    console.log("[" + q.lang + "] " + secs + "s  hoi: " + q.text);
    console.log("        dap: " + reply.replace(/\s+/g, " ").slice(0, 220));
    console.log("        ngon ngu tra loi: " + (got || "latin/english") +
      " | mong doi: " + (expected || "latin/english") +
      " | " + (match ? "KHOP" : "*** LECH ***"));
    console.log("");
  }

  await handle.close();
  console.log("KET QUA: " + worst);
}

main().catch((e) => {
  console.error("LOI: " + (e && e.stack ? e.stack : e));
  process.exit(1);
});
