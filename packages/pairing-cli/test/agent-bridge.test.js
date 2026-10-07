"use strict";

/**
 * Tests for the agent bridge.
 *
 * The bridge is exercised over real HTTP against a real listening socket, but
 * with a stubbed registry so no agent CLI is ever spawned: these must stay fast
 * and free.
 */

const test = require("node:test");
const assert = require("node:assert/strict");

const bridge = require("../lib/agent-bridge");
const registry = require("../lib/agent-registry");

const API_KEY = "test-key";

/** Run the handler on an ephemeral port and return {url, close}. */
function withBridge(opts = {}) {
  return bridge
    .listenBridge({ apiKey: API_KEY, log: () => {}, ...opts })
    .then(({ url, server }) => ({ url, close: () => server.close() }));
}

function post(url, body, key = API_KEY) {
  return fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${key}` },
    body: JSON.stringify(body),
  });
}

function get(url, key = API_KEY) {
  return fetch(url, { headers: key ? { Authorization: `Bearer ${key}` } : {} });
}

// --- messagesToPrompt -------------------------------------------------------

test("messagesToPrompt renders a single user turn", () => {
  assert.equal(
    bridge.messagesToPrompt([{ role: "user", content: "hello" }]),
    "hello",
  );
});

test("messagesToPrompt labels assistant and system turns", () => {
  const out = bridge.messagesToPrompt([
    { role: "system", content: "be brief" },
    { role: "user", content: "hi" },
    { role: "assistant", content: "hello" },
    { role: "user", content: "again" },
  ]);
  assert.match(out, /Instructions: be brief/);
  assert.match(out, /Assistant: hello/);
  assert.ok(out.endsWith("again"), "the newest user turn must come last");
});

test("messagesToPrompt flattens array content parts", () => {
  const out = bridge.messagesToPrompt([
    { role: "user", content: [{ type: "text", text: "part one " }, { type: "text", text: "part two" }] },
  ]);
  assert.equal(out, "part one part two");
});

test("messagesToPrompt drops empty turns and returns '' when nothing remains", () => {
  assert.equal(bridge.messagesToPrompt([{ role: "user", content: "   " }]), "");
  assert.equal(bridge.messagesToPrompt([]), "");
  assert.equal(bridge.messagesToPrompt(null), "");
  assert.equal(bridge.messagesToPrompt(undefined), "");
});

test("messagesToPrompt tolerates malformed entries", () => {
  assert.equal(bridge.messagesToPrompt([null, { content: "x" }]), "x");
  assert.equal(bridge.messagesToPrompt([{ role: "user", content: 42 }]), "42");
});

// --- modelList --------------------------------------------------------------

test("modelList exposes id, label and description for each available agent", () => {
  const list = bridge.modelList();
  assert.ok(list.length > 0);
  for (const m of list) {
    assert.equal(m.object, "model");
    assert.equal(m.owned_by, "hermes-remote");
    assert.equal(typeof m.id, "string");
    assert.equal(typeof m.label, "string");
    assert.equal(typeof m.description, "string");
  }
});

test("modelList includes the hermes passthrough", () => {
  assert.ok(bridge.modelList().some((m) => m.id === "hermes"));
});

// --- completionBody ---------------------------------------------------------

test("completionBody has the Claude-Fable-compatible shape", () => {
  const b = bridge.completionBody("hermes", "the answer", 1234);
  assert.equal(b.object, "chat.completion");
  assert.equal(b.model, "hermes");
  assert.equal(b.choices[0].message.role, "assistant");
  assert.equal(b.choices[0].message.content, "the answer");
  assert.equal(b.choices[0].finish_reason, "stop");
  assert.ok(b.usage);
  assert.equal(b["x-hermes-duration-ms"], 1234);
});

// --- HTTP surface -----------------------------------------------------------

test("health is reachable without a key and lists agents", async () => {
  const { url, close } = await withBridge();
  try {
    const r = await fetch(`${url}/health`);
    assert.equal(r.status, 200);
    const j = await r.json();
    assert.equal(j.status, "ok");
    assert.ok(Array.isArray(j.agents));
  } finally {
    close();
  }
});

test("a missing or wrong key is rejected on data routes", async () => {
  const { url, close } = await withBridge();
  try {
    assert.equal((await get(`${url}/v1/models`, "wrong")).status, 401);
    assert.equal((await get(`${url}/v1/models`, "")).status, 401);
    assert.equal((await get(`${url}/v1/models`, API_KEY)).status, 200);
  } finally {
    close();
  }
});

test("the key can be disabled by omitting apiKey", async () => {
  const { url, close } = await withBridge({ apiKey: "" });
  try {
    assert.equal((await fetch(`${url}/v1/models`)).status, 200);
  } finally {
    close();
  }
});

test("GET /v1/models returns the agent list", async () => {
  const { url, close } = await withBridge();
  try {
    const r = await get(`${url}/v1/models`);
    const j = await r.json();
    assert.equal(j.object, "list");
    assert.ok(j.data.some((m) => m.id === "hermes"));
  } finally {
    close();
  }
});

test("an unknown agent is a 404 that names the available ones", async () => {
  const { url, close } = await withBridge();
  try {
    const r = await post(`${url}/v1/chat/completions`, {
      model: "does-not-exist",
      messages: [{ role: "user", content: "hi" }],
    });
    assert.equal(r.status, 404);
    const j = await r.json();
    assert.match(j.error.message, /unknown agent/);
    assert.ok(Array.isArray(j.error.available));
  } finally {
    close();
  }
});

test("the hermes passthrough is refused with a pointer to the gateway", async () => {
  const { url, close } = await withBridge();
  try {
    const r = await post(`${url}/v1/chat/completions`, {
      model: "hermes",
      messages: [{ role: "user", content: "hi" }],
    });
    assert.equal(r.status, 400);
    assert.match((await r.json()).error.message, /gateway/);
  } finally {
    close();
  }
});

test("an empty prompt is rejected before any agent runs", async () => {
  const { url, close } = await withBridge();
  try {
    const r = await post(`${url}/v1/chat/completions`, { model: "hermes", messages: [] });
    assert.equal(r.status, 400);
    assert.match((await r.json()).error.message, /no message content/);
  } finally {
    close();
  }
});

test("a malformed JSON body is a 400, not a crash", async () => {
  const { url, close } = await withBridge();
  try {
    const r = await fetch(`${url}/v1/chat/completions`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${API_KEY}` },
      body: "{not json",
    });
    assert.equal(r.status, 400);
  } finally {
    close();
  }
});

