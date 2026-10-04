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
