#!/usr/bin/env node
"use strict";

/**
 * hermes-remote — one command to bridge a PC's Hermes agent to an Android phone.
 *
 *   npx hermes-remote            # tunnel by default (public URL in the QR)
 *   hermes-remote --no-tunnel    # LAN only
 *   hermes-remote --lan           # alias for --no-tunnel
 *   hermes-remote --port 8765     # fixed webview port
 *   hermes-remote --no-open       # don't auto-open the browser
 */

const os = require("os");
const { spawn } = require("child_process");
const QRCode = require("qrcode");

const config = require("../lib/config");
const gateway = require("../lib/gateway");
const tunnel = require("../lib/tunnel");
const registry = require("../lib/agent-registry");
const bridge = require("../lib/agent-bridge");
const { translator, resolveLang, LANGS } = require("../lib/i18n");
const { createServer, listen } = require("../lib/server");

function parseArgs(argv) {
  const a = {
    tunnel: true,
    open: true,
    port: 0,
    profile: "",
    webhookToken: "",
    agents: true,
    agent: "",
    lang: "",
  };
  for (let i = 0; i < argv.length; i++) {
    const t = argv[i];
    if (t === "--no-tunnel" || t === "--lan") a.tunnel = false;
    else if (t === "--tunnel") a.tunnel = true;
    else if (t === "--no-open") a.open = false;
    else if (t === "--port") a.port = parseInt(argv[++i] || "0", 10);
    else if (t === "--profile") a.profile = String(argv[++i] || "");
    else if (t === "--webhook-token") a.webhookToken = String(argv[++i] || "");
    else if (t === "--no-webhook") a.webhookToken = "";
    else if (t === "--agent") a.agent = String(argv[++i] || "");
    else if (t === "--no-agents") a.agents = false;
    else if (t === "--lang") a.lang = String(argv[++i] || "");
    else if (t === "--help" || t === "-h") a.help = true;
  }
  return a;
}

/**
 * Print usage.
 *
 * The option list is translated (see lib/i18n.js); English is the fallback so a
 * reader with an unexpected locale still gets understandable help.
 */
function printHelp(lang) {
  const t = translator(lang);
  const lines = [
    "",
    t("help_usage_title"),
    "  hermes-remote [options]",
    "",
    t("help_options_title"),
    "  " + t("help_opt_tunnel"),
    "  " + t("help_opt_no_tunnel"),
    "  " + t("help_opt_port"),
    "  " + t("help_opt_profile"),
    "  " + t("help_opt_webhook"),
    "  " + t("help_opt_agent"),
    "  " + t("help_opt_no_agents"),
    "  " + t("help_opt_lang"),
    "  " + t("help_opt_no_open"),
    "  " + t("help_opt_help"),
    "",
  ];
  console.log(lines.join("\n"));
}

/** Best-effort LAN IPv4 of this machine. */
function lanIP() {
  const nets = os.networkInterfaces();
  for (const name of Object.keys(nets)) {
    for (const ni of nets[name] || []) {
      if (ni.family === "IPv4" && !ni.internal) return ni.address;
    }
  }
  return "127.0.0.1";
}

/** Open a URL in the default browser (best effort). */
function openBrowser(url) {
  try {
    if (process.platform === "win32") {
      spawn("cmd", ["/c", "start", "", url], { detached: true, stdio: "ignore" }).unref();
    } else if (process.platform === "darwin") {
      spawn("open", [url], { detached: true, stdio: "ignore" }).unref();
    } else {
      spawn("xdg-open", [url], { detached: true, stdio: "ignore" }).unref();
    }
  } catch (_) {
    /* ignore */
  }
}

/** Print the connect payload as an ASCII QR in the terminal (fallback). */
async function printAsciiQR(payload) {
  try {
    const s = await QRCode.toString(JSON.stringify(payload), {
      type: "terminal",
      small: true,
      errorCorrectionLevel: "M",
    });
    console.log(s);
  } catch (_) {
    /* terminal may not render; the browser page still works */
  }
}

function banner(lines) {
  console.log("\n" + "─".repeat(58));
  for (const l of lines) console.log("  " + l);
  console.log("─".repeat(58) + "\n");
}

/**
 * Start the agent bridge in-process.
 *
 * Runs in this process rather than as a child so Ctrl+C tears everything down
 * together, and so the bridge can report "agent not installed" as a live 503
 * instead of the port simply being closed.
 *
 * @param {{port:number, apiKey:string, defaultAgent?:string, host?:string}} opts
 * @returns {Promise<{url:string, close:() => Promise<void>, proc:null}>}
 */
