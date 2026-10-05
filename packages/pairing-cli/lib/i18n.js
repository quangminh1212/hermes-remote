"use strict";

/**
 * Translations for the CLI and the pairing page.
 *
 * Three languages: English (default), Vietnamese, Simplified Chinese.
 *
 * Selection order:
 *   1. `--lang <code>` on the command line
 *   2. the `HERMES_LANG` environment variable
 *   3. the OS locale (LC_ALL / LANG / Intl)
 *   4. English
 *
 * English is the default rather than Vietnamese because the tool is published to
 * npm: an unfamiliar locale should land on the language most likely to be
 * understood, not on the author's.
 */

const LANGS = ["en", "vi", "zh"];

const MESSAGES = {
  en: {
    banner_title: "HERMES REMOTE",
    banner_sub: "Connect an Android phone to Hermes on this machine",

    reading_config: "Read Hermes config: %s",
    lan_address: "LAN address: %s",
    profile_ok: "Phone profile: %s",
    profile_unknown: 'Profile "%s" is not in the list (%s).',
    profile_fallback: "→ Using the top-level key (empty profile).",
    profiles_none: "none",

    cfg_no_env: "Hermes .env not found. Set HERMES_ENV_FILE or install Hermes first.",
    cfg_no_key: "API_SERVER_KEY is empty in %s — enable the Hermes API server first.",
    gw_empty_reply: "(Hermes did not reply)",
    tun_downloading: "Downloading cloudflared (%s)...",
    tun_extracting: "Extracting cloudflared...",
    tun_unzip_failed: "could not extract cloudflared",
    tun_missing_bin: "cloudflared missing after download",
    tun_url_timeout: "timed out waiting for the tunnel URL",
    tun_exited: "cloudflared exited early (code %s)",
    gateway_up: "Gateway is already running (health 200).",
    gateway_restarting: "Gateway not responding — restarting (detached)…",
    gateway_ready: "Gateway is up (health 200).",
    gateway_timeout: "Gateway did not come up within %ss. Check the Hermes logs.",
    gateway_no_cli:
      "Gateway is not running and the `hermes` command was not found. Start the Hermes gateway and try again.",

    tunnel_opening: "Opening a public tunnel (cloudflared)…",
    tunnel_ready: "Public tunnel: %s",
    tunnel_failed: "Could not open a tunnel (%s).",
    tunnel_fallback: "→ Falling back to the LAN address. 4G use needs a tunnel.",
    lan_mode: "LAN mode (no tunnel). The phone must be on the same Wi-Fi.",

    agents_found: "Agent CLIs found: %s",
    agents_none: "No agent CLI found — the app will only reach the Hermes gateway.",
    agents_bridge: "Agent bridge: %s (switch agent with the model field)",
    agents_off: "Agent bridge: off (--no-agents)",

    open_on_phone: "OPEN ON YOUR PHONE",
    open_step1: "1. Install and open the Hermes Remote app",
    open_step2: "2. Tap “Scan QR” and scan the code below",
    open_step3: "3. The app connects by itself",

    web_url: "QR page (webview): %s",
    server_public: "Public server     : %s",
    server_lan: "LAN server        : %s",
    model_label: "Model             : %s",
    model_default: "(default)",

    webhook_title: "WEBHOOK FOR IFTTT / GOOGLE HOME",
    webhook_token_line: "Webhook token     : %s",
    webhook_ping: "Liveness check    : GET %s",
    webhook_off: "Webhook           : off (add --webhook-token <t> to enable)",

    help_usage_title: "Usage:",
    help_options_title: "Options:",
    help_opt_tunnel: "--tunnel            (default) open a public tunnel so 4G works",
    help_opt_no_tunnel: "--no-tunnel, --lan  LAN address only, no public tunnel",
    help_opt_port: "--port <n>          webview port for the QR page (default: auto)",
    help_opt_profile: "--profile <name>    Hermes profile for the phone (default: top-level key)",
    help_opt_webhook: "--webhook-token <t> enable /api/webhook/ask with secret token <t>",
    help_opt_agent: "--agent <id>        agent CLI to use by default (claude, codex, ...)",
    help_opt_no_agents: "--no-agents         do not start the agent bridge",
    help_opt_lang: "--lang <code>       language for this output: en, vi, zh (default: system)",
    help_opt_no_open: "--no-open           do not open the browser automatically",
    help_opt_help: "-h, --help          show this help",
    ctrl_c: "Press Ctrl+C to stop.",

    // ---- pairing page ----
    page_title: "Hermes Remote — Scan the QR to connect",
    page_brand: "Hermes Remote",
    page_running: "Running",
    page_heading: "Scan to connect",
    page_sub: "Open the <b>Hermes Remote</b> app on your phone and scan the code below.",
    page_step1: "Open the <b>Hermes Remote</b> app on Android.",
    page_step2: "Tap <b>Scan QR</b> and point the camera at the code above.",
    page_step3: "The app fills in the server and key, then <b>connects</b>.",
    page_server_public: "Server (public)",
    page_server_lan: "Server (LAN fallback)",
    page_model: "Model",
    page_profile: "Profile",
    page_agents: "Agents",
    page_agents_hint: "Ask the app to use any of these",
    page_default: "(default)",
    page_footer: "Connect data:",
  },

  vi: {
    banner_title: "HERMES REMOTE",
    banner_sub: "Kết nối điện thoại Android với Hermes trên máy này",

    reading_config: "Đọc cấu hình Hermes: %s",
    lan_address: "Địa chỉ LAN: %s",
    profile_ok: "Profile cho điện thoại: %s",
    profile_unknown: 'Profile "%s" không có trong danh sách (%s).',
    profile_fallback: "→ Dùng key gốc (profile rỗng).",
    profiles_none: "không có profile nào",

    cfg_no_env: "Không tìm thấy file .env của Hermes. Đặt biến HERMES_ENV_FILE hoặc cài Hermes trước.",
    cfg_no_key: "API_SERVER_KEY trống trong %s — bật API server Hermes trước.",
    gw_empty_reply: "(Hermes không trả lời)",
    tun_downloading: "Đang tải cloudflared (%s)...",
    tun_extracting: "Giải nén cloudflared...",
    tun_unzip_failed: "giải nén cloudflared thất bại",
    tun_missing_bin: "không thấy cloudflared sau khi tải",
    tun_url_timeout: "Hết thời gian chờ URL tunnel",
    tun_exited: "cloudflared thoát sớm (code %s)",
    gateway_up: "Gateway đã chạy sẵn (health 200).",
    gateway_restarting: "Gateway chưa phản hồi — đang khởi động lại (detached)…",
    gateway_ready: "Gateway đã lên (health 200).",
    gateway_timeout: "Gateway không lên sau %ss. Kiểm tra log Hermes.",
    gateway_no_cli:
      "Gateway chưa chạy và không tìm thấy lệnh `hermes`. Hãy bật Hermes gateway rồi thử lại.",

    tunnel_opening: "Đang mở tunnel công khai (cloudflared)…",
    tunnel_ready: "Tunnel công khai: %s",
    tunnel_failed: "Không mở được tunnel (%s).",
    tunnel_fallback: "→ Sẽ dùng địa chỉ LAN. Dùng từ 4G sẽ cần tunnel.",
    lan_mode: "Chế độ LAN (không tunnel). Điện thoại phải cùng Wi-Fi.",

    agents_found: "Đã tìm thấy CLI agent: %s",
    agents_none: "Không tìm thấy CLI agent nào — app chỉ nối được tới Hermes gateway.",
    agents_bridge: "Agent bridge: %s (đổi agent bằng trường model)",
    agents_off: "Agent bridge: tắt (--no-agents)",

    open_on_phone: "MỞ TRÊN ĐIỆN THOẠI",
    open_step1: "1. Cài & mở app Hermes Remote",
    open_step2: "2. Bấm “Quét QR” rồi quét mã dưới đây",
    open_step3: "3. App tự kết nối",

    web_url: "Trang QR (webview): %s",
    server_public: "Server công khai  : %s",
    server_lan: "Server LAN        : %s",
    model_label: "Model             : %s",
    model_default: "(mặc định)",

    webhook_title: "WEBHOOK CHO IFTTT / GOOGLE HOME",
    webhook_token_line: "Webhook token     : %s",
    webhook_ping: "Kiểm tra sống     : GET %s",
    webhook_off: "Webhook           : tắt (thêm --webhook-token <t> để bật)",

    help_usage_title: "Cách dùng:",
    help_options_title: "Tuỳ chọn:",
    help_opt_tunnel: "--tunnel            (mặc định) mở tunnel công khai để dùng từ 4G",
    help_opt_no_tunnel: "--no-tunnel, --lan  chỉ dùng địa chỉ LAN, không mở tunnel",
    help_opt_port: "--port <n>          cổng webview cho trang QR (mặc định: tự chọn)",
    help_opt_profile: "--profile <tên>     profile Hermes cho điện thoại (mặc định: key gốc)",
    help_opt_webhook: "--webhook-token <t> bật /api/webhook/ask với token bí mật <t>",
    help_opt_agent: "--agent <id>        agent CLI dùng mặc định (claude, codex, ...)",
    help_opt_no_agents: "--no-agents         không khởi động agent bridge",
    help_opt_lang: "--lang <code>       ngôn ngữ cho kết quả này: en, vi, zh (mặc định: hệ thống)",
    help_opt_no_open: "--no-open           không tự mở trình duyệt",
    help_opt_help: "-h, --help          hiện trợ giúp",
    ctrl_c: "Nhấn Ctrl+C để dừng.",

    page_title: "Hermes Remote — Quét QR để kết nối",
    page_brand: "Hermes Remote",
    page_running: "Đang chạy",
    page_heading: "Quét mã để kết nối",
    page_sub: "Mở app <b>Hermes Remote</b> trên điện thoại và quét mã bên dưới.",
    page_step1: "Mở app <b>Hermes Remote</b> trên Android.",
    page_step2: "Bấm <b>Quét QR</b> và hướng camera vào mã trên.",
    page_step3: "App tự điền server, key và <b>kết nối ngay</b>.",
    page_server_public: "Server (public)",
    page_server_lan: "Server (LAN dự phòng)",
    page_model: "Model",
    page_profile: "Profile",
    page_agents: "Agent",
    page_agents_hint: "Yêu cầu app dùng bất kỳ agent nào dưới đây",
    page_default: "(mặc định)",
    page_footer: "Dữ liệu kết nối:",
  },

  zh: {
    banner_title: "HERMES REMOTE",
    banner_sub: "把 Android 手机连接到本机的 Hermes",

    reading_config: "读取 Hermes 配置：%s",
    lan_address: "局域网地址：%s",
    profile_ok: "手机使用的 Profile：%s",
    profile_unknown: 'Profile "%s" 不在列表中（%s）。',
    profile_fallback: "→ 使用顶层密钥（空 profile）。",
    profiles_none: "没有 profile",

    cfg_no_env: "找不到 Hermes 的 .env 文件。请设置 HERMES_ENV_FILE 或先安装 Hermes。",
    cfg_no_key: "%s 中的 API_SERVER_KEY 为空 — 请先启用 Hermes API server。",
    gw_empty_reply: "（Hermes 未回复）",
    tun_downloading: "正在下载 cloudflared（%s）...",
    tun_extracting: "正在解压 cloudflared...",
    tun_unzip_failed: "解压 cloudflared 失败",
    tun_missing_bin: "下载后找不到 cloudflared",
    tun_url_timeout: "等待隧道 URL 超时",
    tun_exited: "cloudflared 提前退出（代码 %s）",
    gateway_up: "Gateway 已在运行（health 200）。",
    gateway_restarting: "Gateway 无响应 — 正在重新启动（后台）…",
    gateway_ready: "Gateway 已启动（health 200）。",
    gateway_timeout: "Gateway 在 %ss 内未启动。请检查 Hermes 日志。",
    gateway_no_cli: "Gateway 未运行，且找不到 `hermes` 命令。请先启动 Hermes gateway 再试。",

    tunnel_opening: "正在打开公网隧道（cloudflared）…",
    tunnel_ready: "公网隧道：%s",
    tunnel_failed: "无法打开隧道（%s）。",
    tunnel_fallback: "→ 将使用局域网地址。用 4G 访问需要隧道。",
    lan_mode: "局域网模式（不使用隧道）。手机必须连接同一 Wi-Fi。",

    agents_found: "发现 Agent CLI：%s",
    agents_none: "未找到任何 Agent CLI — 应用只能连接 Hermes gateway。",
    agents_bridge: "Agent 桥接：%s（通过 model 字段切换 agent）",
    agents_off: "Agent 桥接：已关闭（--no-agents）",

    open_on_phone: "在手机上打开",
    open_step1: "1. 安装并打开 Hermes Remote 应用",
    open_step2: "2. 点击「扫描二维码」并扫描下方二维码",
    open_step3: "3. 应用会自动连接",

    web_url: "二维码页面（webview）：%s",
    server_public: "公网服务器    ：%s",
    server_lan: "局域网服务器  ：%s",
    model_label: "模型          ：%s",
    model_default: "（默认）",

    webhook_title: "用于 IFTTT / GOOGLE HOME 的 WEBHOOK",
    webhook_token_line: "Webhook 令牌  ：%s",
    webhook_ping: "存活检查      ：GET %s",
    webhook_off: "Webhook       ：关闭（加 --webhook-token <t> 开启）",

    help_usage_title: "用法：",
    help_options_title: "选项：",
    help_opt_tunnel: "--tunnel            （默认）打开公网隧道，便于 4G 使用",
    help_opt_no_tunnel: "--no-tunnel, --lan  仅使用局域网地址，不打开隧道",
    help_opt_port: "--port <n>          二维码页面使用的 webview 端口（默认：自动）",
    help_opt_profile: "--profile <名称>    手机使用的 Hermes profile（默认：顶层密钥）",
    help_opt_webhook: "--webhook-token <t> 用密钥 <t> 开启 /api/webhook/ask",
    help_opt_agent: "--agent <id>        默认使用的 agent CLI（claude、codex……）",
    help_opt_no_agents: "--no-agents         不启动 Agent 桥接",
    help_opt_lang: "--lang <code>       本输出的语言：en、vi、zh（默认：系统语言）",
    help_opt_no_open: "--no-open           不自动打开浏览器",
    help_opt_help: "-h, --help          显示此帮助",

    ctrl_c: "按 Ctrl+C 停止。",

    page_title: "Hermes Remote — 扫描二维码以连接",
    page_brand: "Hermes Remote",
    page_running: "运行中",
    page_heading: "扫码连接",
    page_sub: "在手机上打开 <b>Hermes Remote</b> 应用，扫描下方二维码。",
    page_step1: "在 Android 上打开 <b>Hermes Remote</b> 应用。",
    page_step2: "点击 <b>扫描二维码</b>，将摄像头对准上方二维码。",
    page_step3: "应用会自动填入服务器和密钥并<b>立即连接</b>。",
    page_server_public: "服务器（公网）",
    page_server_lan: "服务器（局域网备用）",
    page_model: "模型",
    page_profile: "Profile",
    page_agents: "Agent",
    page_agents_hint: "可让应用使用以下任意 agent",
    page_default: "（默认）",
    page_footer: "连接数据：",
  },
};

