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
const { createServer, listen } = require("../lib/server");

function parseArgs(argv) {
  const a = { tunnel: true, open: true, port: 0 };
  for (let i = 0; i < argv.length; i++) {
    const t = argv[i];
    if (t === "--no-tunnel" || t === "--lan") a.tunnel = false;
    else if (t === "--tunnel") a.tunnel = true;
    else if (t === "--no-open") a.open = false;
    else if (t === "--port") a.port = parseInt(argv[++i] || "0", 10);
    else if (t === "--help" || t === "-h") a.help = true;
  }
  return a;
}

function printHelp() {
  console.log(`
hermes-remote — kết nối app Hermes Remote (Android) với Hermes trên PC này.

Cách dùng:
  hermes-remote [tuỳ chọn]

Tuỳ chọn:
  --no-tunnel, --lan   Chỉ dùng địa chỉ LAN, không mở tunnel công khai
  --tunnel             (mặc định) Mở tunnel công khai để dùng từ 4G
  --port <n>           Cổng cho trang webview QR (mặc định: tự chọn)
  --no-open            Không tự mở trình duyệt
  -h, --help           Hiện trợ giúp
`);
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

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) return printHelp();

  banner(["HERMES REMOTE", "Kết nối điện thoại Android với Hermes trên máy này"]);

  // 1. Read Hermes config.
  const cfg = config.readHermesConfig();
  console.log("  ✔ Đọc cấu hình Hermes:", cfg.envFile);
  const profiles = config.listProfiles(cfg.envFile);
  const localUrl = `http://${lanIP()}:${cfg.port}`;
  console.log("  ✔ Địa chỉ LAN:", localUrl);

  // 2. Make sure the gateway is up.
  await gateway.ensureGateway(cfg.port, (m) => console.log("  … " + m));

  // 3. Tunnel (default) for use anywhere.
  let publicUrl = localUrl;
  let tunnelProc = null;
  if (args.tunnel) {
    try {
      const bin = await tunnel.ensureCloudflared((m) => console.log("  … " + m));
      console.log("  … Đang mở tunnel công khai (cloudflared)...");
      const t = await tunnel.startTunnel(`http://127.0.0.1:${cfg.port}`, bin);
      publicUrl = t.url;
      tunnelProc = t.proc;
      console.log("  ✔ Tunnel công khai:", publicUrl);
    } catch (e) {
      console.log("  ⚠ Không mở được tunnel (" + e.message + ").");
      console.log("    → Sẽ dùng địa chỉ LAN. Dùng từ 4G sẽ cần tunnel.");
    }
  } else {
    console.log("  ℹ Chế độ LAN (không tunnel). Điện thoại phải cùng Wi-Fi.");
  }

  // 4. Start the webview QR server.
  const app = createServer({
    publicUrl,
    localUrl,
    apiKey: cfg.apiKey,
    model: cfg.model,
    profiles,
  });
  const { url: webUrl, port: webPort } = await listen(app, args.port);

  // 5. Present it.
  banner([
    "MỞ TRÊN ĐIỆN THOẠI",
    "1. Cài & mở app Hermes Remote",
    "2. Bấm “Quét QR” rồi quét mã dưới đây",
    "3. App tự kết nối",
  ]);
  await printAsciiQR({
    v: 1,
    baseUrl: publicUrl,
    fallbackUrl: localUrl,
    apiKey: cfg.apiKey,
    profile: "",
    model: cfg.model,
    profiles,
  });

  console.log("  Trang QR (webview): " + webUrl);
  console.log("  Server công khai  : " + publicUrl);
  console.log("  Server LAN        : " + localUrl);
  console.log("  Model             : " + (cfg.model || "(mặc định)"));
  console.log("\n  Nhấn Ctrl+C để dừng.\n");

  if (args.open) openBrowser(webUrl);

  const shutdown = () => {
    console.log("\n  Đang đóng...");
    if (tunnelProc) try { tunnelProc.kill(); } catch (_) {}
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
