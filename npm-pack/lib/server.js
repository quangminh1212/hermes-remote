"use strict";

/**
 * The local "webview" server: serves a QR page (and a machine-readable
 * /api/connect payload) that the Android app scans to auto-configure itself.
 *
 * It also exposes a small webhook (/api/webhook/ask) so voice assistants that
 * can only call a URL — IFTTT applets triggered by Google Assistant, or a
 * Google Home routine — can ask Hermes something and get the answer back.
 */

const express = require("express");
const QRCode = require("qrcode");
const path = require("path");

/**
 * @param {object} opts
 * @param {string} opts.publicUrl  Public (tunnel) URL to the Hermes API, no trailing slash.
 * @param {string} opts.localUrl   LAN fallback URL.
 * @param {string} opts.apiKey     Hermes API key.
 * @param {string} opts.model      Default model id.
 * @param {string[]} opts.profiles Available Hermes profiles.
 * @param {number} [opts.port]     Port for this QR webview server (0 = random).
 * @param {string} [opts.gatewayUrl] Base URL of the local Hermes gateway to ask
 *                                   (defaults to the LAN url).
 * @param {string} [opts.webhookToken] Shared secret required by the webhook.
 *                                     Empty disables the webhook entirely.
 * @param {Function} [opts.fetchImpl] fetch implementation (injectable for tests).
 */
function createServer(opts) {
  const app = express();
  app.use(express.json());

  // The exact payload the phone will store. baseUrl = public tunnel; the app
  // also keeps the LAN url as a fallback it can switch to.
  const connectPayload = {
    v: 1,
    baseUrl: opts.publicUrl,
    fallbackUrl: opts.localUrl,
    apiKey: opts.apiKey,
    profile: opts.profile || "",
    model: opts.model || "",
    profiles: opts.profiles || [],
  };

  // Machine-readable JSON. Nice for debugging or an app that can't scan.
  app.get("/api/connect", (_req, res) => res.json(connectPayload));

  // The QR image (PNG) of the connect payload.
  app.get("/qr.png", async (_req, res) => {
    try {
      const buf = await QRCode.toBuffer(JSON.stringify(connectPayload), {
        errorCorrectionLevel: "M",
        margin: 2,
        width: 420,
      });
      res.type("png").send(buf);
    } catch (e) {
      res.status(500).send(String(e));
    }
  });

  // --- Webhook (Phase C): let IFTTT / Google Home ask Hermes ---------------

  const gatewayUrl = (opts.gatewayUrl || opts.localUrl || "").replace(/\/$/, "");
  const fetchImpl = opts.fetchImpl || globalThis.fetch;
  const webhookToken = opts.webhookToken || "";

  // Liveness probe for IFTTT/Google Home setup.
  app.get("/api/webhook/ping", (_req, res) => {
    res.json({ ok: true, webhook: webhookToken ? "enabled" : "disabled" });
  });

  app.post("/api/webhook/ask", async (req, res) => {
    if (!webhookToken) {
      return res.status(404).json({ ok: false, error: "webhook disabled" });
    }
    const body = req.body || {};
    const token = body.token || req.get("x-webhook-token") || req.query.token;
    if (token !== webhookToken) {
      return res.status(401).json({ ok: false, error: "bad token" });
    }

    const text = String(body.text ?? body.query ?? "").trim();
    if (!text) {
      return res.status(400).json({ ok: false, error: "missing text" });
    }
    if (typeof fetchImpl !== "function") {
      return res.status(500).json({ ok: false, error: "no fetch available" });
    }

    try {
      const reply = await askGateway(fetchImpl, gatewayUrl, opts.apiKey, {
        model: opts.model,
        profile: opts.profile,
        text,
      });
      // ?say=1 returns plain text so a speaker can read it directly.
      if (req.query.say === "1") {
        return res.type("text/plain").send(reply);
      }
      return res.json({ ok: true, reply });
    } catch (e) {
      return res.status(502).json({ ok: false, error: String(e && e.message ? e.message : e) });
    }
  });

  // The human-facing page.
  app.get("/", (_req, res) => {
    res.type("html").send(renderPage(connectPayload));
  });

  return app;
}

