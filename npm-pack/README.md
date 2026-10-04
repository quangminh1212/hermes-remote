# hermes-remote

One command to connect the **Hermes Remote** Android app to the Hermes agent
running on your PC. It starts the gateway, opens a public tunnel, and shows a
QR code your phone scans to configure itself — no typing URLs or keys.

```
npx hermes-remote
```

## What it does

1. Reads your Hermes `.env` (API key, port, model) automatically.
2. Makes sure the Hermes gateway API server is running (`/health`).
3. Opens a **Cloudflare quick tunnel** so the phone can reach your PC from
   anywhere — same Wi-Fi or 4G. (Downloads `cloudflared` on first run.)
4. Serves a small web page with a **QR code** containing the connection info,
   and opens it in your browser.

Then on the phone: open the **Hermes Remote** app → tap **Quét QR** → point the
camera at the code → the app configures and connects itself.

## Requirements

- Node.js 18+
- Hermes installed on this machine (its `.env` must have `API_SERVER_KEY`).

## Usage

```bash
npx hermes-remote              # tunnel + QR page (recommended)
npx hermes-remote --no-tunnel  # LAN only (phone must be on the same Wi-Fi)
npx hermes-remote --port 8765  # fixed webview port
npx hermes-remote --no-open    # don't auto-open the browser
```

Options:

| Flag | Meaning |
|------|---------|
| `--tunnel` | (default) public Cloudflare tunnel so it works over 4G |
| `--no-tunnel`, `--lan` | skip the tunnel; use the LAN address only |
| `--port <n>` | port for the local QR web page |
| `--no-open` | do not open the browser automatically |
| `-h`, `--help` | show help |

## The QR payload

The QR encodes a compact JSON the app understands:

```json
{
  "v": 1,
  "baseUrl": "https://xxxx.trycloudflare.com",
  "fallbackUrl": "http://192.168.1.113:8642",
  "apiKey": "…",
  "profile": "",
  "model": "…",
  "profiles": ["dalek", "doraemon"]
}
```

The web page also exposes it as JSON at `/api/connect` (handy for debugging or
for an app that can't scan).

## Notes

- `profile` is empty on purpose: it uses the top-level `.env` API key. Named
  Hermes profiles need their own `API_SERVER_KEY`, otherwise the API returns
  401.
- The quick tunnel URL changes every run. Re-scan the QR if you restart.
- Ctrl+C stops the tunnel and the web page.

## Troubleshooting

- **"Không tìm thấy file .env của Hermes"** — set `HERMES_ENV_FILE` to the path
  of your Hermes `.env`.
- **"Gateway chưa chạy và không tìm thấy lệnh hermes"** — install Hermes / make
  sure `hermes` is on your `PATH`.
- **Phone can't connect over 4G** — the tunnel failed to open; check your
  network, or use `--no-tunnel` on the same Wi-Fi.
