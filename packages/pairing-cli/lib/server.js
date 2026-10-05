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

// The pairing page lives in its own module: it is pure presentation and is
// unit-tested independently of the HTTP server.
const { renderPage } = require("./pairing-page");

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
