"use strict";

const { test } = require("node:test");
const assert = require("node:assert");
const http = require("http");

const config = require("../lib/config");
const { createServer, listen } = require("../lib/server");
const { parseArgs, lanIP } = require("../bin/cli");

test("parseEnv reads keys, ignores comments, strips quotes", () => {
  const env = config.parseEnv(
    ["# comment", "API_SERVER_KEY=abc123", 'API_SERVER_PORT="8642"', "", "API_SERVER_MODEL_NAME=Claude-Fable.3"].join("\n")
  );
  assert.equal(env.API_SERVER_KEY, "abc123");
  assert.equal(env.API_SERVER_PORT, "8642");
  assert.equal(env.API_SERVER_MODEL_NAME, "Claude-Fable.3");
});

test("parseArgs: tunnel on by default, --no-tunnel / --lan disable it", () => {
  assert.equal(parseArgs([]).tunnel, true);
  assert.equal(parseArgs(["--no-tunnel"]).tunnel, false);
  assert.equal(parseArgs(["--lan"]).tunnel, false);
  assert.equal(parseArgs(["--port", "8765"]).port, 8765);
  assert.equal(parseArgs(["--no-open"]).open, false);
});

test("parseArgs: --profile picks a profile, defaults to empty (top-level key)", () => {
  assert.equal(parseArgs([]).profile, "");
  assert.equal(parseArgs(["--profile", "dalek"]).profile, "dalek");
  assert.equal(parseArgs(["--profile"]).profile, "");
});

test("lanIP returns a non-empty IPv4-ish string", () => {
  const ip = lanIP();
  assert.ok(typeof ip === "string" && ip.length >= 7);
});

test("webview server: /api/connect returns the connect payload", async () => {
  const app = createServer({
    publicUrl: "https://abc.trycloudflare.com",
    localUrl: "http://192.168.1.113:8642",
    apiKey: "secret-key-value",
    model: "Claude-Fable.3",
    profiles: ["dalek"],
  });
  const { port, server } = await listen(app, 0);
  try {
    const json = await getJson(`http://127.0.0.1:${port}/api/connect`);
    assert.equal(json.v, 1);
    assert.equal(json.baseUrl, "https://abc.trycloudflare.com");
    assert.equal(json.fallbackUrl, "http://192.168.1.113:8642");
    assert.equal(json.apiKey, "secret-key-value");
    assert.equal(json.profile, "");
    assert.equal(json.model, "Claude-Fable.3");
    assert.deepEqual(json.profiles, ["dalek"]);
  } finally {
    server.close();
  }
});

test("webview server: /api/connect passes through the selected profile", async () => {
  const app = createServer({
    publicUrl: "https://abc.trycloudflare.com",
    localUrl: "http://192.168.1.113:8642",
    apiKey: "secret-key-value",
    model: "Claude-Fable.3",
    profiles: ["dalek", "doraemon"],
    profile: "doraemon",
  });
  const { port, server } = await listen(app, 0);
  try {
    const json = await getJson(`http://127.0.0.1:${port}/api/connect`);
    assert.equal(json.profile, "doraemon");
    assert.deepEqual(json.profiles, ["dalek", "doraemon"]);
  } finally {
    server.close();
  }
});

test("webview server: /qr.png returns a PNG, / returns the QR page", async () => {
  const app = createServer({
    publicUrl: "https://x.trycloudflare.com",
    localUrl: "http://10.0.0.5:8642",
    apiKey: "k".repeat(20),
    model: "m",
    profiles: [],
  });
  const { port, server } = await listen(app, 0);
  try {
    const png = await getBuffer(`http://127.0.0.1:${port}/qr.png`);
    // PNG magic number
    assert.deepEqual([...png.subarray(0, 4)], [0x89, 0x50, 0x4e, 0x47]);
    const html = (await getBuffer(`http://127.0.0.1:${port}/`)).toString("utf8");
    assert.ok(html.includes("Hermes Remote"));
    assert.ok(html.includes("https://x.trycloudflare.com"));
    assert.ok(!html.includes("k".repeat(20)), "raw api key must not be printed on the page");
  } finally {
    server.close();
  }
});

function getJson(url) {
  return new Promise((resolve, reject) => {
    http
      .get(url, (res) => {
        let b = "";
        res.on("data", (c) => (b += c));
        res.on("end", () => resolve(JSON.parse(b)));
      })
      .on("error", reject);
  });
}

function getBuffer(url) {
  return new Promise((resolve, reject) => {
    http
      .get(url, (res) => {
        const chunks = [];
        res.on("data", (c) => chunks.push(c));
        res.on("end", () => resolve(Buffer.concat(chunks)));
      })
      .on("error", reject);
  });
}

/** POST a JSON body and return { status, headers, text }. */
function postJson(url, body, headers = {}) {
  return new Promise((resolve, reject) => {
    const payload = JSON.stringify(body);
    const u = new URL(url);
    const req = http.request(
      {
        hostname: u.hostname,
        port: u.port,
        path: u.pathname + u.search,
        method: "POST",
        headers: { "Content-Type": "application/json", "Content-Length": Buffer.byteLength(payload), ...headers },
      },
      (res) => {
        let b = "";
        res.on("data", (c) => (b += c));
        res.on("end", () => resolve({ status: res.statusCode, headers: res.headers, text: b }));
      }
    );
    req.on("error", reject);
    req.write(payload);
    req.end();
  });
}