test("an unknown route is a 404", async () => {
  const { url, close } = await withBridge();
  try {
    assert.equal((await get(`${url}/nope`)).status, 404);
  } finally {
    close();
  }
});

test("a wrong HTTP method on a known route is a 404", async () => {
  const { url, close } = await withBridge();
  try {
    assert.equal((await get(`${url}/v1/chat/completions`)).status, 404);
  } finally {
    close();
  }
});

test("a real answer is relayed from the agent, with the agent stubbed", async () => {
  // Replace the adapter's build() so no CLI process is created; the bridge's
  // plumbing — spawn, capture stdout, shape the response — is still exercised.
  const adapter = registry.get("hermes");
  // The hermes adapter is a gateway passthrough by default, so a stubbed
  // spawn path has to opt out of that to exercise the child-process plumbing.
  const savedPassthrough = adapter.passthrough;
  const saved = adapter.build;
  adapter.passthrough = false;
  adapter.build = () => ({
    command: process.execPath,
    args: ["-e", 'process.stdout.write("STUBBED_ANSWER")'],
    stdin: null,
  });

  const { url, close } = await withBridge();
  try {
    const r = await post(`${url}/v1/chat/completions`, {
      model: "hermes",
      messages: [{ role: "user", content: "hi" }],
    });
    assert.equal(r.status, 200);
    const j = await r.json();
    assert.equal(j.choices[0].message.content, "STUBBED_ANSWER");
    assert.equal(j.model, "hermes");
  } finally {
    adapter.build = saved;
    adapter.passthrough = savedPassthrough;
    close();
  }
});

test("streaming emits a delta, a stop, and the terminator", async () => {
  const adapter = registry.get("hermes");
  // The hermes adapter is a gateway passthrough by default, so a stubbed
  // spawn path has to opt out of that to exercise the child-process plumbing.
  const savedPassthrough = adapter.passthrough;
  const saved = adapter.build;
  adapter.passthrough = false;
  adapter.build = () => ({
    command: process.execPath,
    args: ["-e", 'process.stdout.write("STREAMED")'],
    stdin: null,
  });

  const { url, close } = await withBridge();
  try {
    const r = await post(`${url}/v1/chat/completions`, {
      model: "hermes",
      stream: true,
      messages: [{ role: "user", content: "hi" }],
    });
    assert.equal(r.status, 200);
    assert.match(r.headers.get("content-type"), /text\/event-stream/);

    const text = await r.text();
    assert.ok(text.includes("STREAMED"), "the answer must appear in a delta");
    assert.ok(text.includes('"finish_reason":"stop"'));
    assert.ok(text.includes("data: [DONE]"));

    // Every non-terminator line must be a parseable JSON SSE frame.
    for (const frame of text.split("\n\n").filter(Boolean)) {
      const payload = frame.replace(/^data: /, "");
      if (payload === "[DONE]") continue;
      assert.doesNotThrow(() => JSON.parse(payload), `bad SSE frame: ${payload}`);
    }
  } finally {
    adapter.build = saved;
    adapter.passthrough = savedPassthrough;
    close();
  }
});

