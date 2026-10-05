"use strict";

/**
 * XLab design tokens for the pairing page.
 *
 * Mirrors `tailwind.config.js` in the XLab web project and
 * `XLabTheme.kt` in the Android app, so all three surfaces read as one product.
 * The values are duplicated per runtime (no shared build step) but named
 * identically, which makes drift obvious.
 */
const TOKENS = {
  primary: "#00A19A",
  primaryDark: "#0D9488",
  primaryLight: "#33D6D6",
  secondary: "#37C88F",

  bgPrimary: "#1E293B",
  bgSecondary: "#334155",
  bgTertiary: "#475569",
  bgElevated: "#64748B",

  textPrimary: "#F7F9FB",
  textSecondary: "#C9D1D9",
  textTertiary: "#8B949E",

  borderSubtle: "#475569",
  border: "#64748B",

  success: "#3FB950",
  warning: "#D29922",
  error: "#F85149",
};

/** Escape a value for safe interpolation into HTML text or an attribute. */
function esc(s) {
  return String(s).replace(
    /[&<>"']/g,
    (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]),
  );
}

const STYLES = `
  :root {
    color-scheme: dark;
    --primary: ${TOKENS.primary};
    --primary-dark: ${TOKENS.primaryDark};
    --bg-primary: ${TOKENS.bgPrimary};
    --bg-secondary: ${TOKENS.bgSecondary};
    --bg-tertiary: ${TOKENS.bgTertiary};
    --text-primary: ${TOKENS.textPrimary};
    --text-secondary: ${TOKENS.textSecondary};
    --text-tertiary: ${TOKENS.textTertiary};
    --border: ${TOKENS.border};
    --border-subtle: ${TOKENS.borderSubtle};
    --success: ${TOKENS.success};
  }
  * { box-sizing: border-box; }
  body {
    margin: 0;
    min-height: 100vh;
    display: grid;
    place-items: center;
    padding: 24px 0;
    font-family: var(--font-inter), ui-sans-serif, system-ui, -apple-system,
      "Segoe UI", Roboto, sans-serif;
    background:
      radial-gradient(60rem 40rem at 50% -10%, rgba(0,161,154,.16), transparent 60%),
      var(--bg-primary);
    color: var(--text-primary);
  }
  .card {
    width: min(560px, 92vw);
    background: var(--bg-secondary);
    border: 1px solid var(--border-subtle);
    border-radius: 1rem;
    padding: 32px;
    text-align: center;
    box-shadow: 0 25px 50px -12px rgba(0,0,0,.45);
  }
  .brand { display: flex; align-items: center; justify-content: center; gap: 10px; margin-bottom: 18px; }
  .logo {
    width: 34px; height: 34px; border-radius: 9px; flex: none;
    background: linear-gradient(135deg, var(--primary), var(--primary-dark));
    display: grid; place-items: center;
    font-weight: 700; font-size: 17px; color: #fff;
  }
  .brand-name { font-weight: 650; letter-spacing: .2px; }
  h1 { margin: 0 0 6px; font-size: 22px; font-weight: 650; }
  .sub { color: var(--text-secondary); font-size: 14px; margin-bottom: 22px; line-height: 1.5; }
  .pill {
    display: inline-flex; align-items: center; gap: 7px;
    padding: 4px 12px; border-radius: 9999px;
    font-size: 12px; font-weight: 500;
    background: rgba(63,185,80,.12);
    color: var(--success);
    border: 1px solid rgba(63,185,80,.35);
    margin-bottom: 18px;
  }
  .dot { width: 6px; height: 6px; border-radius: 9999px; background: var(--success);
    box-shadow: 0 0 0 3px rgba(63,185,80,.2); }
  .qr {
    background: #fff; border-radius: 1rem; padding: 16px;
    display: inline-block; line-height: 0;
    box-shadow: 0 10px 15px -3px rgba(0,0,0,.35);
  }
  .qr img { display: block; width: 300px; height: 300px; border-radius: 4px; }
  .steps { text-align: left; margin: 24px auto 0; max-width: 400px; font-size: 14px; line-height: 1.7; }
  .steps .n {
    display: inline-grid; place-items: center;
    width: 20px; height: 20px; border-radius: 9999px; margin-right: 8px;
    background: var(--primary); color: #fff; font-size: 11px; font-weight: 700;
    vertical-align: 1px;
  }
  .steps b { color: var(--primary-light, #33D6D6); font-weight: 600; }
  code {
    background: var(--bg-primary); padding: 2px 7px; border-radius: .375rem;
    font-size: 12.5px; color: var(--text-secondary);
    border: 1px solid var(--border-subtle);
  }
  .meta { margin-top: 26px; border-top: 1px solid var(--border-subtle); padding-top: 6px; }
  .row {
    display: flex; justify-content: space-between; gap: 14px;
    padding: 9px 0; border-bottom: 1px solid var(--border-subtle);
    font-size: 13px; text-align: left;
  }
  .row:last-child { border-bottom: 0; }
  .row .k { color: var(--text-tertiary); flex: none; }
  .row .v { color: var(--text-secondary); word-break: break-all; text-align: right; }
  .foot { margin-top: 18px; font-size: 12px; color: var(--text-tertiary); }
`;

/**
 * Render the pairing page.
 *
 * @param {{baseUrl:string, fallbackUrl:string, model?:string, profile?:string}} p
 * @returns {string} full HTML document
 */
function renderPage(p) {
  const profile = p.profile && String(p.profile).trim() ? p.profile : "mặc định (.env)";

  return `<!doctype html>
<html lang="vi">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Hermes Remote — Quét QR để kết nối</title>
<style>${STYLES}</style>
</head>
<body>
  <main class="card">
    <div class="brand">
      <div class="logo">H</div>
      <span class="brand-name">Hermes Remote</span>
    </div>

    <div class="pill"><span class="dot"></span>Đang chạy</div>
    <h1>Quét mã để kết nối</h1>
    <p class="sub">Mở app <b>Hermes Remote</b> trên điện thoại và quét mã bên dưới.</p>

    <div class="qr"><img src="/qr.png" alt="Mã QR kết nối" width="300" height="300"></div>

    <div class="steps">
      <div><span class="n">1</span>Mở app <b>Hermes Remote</b> trên Android.</div>
      <div><span class="n">2</span>Bấm <b>Quét QR</b> và hướng camera vào mã trên.</div>
      <div><span class="n">3</span>App tự điền server, key và <b>kết nối ngay</b>.</div>
    </div>

    <div class="meta">
      <div class="row"><span class="k">Server (public)</span><span class="v">${esc(p.baseUrl)}</span></div>
      <div class="row"><span class="k">Server (LAN dự phòng)</span><span class="v">${esc(p.fallbackUrl)}</span></div>
      <div class="row"><span class="k">Model</span><span class="v">${esc(p.model || "(mặc định)")}</span></div>
      <div class="row"><span class="k">Profile</span><span class="v">${esc(profile)}</span></div>
    </div>

    <p class="foot">Dữ liệu kết nối: <code>/api/connect</code></p>
  </main>
</body>
</html>`;
}

module.exports = { renderPage, esc, TOKENS };