/** A fake fetch that answers chat-completions with a canned reply. */
function fakeGateway(reply, capture) {
  return async (url, init) => {
    if (capture) {
      capture.url = url;
      capture.body = JSON.parse(init.body);
      capture.auth = init.headers.Authorization;
    }
    return {
      ok: true,
      status: 200,
      json: async () => ({ choices: [{ message: { role: "assistant", content: reply } }] }),
      text: async () => "",
    };
  };
}

test("parseArgs: --webhook-token enables the webhook, absent by default", () => {
  assert.equal(parseArgs([]).webhookToken, "");
  assert.equal(parseArgs(["--webhook-token", "s3cr3t"]).webhookToken, "s3cr3t");
});

test("webhook: disabled (404) when no token configured", async () => {
  const app = createServer({
    publicUrl: "http://x",
    localUrl: "http://localhost:1",
    apiKey: "k",
    model: "m",
    profiles: [],
  });
  const { port, server } = await listen(app, 0);
  try {
    const r = await postJson(`http://127.0.0.1:${port}/api/webhook/ask`, { text: "hi" });
    assert.equal(r.status, 404);
  } finally {
    server.close();
  }
});

test("webhook: rejects a bad token with 401", async () => {
  const app = createServer({
    publicUrl: "http://x",
    localUrl: "http://localhost:1",
    apiKey: "k",
    model: "m",
    profiles: [],
    webhookToken: "good",
    fetchImpl: fakeGateway("nope"),
  });
  const { port, server } = await listen(app, 0);
  try {
    const r = await postJson(`http://127.0.0.1:${port}/api/webhook/ask`, { token: "bad", text: "hi" });
    assert.equal(r.status, 401);
  } finally {
    server.close();
  }
});

test("webhook: forwards the prompt to the gateway and returns the reply", async () => {
  const capture = {};
  const app = createServer({
    publicUrl: "http://x",
    localUrl: "http://localhost:8642",
    apiKey: "secret-key",
    model: "Claude-Fable.3",
    profiles: [],
    webhookToken: "good",
    fetchImpl: fakeGateway("Xin chào, đây là Hermes.", capture),
  });
  const { port, server } = await listen(app, 0);
  try {
    const r = await postJson(
      `http://127.0.0.1:${port}/api/webhook/ask`,
      { token: "good", text: "trạng thái git?" }
    );
    assert.equal(r.status, 200);
    const json = JSON.parse(r.text);
    assert.equal(json.ok, true);
    assert.equal(json.reply, "Xin chào, đây là Hermes.");
    assert.equal(capture.url, "http://localhost:8642/v1/chat/completions");
    assert.equal(capture.auth, "Bearer secret-key");
    assert.equal(capture.body.messages[0].content, "trạng thái git?");
    assert.equal(capture.body.stream, false);
  } finally {
    server.close();
  }
});

test("webhook: ?say=1 returns plain text; token may come from a header", async () => {
  const app = createServer({
    publicUrl: "http://x",
    localUrl: "http://localhost:8642",
    apiKey: "k",
    model: "m",
    profiles: [],
    webhookToken: "good",
    fetchImpl: fakeGateway("câu trả lời"),
  });
  const { port, server } = await listen(app, 0);
  try {
    const r = await postJson(
      `http://127.0.0.1:${port}/api/webhook/ask?say=1`,
      { text: "hỏi" },
      { "x-webhook-token": "good" }
    );
    assert.equal(r.status, 200);
    assert.equal(r.text, "câu trả lời");
  } finally {
    server.close();
  }
});

test("webhook: uses /p/<profile> when a profile is set", async () => {
  const capture = {};
  const app = createServer({
    publicUrl: "http://x",
    localUrl: "http://localhost:8642",
    apiKey: "k",
    model: "m",
    profiles: ["dalek"],
    profile: "dalek",
    webhookToken: "good",
    fetchImpl: fakeGateway("ok", capture),
  });
  const { port, server } = await listen(app, 0);
  try {
    const r = await postJson(`http://127.0.0.1:${port}/api/webhook/ask`, { token: "good", text: "x" });
    assert.equal(r.status, 200);
    assert.equal(capture.url, "http://localhost:8642/p/dalek/v1/chat/completions");
  } finally {
    server.close();
  }
});

test("webhook: gateway failure surfaces as 502", async () => {
  const failing = async () => ({ ok: false, status: 500, text: async () => "boom", json: async () => ({}) });
  const app = createServer({
    publicUrl: "http://x",
    localUrl: "http://localhost:8642",
    apiKey: "k",
    model: "m",
    profiles: [],
    webhookToken: "good",
    fetchImpl: failing,
  });
  const { port, server } = await listen(app, 0);
  try {
    const r = await postJson(`http://127.0.0.1:${port}/api/webhook/ask`, { token: "good", text: "x" });
    assert.equal(r.status, 502);
    assert.equal(JSON.parse(r.text).ok, false);
  } finally {
    server.close();
  }
});

test("webhook: /api/webhook/ping reports enabled/disabled", async () => {
  const appOn = createServer({
    publicUrl: "http://x", localUrl: "http://localhost:1", apiKey: "k",
    model: "m", profiles: [], webhookToken: "t",
  });
  const { port, server } = await listen(appOn, 0);
  try {
    const json = await getJson(`http://127.0.0.1:${port}/api/webhook/ping`);
    assert.equal(json.ok, true);
    assert.equal(json.webhook, "enabled");
  } finally {
    server.close();
  }
});