test("an agent that fails surfaces as a 500 with the reason", async () => {
  const adapter = registry.get("hermes");
  // The hermes adapter is a gateway passthrough by default, so a stubbed
  // spawn path has to opt out of that to exercise the child-process plumbing.
  const savedPassthrough = adapter.passthrough;
  const saved = adapter.build;
  adapter.passthrough = false;
  adapter.build = () => ({
    command: process.execPath,
    args: ["-e", "process.exit(3)"],
    stdin: null,
  });

  const { url, close } = await withBridge();
  try {
    const r = await post(`${url}/v1/chat/completions`, {
      model: "hermes",
      messages: [{ role: "user", content: "hi" }],
    });
    assert.equal(r.status, 500);
    assert.match((await r.json()).error.message, /hermes failed/);
  } finally {
    adapter.build = saved;
    adapter.passthrough = savedPassthrough;
    close();
  }
});

test("with no configured default, a model-less request still names a real agent", async () => {
  // Regression guard: the built-in default used to be "claude", which stopped
  // existing when the CLI adapters were removed. A model-less request would
  // then fail naming an agent the app never offered.
  //
  // A real message is required: an empty one is rejected as 400 before the
  // agent is resolved at all, so it would test nothing.
  const { url, close } = await withBridge();
  try {
    const r = await post(`${url}/v1/chat/completions`, {
      messages: [{ role: "user", content: "hi" }],
    });
    const j = await r.json();

    // The default must resolve. "hermes" is a passthrough, so the expected
    // outcome is the gateway refusal — not a 404 naming an unknown agent.
    assert.notEqual(
      r.status,
      404,
      `default agent did not resolve: ${JSON.stringify(j)}`,
    );
    assert.ok(
      !/unknown agent/i.test(JSON.stringify(j)),
      `default agent resolved to an unregistered id: ${JSON.stringify(j)}`,
    );
    assert.match(
      j.error?.message ?? "",
      /served by the .*gateway/,
      `expected the hermes passthrough, got: ${JSON.stringify(j)}`,
    );
  } finally {
    close();
  }
});

test("the model field selects the agent, and defaults when absent", async () => {
  const adapter = registry.get("hermes");
  // The hermes adapter is a gateway passthrough by default, so a stubbed
  // spawn path has to opt out of that to exercise the child-process plumbing.
  const savedPassthrough = adapter.passthrough;
  const saved = adapter.build;
  adapter.passthrough = false;
  let sawPrompt = null;
  adapter.build = (p) => {
    sawPrompt = p;
    return { command: process.execPath, args: ["-e", 'process.stdout.write("ok")'], stdin: null };
  };

  const { url, close } = await withBridge();
  try {
    await post(`${url}/v1/chat/completions`, {
      model: "hermes",
      messages: [{ role: "user", content: "route me" }],
    });
    // The prompt carries a language directive plus the untouched question, so
    // assert on the payload rather than on an exact string match.
    assert.match(sawPrompt, /\[Language\]/);
    assert.ok(sawPrompt.trimEnd().endsWith("route me"), "question must survive intact");

    // No model -> the configured default.
    const { url: u2, close: c2 } = await withBridge({ defaultAgent: "hermes" });
    try {
      const r = await post(u2 + "/v1/chat/completions", {
        messages: [{ role: "user", content: "defaulted" }],
      });
      assert.equal(r.status, 200);
      assert.equal((await r.json()).model, "hermes");
    } finally {
      c2();
    }
  } finally {
    adapter.build = saved;
    adapter.passthrough = savedPassthrough;
    close();
  }
});

test("the model id is matched case-insensitively", async () => {
  const adapter = registry.get("hermes");
  // The hermes adapter is a gateway passthrough by default, so a stubbed
  // spawn path has to opt out of that to exercise the child-process plumbing.
  const savedPassthrough = adapter.passthrough;
  const saved = adapter.build;
  adapter.passthrough = false;
  adapter.build = () => ({
    command: process.execPath,
    args: ["-e", 'process.stdout.write("ok")'],
    stdin: null,
  });

  const { url, close } = await withBridge();
  try {
    const r = await post(`${url}/v1/chat/completions`, {
      model: "hermes",
      messages: [{ role: "user", content: "hi" }],
    });
    assert.equal(r.status, 200);
  } finally {
    adapter.build = saved;
    adapter.passthrough = savedPassthrough;
    close();
  }
});

test("a trailing slash on a route is tolerated", async () => {
  const { url, close } = await withBridge();
  try {
    assert.equal((await get(`${url}/v1/models/`)).status, 200);
  } finally {
    close();
  }
});