async function startAgentBridge(opts) {
  const host = opts.host || "0.0.0.0";
  const handle = await bridge.listenBridge(
    { apiKey: opts.apiKey, defaultAgent: opts.defaultAgent || "" },
    opts.port,
    host,
  );
  return {
    url: `http://${lanIP()}:${handle.port}`,
    close: handle.close,
    // No child process: `proc` is kept so shutdown() can stay shape-compatible.
    proc: null,
    _handle: handle,
  };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const t = translator(args.lang);
  if (args.help) return printHelp(args.lang);

  banner([t("banner_title"), t("banner_sub")]);

  // 1. Read Hermes config.
  const cfg = config.readHermesConfig(null, t);
  console.log("  ✔ " + t("reading_config", cfg.envFile));
  const profiles = config.listProfiles(cfg.envFile);
  const localUrl = `http://${lanIP()}:${cfg.port}`;
  console.log("  ✔ " + t("lan_address", localUrl));

  // Pick the profile the phone will connect as. Empty = top-level key.
  let profile = args.profile || "";
  if (profile && !profiles.includes(profile)) {
    console.log(
      "  ⚠ " + t("profile_unknown", profile, profiles.length ? profiles.join(", ") : t("profiles_none"))
    );
    console.log("    " + t("profile_fallback"));
    profile = "";
  } else if (profile) {
    console.log("  ✔ " + t("profile_ok", profile));
  }

  // 2. Make sure the gateway is up.
  await gateway.ensureGateway(cfg.port, (m) => console.log("  … " + m), 60000, t);

  // 3. Start the agent bridge, unless disabled.
  //
  // This is what lets the phone pick *which* CLI answers: the app always POSTs
  // to one endpoint, and the model field in that request names the agent. The
  // bridge listens on a port of its own so it never shadows the Hermes gateway.
  let bridgeProc = null;
  let bridgeHandle = null;
  let bridgeUrl = "";
  let agents = [];
  if (args.agents) {
    // available() returns descriptors; the CLI only needs the ids, both for
    // the console line and for the QR/page payload the phone reads.
    agents = registry.available().map((a) => a.id);
    if (agents.length) {
      console.log("  ✔ " + t("agents_found", agents.join(", ")));
      const started = await startAgentBridge({
        port: cfg.port + 1,
        apiKey: cfg.apiKey,
        defaultAgent: args.agent,
      });
      bridgeProc = started.proc;
      bridgeHandle = started._handle;
      bridgeUrl = started.url;
      console.log("  ✔ " + t("agents_bridge", bridgeUrl));
    } else {
      console.log("  ℹ " + t("agents_none"));
    }
  } else {
    console.log("  ℹ " + t("agents_off"));
  }

  // 4. Tunnel (default) for use anywhere.
  let publicUrl = localUrl;
  let tunnelProc = null;
  if (args.tunnel) {
    try {
      const bin = await tunnel.ensureCloudflared((m) => console.log("  … " + m), t);
      console.log("  … " + t("tunnel_opening"));
      const tun = await tunnel.startTunnel(`http://127.0.0.1:${cfg.port}`, bin, 45000, t);
      publicUrl = tun.url;
      tunnelProc = tun.proc;
      console.log("  ✔ " + t("tunnel_ready", publicUrl));
    } catch (e) {
      console.log("  ⚠ " + t("tunnel_failed", e.message));
      console.log("    " + t("tunnel_fallback"));
    }
  } else {
    console.log("  ℹ " + t("lan_mode"));
  }

  // 5. The one payload the phone stores, built once and used for every route
  // that hands it over: the terminal QR, the on-screen QR (data URL), and
  // /api/connect. Building it in one place is deliberate — when the two QR
  // codes were assembled separately they drifted, and the page QR pointed the
  // phone at the gateway while the terminal QR pointed it at the bridge.
  //
  // It carries whichever endpoint actually serves chat: the bridge when it is
  // running (so the phone can choose an agent), otherwise the gateway.
  const chatUrl = bridgeUrl || publicUrl;
  const connectPayload = {
    v: 1,
    baseUrl: chatUrl,
    fallbackUrl: bridgeUrl ? `http://${lanIP()}:${cfg.port + 1}` : localUrl,
    apiKey: cfg.apiKey,
    profile,
    model: cfg.model,
    profiles,
    // The agent ids the bridge can answer with, so the app can offer a picker
    // without probing. Empty when the bridge is off; the app then falls back to
    // the plain model field.
    agents,
    defaultAgent: args.agent || "",
    lang: resolveLang(args.lang),
  };

  // 6. Start the webview QR server, handing it that same payload.
  const app = createServer({
    payload: connectPayload,
    publicUrl,
    localUrl,
    apiKey: cfg.apiKey,
    model: cfg.model,
    profiles,
    profile,
    gatewayUrl: localUrl,
    webhookToken: args.webhookToken,
    lang: resolveLang(args.lang),
    agents,
  });
  const { url: webUrl, port: webPort } = await listen(app, args.port);

  // 7. Present it.
  banner([t("open_on_phone"), t("open_step1"), t("open_step2"), t("open_step3")]);

  await printAsciiQR(connectPayload);

  console.log("  " + t("web_url", webUrl));
  console.log("  " + t("server_public", publicUrl));
  console.log("  " + t("server_lan", localUrl));
  console.log("  " + t("model_label", cfg.model || t("model_default")));

  if (args.webhookToken) {
    banner([
      t("webhook_title"),
      "POST " + webUrl + "/api/webhook/ask",
      'Body JSON: {"token":"<token>","text":"..."}',
    ]);
    console.log("  " + t("webhook_token_line", args.webhookToken));
    console.log("  " + t("webhook_ping", webUrl + "/api/webhook/ping"));
  } else {
    console.log("  " + t("webhook_off"));
  }

  console.log("\n  " + t("ctrl_c") + "\n");

  if (args.open) openBrowser(webUrl);

  const shutdown = () => {
    if (tunnelProc) try { tunnelProc.kill(); } catch (_) {}
    if (bridgeProc) try { bridgeProc.kill(); } catch (_) {}
    // The bridge runs in-process, so closing it is asynchronous.
    if (bridgeHandle) { try { bridgeHandle.close(); } catch (_) {} }
    process.exit(0);
  };
  process.on("SIGINT", shutdown);
  process.on("SIGTERM", shutdown);
}

if (require.main === module) {
  main().catch((e) => {
    console.error("\n  ✖ Lỗi:", e.message, "\n");
    process.exit(1);
  });
}

module.exports = { main, parseArgs, lanIP };