/**
 * Pick the language to use.
 *
 * @param {string} [explicit] a `--lang` value
 * @returns {"en"|"vi"|"zh"}
 */
function resolveLang(explicit) {
  const norm = (v) => {
    if (!v) return null;
    const s = String(v).trim().toLowerCase();
    if (!s) return null;
    // "zh-CN" / "zh_CN" / "zh-Hans" -> "zh"; "vi-VN" -> "vi".
    const base = s.split(/[-_.]/)[0];
    return LANGS.includes(base) ? base : null;
  };

  const fromFlag = norm(explicit);
  if (fromFlag) return fromFlag;

  const fromEnv = norm(process.env.HERMES_LANG);
  if (fromEnv) return fromEnv;

  const fromLocale = norm(
    process.env.LC_ALL || process.env.LC_MESSAGES || process.env.LANG,
  );
  if (fromLocale) return fromLocale;

  try {
    const fromIntl = norm(Intl.DateTimeFormat().resolvedOptions().locale);
    if (fromIntl) return fromIntl;
  } catch (_) {
    /* no Intl; fall through */
  }

  return "en";
}

/**
 * Build a translator for one language.
 *
 * @param {string} [lang]
 * @returns {(key:string, ...args:any[]) => string}
 */
function translator(lang) {
  const code = resolveLang(lang);
  const table = MESSAGES[code] || MESSAGES.en;

  return function t(key, ...args) {
    // Fall back to English for a key missing from a translation, so a gap shows
    // as English text rather than the literal key name.
    let s = table[key];
    if (s === undefined) s = MESSAGES.en[key];
    if (s === undefined) return key;

    let i = 0;
    return String(s).replace(/%s/g, () => (i < args.length ? String(args[i++]) : "%s"));
  };
}

