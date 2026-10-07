"use strict";

/**
 * Agent bridge — a Claude-Fable-compatible HTTP endpoint that answers by
 * running the selected agent CLI on this PC.
 *
 * Why this exists
 * ---------------
 * The phone talks to one HTTP endpoint. Hermes' gateway exposes one model name,
 * so the app has no way to say "answer this with something else". This bridge
 * sits beside the gateway and speaks the same API the app already knows:
 *
 *     POST /v1/chat/completions   { model: "hermes", messages: [...] }
 *     GET  /v1/models             -> [{ id: "hermes", ... }]
 *
 * The `model` field selects the agent, and the Claude-Fable-shaped surface is kept
 * even though one agent remains: it is what the app already speaks, and it is
 * what lets another agent be added without touching the phone.
 *
 * Streaming
 * ---------
 * The app streams replies (SSE). A CLI can take tens of seconds and only prints
 * its answer at the end, so there is nothing to stream incrementally. This
 * bridge therefore emits a valid SSE response with one content delta followed
 * by the terminator — the app renders it exactly like a streamed answer.
 */

const http = require("http");
const registry = require("./agent-registry");
const { languageDirective } = require("./i18n");

/**
 * Flatten incoming chat messages into one prompt.
 *
 * The CLI sees a single string, so the conversation is rendered as plain
 * transcript lines. Only user and assistant turns are included; system prompts
 * are folded in because the CLI has no separate system channel.
 */
function messagesToPrompt(messages) {
  if (!Array.isArray(messages) || messages.length === 0) return "";

  const parts = [];
  for (const m of messages) {
    const role = m && m.role ? String(m.role) : "user";
    let content = m ? m.content : "";
    // Content may be an array of parts ({type:"text",text:"..."}).
    if (Array.isArray(content)) {
      content = content
        .map((p) => (typeof p === "string" ? p : p && p.text ? p.text : ""))
        .join("");
    }
    content = String(content ?? "").trim();
    if (!content) continue;

    if (role === "assistant") parts.push(`Assistant: ${content}`);
    else if (role === "system") parts.push(`Instructions: ${content}`);
    else parts.push(content);
  }
  // The final user turn is what an agent CLI expects; earlier turns give it
  // context. A blank prompt is rejected rather than sent.
  return parts.join("\n\n").trim();
}

/**
 * The most recent user message, as plain text.
 *
 * Used to pick the language to answer in. Returns "" when there is none, which
 * makes the language directive fall back to "match the user's language".
 *
 * @param {Array<{role?:string, content?:any}>} messages
 * @returns {string}
 */
function lastUserMessage(messages) {
  if (!Array.isArray(messages)) return "";
  for (let i = messages.length - 1; i >= 0; i--) {
    const m = messages[i];
    if (!m || String(m.role || "user") !== "user") continue;
    let content = m.content;
    if (Array.isArray(content)) {
      content = content
        .map((p) => (typeof p === "string" ? p : p && p.text ? p.text : ""))
        .join("");
    }
    const text = String(content ?? "").trim();
    if (text) return text;
  }
  return "";
}

/** The agent ids this bridge can answer with. */
function modelList() {
  return registry.available().map((a) => ({
    id: a.id,
    object: "model",
    owned_by: "hermes-remote",
    // Extra fields are harmless to strict clients and used by the app's picker.
    label: a.label,
    description: a.description,
  }));
}

