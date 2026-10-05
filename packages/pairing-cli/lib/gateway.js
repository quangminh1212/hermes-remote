"use strict";

/**
 * Ensure the Hermes gateway's API server is running and reachable, and that it
 * is bound to a host the tunnel can reach.
 *
 * Strategy:
 *  - Probe http://127.0.0.1:<port>/health.
 *  - If it responds, the gateway is up (the tunnel talks to 127.0.0.1 anyway).
 *  - If not, try `hermes gateway restart` (detached), then poll until healthy.
 */

const http = require("http");
const { spawn, spawnSync } = require("child_process");

function probeHealth(port, host = "127.0.0.1", timeoutMs = 2500) {
  return new Promise((resolve) => {
    const req = http.get(
      { host, port, path: "/health", timeout: timeoutMs },
      (res) => {
        let body = "";
        res.on("data", (c) => (body += c));
        res.on("end", () => resolve({ ok: res.statusCode === 200, status: res.statusCode, body }));
      }
    );
    req.on("timeout", () => {
      req.destroy();
      resolve({ ok: false, status: 0 });
    });
    req.on("error", () => resolve({ ok: false, status: 0 }));
  });
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

/** Return true if a `hermes` CLI exists on PATH. */
function hasHermesCli() {
  const r = spawnSync(process.platform === "win32" ? "where" : "which", ["hermes"]);
  return r.status === 0;
}

/** Restart the gateway detached so it never blocks / hangs this process. */
function restartGatewayDetached() {
  if (process.platform === "win32") {
    const child = spawn("cmd", ["/c", "hermes gateway restart"], {
      detached: true,
      stdio: "ignore",
      windowsHide: true,
    });
    child.unref();
  } else {
    const child = spawn("sh", ["-c", "hermes gateway restart"], {
      detached: true,
      stdio: "ignore",
    });
    child.unref();
  }
}

/**
 * Ensure the API server is healthy. Returns { started: bool, via: string }.
 * If the gateway is already healthy it does nothing.
 */
async function ensureGateway(port, log = () => {}, maxWaitMs = 60000, t = (k) => k) {
  let h = await probeHealth(port);
  if (h.ok) {
    log(t("gateway_up"));
    return { started: false, via: "already" };
  }

  if (!hasHermesCli()) {
    throw new Error(t("gateway_no_cli"));
  }

  log(t("gateway_restarting"));
  restartGatewayDetached();

  const deadline = Date.now() + maxWaitMs;
  while (Date.now() < deadline) {
    await sleep(3000);
    h = await probeHealth(port);
    if (h.ok) {
      log(t("gateway_ready"));
      return { started: true, via: "restart" };
    }
  }
  throw new Error(t("gateway_timeout", maxWaitMs / 1000));
}

module.exports = { probeHealth, ensureGateway, hasHermesCli, restartGatewayDetached };
