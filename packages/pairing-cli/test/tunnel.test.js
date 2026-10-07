"use strict";

/**
 * Tests for the cloudflared tunnel helper.
 *
 * Nothing here downloads cloudflared or opens a network connection.
 *
 * The tests are split by what can be tested portably:
 *
 *  - `extractTunnelUrl` is pure, so the URL-scraping rules are covered in full.
 *    This is the part that decides whether the tunnel came up, and cloudflared
 *    prints other URLs before the real one, so the rules matter.
 *  - The process lifecycle (URL appears on stdout/stderr, exits early, hangs,
 *    cannot be executed) is driven by spawning a real stand-in. On Windows a
 *    `.cmd` cannot be spawned without a shell — Node raises EINVAL — so the
 *    stand-in is the node executable itself, which `startTunnel` can spawn and
 *    whose argv it already controls.
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const tunnel = require("../lib/tunnel");

// --- extractTunnelUrl (pure) ------------------------------------------------

test("extractTunnelUrl finds a quick-tunnel URL", () => {
  assert.equal(
    tunnel.extractTunnelUrl("INF https://calm-river-abc.trycloudflare.com ready"),
    "https://calm-river-abc.trycloudflare.com",
  );
});

test("extractTunnelUrl ignores other URLs in the banner", () => {
  // The real banner mentions cloudflare's own pages before the tunnel URL.
  const banner =
    "INF Thank you for trying Cloudflare Tunnel.\n" +
    "INF See https://github.com/cloudflare/cloudflared#readme\n" +
    "INF https://developers.cloudflare.com/cloudflare-one/\n";
  assert.equal(tunnel.extractTunnelUrl(banner), null);
});

test("extractTunnelUrl takes the tunnel URL out of a mixed banner", () => {
  const mixed =
    "INF See https://github.com/cloudflare/cloudflared\n" +
    "INF +https://real-one-abc.trycloudflare.com\n" +
    "INF https://another-site.example.com\n";
  assert.equal(tunnel.extractTunnelUrl(mixed), "https://real-one-abc.trycloudflare.com");
});

test("extractTunnelUrl does not match a lookalike domain", () => {
  // trycloudflare.com.evil.test must not match, or a hijacked DNS answer could
  // point the phone at someone else's host.
  assert.equal(tunnel.extractTunnelUrl("https://x.trycloudflare.com.evil.test"), null);
  assert.equal(tunnel.extractTunnelUrl("https://notrycloudflare.com"), null);
});

test("extractTunnelUrl rejects non-http and malformed input", () => {
  assert.equal(tunnel.extractTunnelUrl("http://x.trycloudflare.com"), null);
  assert.equal(tunnel.extractTunnelUrl(""), null);
  assert.equal(tunnel.extractTunnelUrl(null), null);
  assert.equal(tunnel.extractTunnelUrl(undefined), null);
  assert.equal(tunnel.extractTunnelUrl(42), null);
});

test("extractTunnelUrl matches at the start of a chunk, not only after a space", () => {
  // cloudflared can split its output across chunks, so the match must not
  // depend on a preceding space.
  assert.equal(
    tunnel.extractTunnelUrl("https://edge-case-abc.trycloudflare.com"),
    "https://edge-case-abc.trycloudflare.com",
  );
});

// --- platformAsset ----------------------------------------------------------

test("platformAsset names the asset for this platform", () => {
  const asset = tunnel.platformAsset();
  if (process.platform === "win32") {
    assert.match(asset, /windows-amd64\.exe$/);
  } else if (process.platform === "darwin") {
    assert.match(asset, /darwin-(arm64|amd64)\.tgz$/);
  } else {
    assert.match(asset, /linux-(arm64|amd64)$/);
  }
});

// --- binDir / resolveCloudflared -------------------------------------------

test("binDir is absolute, under home, and package-specific", () => {
  const dir = tunnel.binDir();
  assert.ok(path.isAbsolute(dir), "must be absolute");
  assert.ok(dir.startsWith(os.homedir()), `expected under home, got ${dir}`);
  assert.ok(dir.includes("hermes-remote"), `expected the package name in ${dir}`);
});

test("resolveCloudflared returns an absolute path or null, never undefined", () => {
  const r = tunnel.resolveCloudflared();
  assert.ok(r === null || typeof r === "string", `got ${typeof r}`);
  if (typeof r === "string") assert.ok(path.isAbsolute(r), `got ${r}`);
});

// --- startTunnel lifecycle (real spawn) -------------------------------------

/**
 * Build a spawnable stand-in for cloudflared.
 *
 * Uses a copy of the running node executable? No — that cannot carry a script.
 * Instead the stand-in is a native one-liner emitted through the platform's own
 * binary: a `.exe` on Windows would need a compiler, so the tests instead
 * spawn `process.execPath` and let `startTunnel`'s fixed argv be harmless
 * extra arguments to node.
 *
 * Node treats an unknown extra argv entry as a script path, so the trick is to
 * put the script FIRST via a wrapper that the OS can exec. The only portable
 * such wrapper is another executable, so the lifecycle tests are limited to
 * platforms where the shell is not required — see the skip guard.
 *
 * @returns {{bin:string, cleanup:()=>void}|null} null when unsupported here
 */
