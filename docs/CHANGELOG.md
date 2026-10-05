# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- **Internationalisation.** The pairing CLI, the QR pairing page and the Android
  app speak three languages (English, Vietnamese, Simplified Chinese). The CLI
  picks a language from `--lang`, `HERMES_LANG` or the OS locale (English
  default); the app uses the device locale via res qualifiers (`values-vi`,
  `values-zh`). Every user-visible string lives in resources — no literals in
  the ViewModel (new `StringProvider` / `AndroidStringProvider`).
- **Agent bridge.** The pairing CLI can start a sidecar HTTP endpoint
  (`--agent <id>`, off with `--no-agents`) that answers chat by running a local
  agent CLI — `claude`, `codex`, `Anthropic`, `opencode` — and exposes them as
  `/v1/models` so the phone can switch agents through the existing `model`
  field. Windows `.cmd` shim resolution and shell-safe prompting are handled so
  a prompt with quotes or `&` cannot be re-interpreted by cmd.exe. Verified
  end-to-end against a live CLI in all three languages.
- The connect payload now carries `agents` (ids the bridge can answer with) and
  `defaultAgent`, so the phone learns the picker without probing. The terminal
  QR, the on-page QR and `/api/connect` are built from one payload object.

### Fixed
- The pairing page QR and `/api/connect` used to be built separately and
  drifted: the page (the documented scan target) pointed the phone at the
  Hermes gateway and never advertised the agent bridge. Now the CLI constructs a
  single payload and every hand-off route serves it verbatim.
- `ConnectPayload.kt` gained `agents` / `defaultAgent` to match.

## [1.0.0] — Core rewrite

### Added
- Real gesture dispatch via `AccessibilityService.dispatchGesture` (tap, swipe).
- Text input via `AccessibilityNodeInfo.ACTION_SET_TEXT`.
- Screen capture via the API 30+ `takeScreenshot` API (real bitmap, base64 PNG).
- Accessibility UI-tree serialization to JSON.
- App launch by package name.
- Resilient WebSocket client: pairing-code auth, exponential-backoff reconnect,
  OkHttp ping keep-alive, request/response correlation by `request_id`.
- Foreground service so the connection survives the app being backgrounded.
- `ConnectionManager` singleton exposing connection state as a `StateFlow`.
- Jetpack Compose UI wired to a `MainViewModel` (status, URL, pairing code).
- Unit tests for protocol DTOs.
- ProGuard/R8 rules for Gson + OkHttp.

### Removed
- ~40 duplicated / self-congratulatory markdown files.
- Broken placeholder code (synthetic `KeyEvent` for touch, non-existent
  `sendKeyEvent`, empty-bitmap screenshots, private-field assignment errors).
- Stray `nul` file and ad-hoc shell/batch test scripts.

### Fixed
- Manifest referenced missing `@xml/*`, `@mipmap/*` resources — now provided.
- Missing `proguard-rules.pro` for release builds.
- Gradle wrapper pinned to a non-existent distribution (8.14.5 → 8.7).
- Missing `INTERNET`, `FOREGROUND_SERVICE*`, `POST_NOTIFICATIONS` permissions.