module.exports = { LANGS, MESSAGES, resolveLang, translator, detectScript, languageDirective };

/**
 * Guess the language a piece of text is written in.
 *
 * Script-based only: Han characters and Vietnamese diacritics are unambiguous
 * in a way that Latin-letter heuristics are not, so this returns a language
 * only when the evidence is strong and `null` otherwise. A wrong guess would
 * make the agent answer in the wrong language, which is worse than not hinting.
 *
 * @param {string} text
 * @returns {"zh"|"vi"|null}
 */
function detectScript(text) {
  if (!text) return null;
  const s = String(text);

  // Han ideographs (CJK unified + extensions) — present only in Chinese here.
  if (/[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]/.test(s)) return "zh";

  // Vietnamese-specific letters that do not occur in English, French, etc.
  if (/[ăâđêôơưàáảãạằắẳẵặầấẩẫậèéẻẽẹềếểễệìíỉĩịòóỏõọồốổỗộờớởỡợùúủũụừứửữựỳýỷỹỵ]/i.test(s)) {
    return "vi";
  }

  return null;
}

/**
 * Build the instruction prepended to the agent's prompt.
 *
 * The agent has no idea what language the asker used, so without this a Chinese
 * question can come back in English. We only name a language when detection is
 * confident; otherwise we ask it to mirror the question, which is the right
 * behaviour anyway and costs nothing when the guess would have been wrong.
 *
 * @param {string} question the user's own text
 * @returns {string} directive lines, or "" when there is nothing useful to say
 */
function languageDirective(question) {
  const detected = detectScript(question);

  const names = { en: "English", vi: "Vietnamese", zh: "Simplified Chinese" };
  const target = detected ? names[detected] : null;

  if (target) {
    return (
      `[Language] Reply in ${target} only, matching the user's question. ` +
      `Keep code, commands, file paths and error messages exactly as they are.`
    );
  }

  return (
    `[Language] Reply in the same language the user wrote in. ` +
    `Keep code, commands, file paths and error messages exactly as they are.`
  );
}
