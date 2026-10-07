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
- The connect payload now carries `agents` (ids the bridge can answer with) and
  `defaultAgent`, so the phone learns the picker without probing. The terminal
  QR, the on-page QR and `/api/connect` are built from one payload object.
- **Light mode, and a darker dark.** The app now has two full palettes and a
  Settings → Appearance choice of System / Dark / Light. The dark palette was
  rebuilt on neutral black (`#0C0C0C`) instead of the slate ramp (`#1E293B` and
  up), which read as muddy blue-grey; teal is now the only hue in the chrome.
  Colours moved from a constants object to a `CompositionLocal`-backed palette
  because a constant cannot change with the mode.
- **Coverage, lint and a cross-platform CI matrix** for `packages/pairing-cli`.
  Thresholds (80% lines/functions, 75% branches) live in the npm script so CI
  cannot drift from local runs. `npm run lint` is dependency-free and checks
  parse errors, duplicate `module.exports`, unresolvable relative requires and
  stray logging. CI runs ubuntu/windows/macos against Node 20 and 22, plus a
  `npm pack` → clean-install → run-the-binary job.
- **[docs/TESTING.md](TESTING.md)** — what each layer owns, why some tests skip,
  and an explicit list of what is deliberately not tested.
- New suites: `route-agents.test.js`, `config.test.js`, `gateway.test.js`,
  `tunnel.test.js`, `ThemeModeTest`, `PaletteTest`. `PaletteTest` measures real
  WCAG contrast ratios rather than asserting hex values.

### Changed
- **The bridge now serves one agent: `hermes`.** Adapters for `claude`, `codex`,
  `grok` and `opencode` were removed along with their tests. Each spawned CLI
  brought its own flags, failure modes and quoting rules, on a phone where the
  user cannot see the child process or its stderr. The adapter contract is
  unchanged, so adding one back stays cheap.
- **Dense chat UI.** Status card collapsed to a single thin bar, agent picker
  moved into Settings, radii reduced to a named scale (6dp control / 8dp bubble
  / 4dp chip), banners became a 2px leading rule, per-message name captions
  dropped, uppercase labels removed. The transcript's share of the screen rose
  from roughly half to roughly three quarters.
- The agent picker and the "agents" info row are hidden when only one agent is
  reported, since a one-option picker cannot be used.

### Fixed
- The pairing page QR and `/api/connect` used to be built separately and
  drifted: the page (the documented scan target) pointed the phone at the
  Hermes gateway and never advertised the agent bridge. Now the CLI constructs a
  single payload and every hand-off route serves it verbatim.
- `ConnectPayload.kt` gained `agents` / `defaultAgent` to match.
- **The QR pointed the phone at a bridge that could not answer it.** With only
  the `hermes` passthrough left, the CLI started an agent bridge and advertised
  it as the chat endpoint — but the bridge refuses passthrough agents and
  replies "point the app at the gateway". The first message from a freshly
  paired phone failed with a 400. `routeAgents` now starts the bridge only when
  a *spawning* adapter exists, and the console line and the routing are decided
  together so they cannot disagree.
- The bridge's built-in `defaultAgent` was still `"claude"`, which no longer
  exists. A request without a `model` field named an agent the app never
  offered.
- `extractTunnelUrl` matched the *prefix* of a longer host, so
  `https://x.trycloudflare.com.evil.test` yielded `https://x.trycloudflare.com`.
  It now requires a host boundary.
- `bin/cli.js` had two `module.exports` statements; the second silently
  discarded the first. Found by the new lint rule.
- `packages/pairing-cli/.gitignore` ignored `package-lock.json` while CI uses
  `npm ci`, which requires one and fails without it.
- The `--help` text still advertised the removed CLIs.

### Notes
- `services/legacy-relay` remains SUPERSEDED and is not part of the release
  gate, but its suite must keep passing: a broken reference implementation is a
  trap for the next reader.

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