function readBody(req, limitBytes = 4 * 1024 * 1024) {
  return new Promise((resolve, reject) => {
    let total = 0;
    const chunks = [];
    req.on("data", (c) => {
      total += c.length;
      if (total > limitBytes) {
        reject(new Error("request body too large"));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    req.on("error", reject);
  });
}

function sendJson(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(body),
  });
  res.end(body);
}

/** Emit one content delta then the SSE terminator. */
function sendSse(res, model, text) {
  res.writeHead(200, {
    "Content-Type": "text/event-stream; charset=utf-8",
    "Cache-Control": "no-cache",
    Connection: "keep-alive",
  });

  const base = {
    id: "chatcmpl-" + Date.now().toString(36),
    object: "chat.completion.chunk",
    created: Math.floor(Date.now() / 1000),
    model,
  };

  res.write(
    `data: ${JSON.stringify({
      ...base,
      choices: [{ index: 0, delta: { role: "assistant", content: text }, finish_reason: null }],
    })}\n\n`,
  );
  res.write(
    `data: ${JSON.stringify({
      ...base,
      choices: [{ index: 0, delta: {}, finish_reason: "stop" }],
    })}\n\n`,
  );
  res.write("data: [DONE]\n\n");
  res.end();
}

/** Non-streaming response shape. */
function completionBody(model, text, durationMs) {
  return {
    id: "chatcmpl-" + Date.now().toString(36),
    object: "chat.completion",
    created: Math.floor(Date.now() / 1000),
    model,
    choices: [
      {
        index: 0,
        message: { role: "assistant", content: text },
        finish_reason: "stop",
      },
    ],
    usage: { prompt_tokens: 0, completion_tokens: 0, total_tokens: 0 },
    // Informational; ignored by Claude-Fable-compatible clients.
    "x-hermes-agent": model,
    "x-hermes-duration-ms": durationMs,
  };
}

/**
 * Build the HTTP handler.
 *
 * @param {{apiKey?:string, defaultAgent?:string, timeoutMs?:number, log?:Function}} opts
 */
function createBridge(opts = {}) {
  const log = opts.log || (() => {});
  const timeoutMs = opts.timeoutMs || 10 * 60 * 1000;
  // "hermes" is the only adapter, so it is the only safe default. This used to
  // say "claude"; leaving that would make a request with no `model` resolve to
  // an agent that no longer exists, and the user would see a 404 naming a CLI
  // the app never offered.
  const defaultAgent = opts.defaultAgent || "hermes";

  return async function handle(req, res) {
    const url = new URL(req.url, "http://localhost");
    const path = url.pathname.replace(/\/+$/, "") || "/";

    // Health is deliberately unauthenticated: it carries no secret and is used
    // as a readiness probe before any key is in hand.
    if (path === "/health" && req.method === "GET") {
      return sendJson(res, 200, {
        status: "ok",
        platform: "hermes-agent-bridge",
        agents: registry.available().map((a) => a.id),
      });
    }

    // Auth mirrors the gateway: Bearer token. The bridge is bound to localhost
    // and reached through the same tunnel, so it uses the same key.
    if (opts.apiKey) {
      const auth = req.headers["authorization"] || "";
      const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
      if (token !== opts.apiKey) {
        return sendJson(res, 401, { error: { message: "invalid api key", type: "auth_error" } });
      }
    }

    try {
      if (path === "/v1/models" && req.method === "GET") {
        return sendJson(res, 200, { object: "list", data: modelList() });
      }

      if (path === "/v1/chat/completions" && req.method === "POST") {
        const raw = await readBody(req);
        let payload;
        try {
          payload = JSON.parse(raw || "{}");
        } catch (_) {
          return sendJson(res, 400, { error: { message: "invalid JSON body" } });
        }

        const rawPrompt = messagesToPrompt(payload.messages);
        // Tell the agent which language to answer in: it cannot infer this
        // from the prompt alone, and a Chinese question answered in English
        // is a bug from the user's point of view.
        const question = lastUserMessage(payload.messages);
        const prompt = rawPrompt
          ? languageDirective(question) + "\n\n" + rawPrompt
          : "";
        if (!prompt) {
          return sendJson(res, 400, { error: { message: "no message content" } });
        }

        const wanted = String(payload.model || defaultAgent).trim().toLowerCase();
        const agent = registry.get(wanted);

        if (!agent) {
          return sendJson(res, 404, {
            error: {
              message: `unknown agent "${wanted}"`,
              type: "invalid_request_error",
              available: registry.available().map((a) => a.id),
            },
          });
        }
        if (agent.passthrough) {
          return sendJson(res, 400, {
            error: {
              message:
                `agent "${agent.id}" is served by the Hermes gateway, not this bridge; ` +
                `point the app at the gateway port for it`,
              type: "invalid_request_error",
            },
          });
        }
        // The registry's run() reports a missing binary itself, with a clearer
        // message; checking here too would duplicate the logic.
        log(`agent=${agent.id} chars=${prompt.length} stream=${!!payload.stream}`);
        const result = await registry.run(agent.id, prompt, { timeoutMs });

        if (payload.stream) return sendSse(res, agent.id, result.text);
        return sendJson(res, 200, completionBody(agent.id, result.text, result.durationMs));
      }

      return sendJson(res, 404, { error: { message: `no route ${req.method} ${path}` } });
    } catch (err) {
      const message = err && err.message ? err.message : String(err);
      log(`error: ${message}`);
      // "not installed" is the client's problem to fix (pick another agent or
      // install the CLI), so it is a 503 rather than an opaque 500.
      const status = /is not installed/.test(message) ? 503 : 500;
      return sendJson(res, status, {
        error: { message, type: status === 503 ? "unavailable" : "agent_error" },
      });
    }
  };
}

/**
 * Start the bridge.
 *
 * @returns {Promise<{url:string, port:number, server:import("http").Server,
 *                    close:() => Promise<void>}>}
 */
function listenBridge(opts, port = 0, host = "127.0.0.1") {
  const server = http.createServer(createBridge(opts));
  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(port, host, () => {
      const p = server.address().port;
      resolve({
        url: `http://127.0.0.1:${p}`,
        port: p,
        server,
        // Promisified so the CLI can await a clean shutdown.
        close: () => new Promise((done) => server.close(() => done())),
      });
    });
  });
}

module.exports = {
  createBridge,
  listenBridge,
  messagesToPrompt,
  lastUserMessage,
  modelList,
  completionBody,
  sendSse,
};
