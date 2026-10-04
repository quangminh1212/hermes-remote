# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

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