/**
 * POST a single-turn prompt to the Hermes gateway and return the assistant text.
 * Non-streaming: IFTTT/Google Home want the whole answer at once.
 */
async function askGateway(fetchImpl, gatewayUrl, apiKey, { model, profile, text }) {
  const profileSegment = profile ? `/p/${profile}` : "";
  const url = `${gatewayUrl}${profileSegment}/v1/chat/completions`;
  const resp = await fetchImpl(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model: model || undefined,
      messages: [{ role: "user", content: text }],
      stream: false,
    }),
  });
  if (!resp.ok) {
    const detail = await safeText(resp);
    throw new Error(`gateway ${resp.status}: ${detail.slice(0, 200)}`);
  }
  const json = await resp.json();
  const content = json?.choices?.[0]?.message?.content;
  return typeof content === "string" && content.trim() ? content.trim() : "(Hermes không trả lời)";
}

async function safeText(resp) {
  try {
    return await resp.text();
  } catch (_) {
    return "";
  }
}

function esc(s) {
  return String(s).replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c])
  );
}

function renderPage(p) {
  return `<!doctype html>
<html lang="vi">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Hermes Remote — Quét QR để kết nối</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body { margin: 0; min-height: 100vh; display: grid; place-items: center;
    font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
    background: #0b0d12; color: #e6e9ef; }
  .card { width: min(560px, 92vw); background: #141821; border: 1px solid #232a38;
    border-radius: 20px; padding: 32px; text-align: center;
    box-shadow: 0 20px 60px rgba(0,0,0,.5); }
  h1 { margin: 0 0 4px; font-size: 22px; }
  .sub { color: #8b93a7; font-size: 14px; margin-bottom: 22px; }
  .qr { background: #fff; border-radius: 16px; padding: 16px; display: inline-block; }
  .qr img { display: block; width: 300px; height: 300px; }
  .steps { text-align: left; margin: 22px auto 0; max-width: 380px; font-size: 14px; line-height: 1.6; }
  .steps b { color: #7fd1a8; }
  code { background: #0b0d12; padding: 2px 6px; border-radius: 6px; font-size: 13px; }
  .row { display: flex; justify-content: space-between; gap: 12px;
    padding: 8px 0; border-bottom: 1px solid #1d2432; font-size: 13px; text-align: left; }
  .row span:first-child { color: #8b93a7; }
  .pill { display:inline-block; padding:2px 10px; border-radius:999px; font-size:12px;
    background:#16301f; color:#7fd1a8; border:1px solid #26543a; margin-bottom:16px; }
</style>
</head>
<body>
  <div class="card">
    <div class="pill">● Đang chạy</div>
    <h1>Hermes Remote</h1>
    <div class="sub">Mở app Hermes Remote trên điện thoại và quét mã bên dưới</div>
    <div class="qr"><img src="/qr.png" alt="QR kết nối" width="300" height="300"></div>
    <div class="steps">
      <div>1. <b>Mở app</b> Hermes Remote trên Android.</div>
      <div>2. Bấm <b>Quét QR</b> và hướng camera vào mã trên.</div>
      <div>3. App tự điền server, key và <b>kết nối ngay</b>.</div>
    </div>
    <div style="margin-top:24px">
      <div class="row"><span>Server (public)</span><span>${esc(p.baseUrl)}</span></div>
      <div class="row"><span>Server (LAN dự phòng)</span><span>${esc(p.fallbackUrl)}</span></div>
      <div class="row"><span>Model</span><span>${esc(p.model || "(mặc định)")}</span></div>
      <div class="row" style="border:0"><span>Profile</span><span>mặc định (.env)</span></div>
    </div>
    <div class="sub" style="margin-top:14px">Dữ liệu kết nối: <code>/api/connect</code></div>
  </div>
</body>
</html>`;
}

/** Start the webview server; resolves with { url, port, server }. */
function listen(app, port) {
  return new Promise((resolve, reject) => {
    const server = app.listen(port, "127.0.0.1", () => {
      const p = server.address().port;
      resolve({ url: "http://127.0.0.1:" + p, port: p, server });
    });
    server.on("error", reject);
  });
}

module.exports = { createServer, listen, renderPage };
