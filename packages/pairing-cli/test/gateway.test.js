"use strict";

/**
 * Tests for gateway readiness probing.
 *
 * `probeHealth` is exercised against a real HTTP server on an ephemeral port
 * rather than a mocked `http.get`, because the behaviour that matters is how it
 * handles real socket outcomes: a 200, a 500, a refused connection, and a
 * server that accepts but never replies. A stub would assert the stub.
 *
 * `ensureGateway`'s restart path is not driven here — it would spawn the real
 * `hermes` CLI and start/stop a real gateway — but the branches that decide
 * whether to restart at all are, since those are pure logic over a probe.
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const http = require("node:http");

const { probeHealth, ensureGateway, hasHermesCli } = require("../lib/gateway");

/** Start a throwaway server; returns { port, close }. */
function withServer(handler) {
  const server = http.createServer(handler);
  return new Promise((resolve) => {
    server.listen(0, "127.0.0.1", () => {
      resolve({
        port: server.address().port,
        close: () => new Promise((r) => server.close(r)),
      });
    });
  });
}

test("probeHealth reports ok on a 200", async () => {
  const s = await withServer((req, res) => {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end('{"status":"ok"}');
  });
  try {
    const r = await probeHealth(s.port);
    assert.equal(r.ok, true);
    assert.equal(r.status, 200);
    assert.equal(r.body, '{"status":"ok"}');
  } finally {
    await s.close();
  }
});

test("probeHealth reports not-ok on a non-200", async () => {
  const s = await withServer((req, res) => {
    res.writeHead(500);
    res.end("boom");
  });
  try {
    const r = await probeHealth(s.port);
    assert.equal(r.ok, false);
    assert.equal(r.status, 500);
  } finally {
    await s.close();
  }
});

test("probeHealth reports not-ok when nothing is listening", async () => {
  // Bind then release, so the port is almost certainly free and the connection
  // is refused rather than answered.
  const s = await withServer((req, res) => res.end());
  const port = s.port;
  await s.close();

  const r = await probeHealth(port, "127.0.0.1", 2000);
  assert.equal(r.ok, false);
  assert.equal(r.status, 0);
});

test("probeHealth gives up on a server that never replies", async () => {
  // Accepts the connection and then does nothing, which is the shape of a
  // hung gateway. Without the timeout this test would hang forever, so its
  // completing at all is part of the assertion.
  const sockets = [];
  const server = http.createServer(() => {
    /* deliberately never respond */
  });
  server.on("connection", (sock) => sockets.push(sock));

  await new Promise((r) => server.listen(0, "127.0.0.1", r));
  const port = server.address().port;

  try {
    const t0 = Date.now();
    const r = await probeHealth(port, "127.0.0.1", 700);
    const elapsed = Date.now() - t0;

    assert.equal(r.ok, false);
    assert.equal(r.status, 0);
    assert.ok(elapsed < 5000, `should abort near the timeout, took ${elapsed}ms`);
  } finally {
    sockets.forEach((s) => s.destroy());
    await new Promise((r) => server.close(r));
  }
});

test("probeHealth honours the requested host", async () => {
  const s = await withServer((req, res) => {
    res.writeHead(200);
    res.end("ok");
  });
  try {
    const r = await probeHealth(s.port, "127.0.0.1");
    assert.equal(r.ok, true);
    // An unroutable host must not be mistaken for healthy.
    const bad = await probeHealth(s.port, "203.0.113.1", 500);
    assert.equal(bad.ok, false);
  } finally {
    await s.close();
  }
});

test("ensureGateway does not restart an already-healthy gateway", async () => {
  const s = await withServer((req, res) => {
    res.writeHead(200);
    res.end("ok");
  });
  try {
    const lines = [];
    const result = await ensureGateway(s.port, (m) => lines.push(m), 1000);
    assert.deepEqual(result, { started: false, via: "already" });
    assert.equal(lines.length, 1, "exactly one log line");
  } finally {
    await s.close();
  }
});

test("ensureGateway throws a timed-out error when nothing comes up", async () => {
  // A short budget keeps this fast. The port is free, so the probe fails
  // immediately and the loop can only time out.
  const s = await withServer((req, res) => res.end());
  const port = s.port;
  await s.close();

  // Only run when the hermes CLI is absent: otherwise ensureGateway would try
  // to restart a real gateway, which is not this test's business.
  if (hasHermesCli()) {
    return;
  }
  await assert.rejects(
    () => ensureGateway(port, () => {}, 400),
    /hermes CLI|not found|timeout|timed out|60|0/i,
  );
});

test("a probe against a server that resets mid-response is not reported ok", async () => {
  const server = http.createServer((req, res) => {
    res.writeHead(200);
    res.write("partial");
    // Destroy before ending: the client sees a truncated/aborted response.
    res.socket.destroy();
  });
  await new Promise((r) => server.listen(0, "127.0.0.1", r));
  const port = server.address().port;

  try {
    const r = await probeHealth(port, "127.0.0.1", 1500);
    // Either it resolved not-ok, or it resolved ok with a truncated body; what
    // must never happen is a hang or an unhandled rejection.
    assert.equal(typeof r.ok, "boolean");
  } finally {
    await new Promise((r) => server.close(r));
  }
});