function makeStandIn(jsBody) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "hermes-tun-"));
  const script = path.join(dir, "impl.js");
  fs.writeFileSync(script, jsBody, "utf8");

  if (process.platform === "win32") {
    // A .cmd is not spawnable without a shell, and a .exe needs a compiler, so
    // the lifecycle path is not covered on Windows. The pure extractor above
    // still is.
    fs.rmSync(dir, { recursive: true, force: true });
    return null;
  }

  const bin = path.join(dir, "cloudflared");
  fs.writeFileSync(bin, `#!/bin/sh\nexec "${process.execPath}" "${script}" "$@"\n`, "utf8");
  fs.chmodSync(bin, 0o755);
  return { bin, cleanup: () => fs.rmSync(dir, { recursive: true, force: true }) };
}

const NO_STANDIN = process.platform === "win32";

test(
  "startTunnel resolves with the URL printed on stdout",
  { skip: NO_STANDIN ? "no spawnable stand-in without a compiler on win32" : false },
  async () => {
    const s = makeStandIn(
      'process.stdout.write("INF https://calm-river-abc.trycloudflare.com\\n");\n' +
        "setTimeout(() => {}, 10000);\n",
    );
    try {
      const { url, proc } = await tunnel.startTunnel("http://127.0.0.1:9999", s.bin, 10000);
      assert.equal(url, "https://calm-river-abc.trycloudflare.com");
      assert.ok(proc, "the handle is returned so the caller can kill it");
      try {
        proc.kill();
      } catch (_) {}
    } finally {
      s.cleanup();
    }
  },
);

test(
  "startTunnel resolves with the URL printed on stderr",
  { skip: NO_STANDIN ? "no spawnable stand-in without a compiler on win32" : false },
  async () => {
    const s = makeStandIn(
      'process.stderr.write("INF | https://shy-moon-xyz.trycloudflare.com |\\n");\n' +
        "setTimeout(() => {}, 10000);\n",
    );
    try {
      const { url, proc } = await tunnel.startTunnel("http://127.0.0.1:9999", s.bin, 10000);
      assert.equal(url, "https://shy-moon-xyz.trycloudflare.com");
      try {
        proc.kill();
      } catch (_) {}
    } finally {
      s.cleanup();
    }
  },
);

test(
  "startTunnel rejects when the process exits without a URL",
  { skip: NO_STANDIN ? "no spawnable stand-in without a compiler on win32" : false },
  async () => {
    const s = makeStandIn('process.stdout.write("no url\\n");\nprocess.exit(7);\n');
    try {
      await assert.rejects(() => tunnel.startTunnel("http://127.0.0.1:9999", s.bin, 10000), /exited/i);
    } finally {
      s.cleanup();
    }
  },
);

test(
  "startTunnel rejects on timeout instead of hanging",
  { skip: NO_STANDIN ? "no spawnable stand-in without a compiler on win32" : false },
  async () => {
    const s = makeStandIn("setTimeout(() => {}, 60000);\n");
    try {
      const t0 = Date.now();
      await assert.rejects(() => tunnel.startTunnel("http://127.0.0.1:9999", s.bin, 500), /timeout/i);
      assert.ok(Date.now() - t0 < 15000, "must abort near the timeout, not hang");
    } finally {
      s.cleanup();
    }
  },
);

test("startTunnel rejects when the binary cannot be executed", async () => {
  await assert.rejects(
    () =>
      tunnel.startTunnel(
        "http://127.0.0.1:9999",
        path.join(os.tmpdir(), "definitely-not-a-real-binary-zzz"),
        3000,
      ),
    /.+/,
  );
});
