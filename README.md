# Hermes Remote

Talk to your Hermes agent on your PC — from your phone, or by voice through
Google Assistant.

The PC holds the brain. The phone is a thin, QR-paired client that sends your
prompt and streams the reply back, optionally reading it aloud.

```
  "Hey Google, ask Hermes …"
        │
        ▼
  ┌──────────────┐   HTTP/SSE   ┌────────────────────┐
  │ Android app  │ ───────────► │  Hermes on your PC │
  │              │ ◄─────────── │   gateway :8642    │
  └──────────────┘   streaming  └────────────────────┘
```

## Repository layout

| Path | What it is |
|---|---|
| `apps/android/` | The Android client (Kotlin, Jetpack Compose) |
| `packages/pairing-cli/` | `npx hermes-remote` — starts the tunnel, serves the QR page |
| `services/legacy-relay/` | The original Python WebSocket relay. **Superseded** — kept for reference only |
| `scripts/` | Developer tooling, including the mock Google Assistant harness |
| `docs/` | Handoff notes, contributing guide, changelog, security |

## Quick start

### 1. On the PC — pair the phone

```bash
npx hermes-remote
```

This finds your Hermes `.env`, ensures the gateway is running, opens a
Cloudflare quick tunnel, and shows a QR page in your browser.

Flags: `--lan` (skip the tunnel), `--port <n>`, `--profile <name>`,
`--agent <id>` (answer through a local CLI: claude, codex, Anthropic, opencode),
`--no-agents` (off), `--lang <en|vi|zh>`, `--no-open`, `-h`.

When an agent is selected the QR page and the phone's connect payload point at a
sidecar "agent bridge" instead of the Hermes gateway, and carry the list of
agents so the phone can switch between them from the model field.

### 2. On the phone

Install the APK, tap **Quét QR**, point the camera at the page. The server URL
and key are filled in automatically.

Build it yourself:

```bash
cd apps/android
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

### 3. Talk to it

- **In the app** — tap the mic and speak, or type.
- **Through Google Assistant** — "Hey Google, ask Hermes \<anything\>".

## Google Assistant integration

Google retired third-party Conversational Actions in 2023, so an app can never
*be* "Hey Google". It can be **reached by** the Assistant, which is what the App
Actions capability in
`apps/android/app/src/main/res/xml/shortcuts.xml` declares.

Supported phrases:

| Say | Effect |
|---|---|
| "Hey Google, **ask Hermes** \<request\>" | Sends `<request>` to the PC, speaks the reply |
| "Hey Google, **open Hermes**" | Just opens the app |

Four delivery shapes are handled (`AssistantIntent`): a flat `query` extra,
`android.intent.extra.TEXT`, a nested built-in-intent parameter bundle, and a
`hermes://ask?query=…` deep link.

### Testing it without an emulator

The Android unit tests cover the intent parsing, but the full hand-off needs a
device. `scripts/mock-assistant.js` simulates what the Assistant sends and fires
real intents at the real APK:

```bash
node scripts/mock-assistant.js --list
node scripts/mock-assistant.js --all
```

It exits non-zero on failure, so it is CI-usable. It needs an attached device or
emulator with the app installed.

### What still needs a real device

Routing the phrase *"Hey Google, ask Hermes …"* requires Google Play Services and
an App Action registered in the Play Console. An AOSP emulator has neither, so
that last hop is verified by hand on a real phone.

## Tests

```bash
# Android — 69 unit tests
cd apps/android && ./gradlew testDebugUnitTest

# Pairing CLI — 24 tests
cd packages/pairing-cli && npm test

# End-to-end Assistant hand-off (needs a device)
node scripts/mock-assistant.js --all
```

## Design

The Android app, the pairing page and the XLab web project share one design
system. Palette tokens are defined in three places and must move together:

- `apps/android/app/src/main/java/io/hermes/remote/ui/theme/XLabTheme.kt`
- `packages/pairing-cli/lib/pairing-page.js`
- `apps/android/app/src/main/res/values/colors.xml`

Brand teal `#00A19A` is the anchor. The app is dark-only by design.

## Documentation

- [HANDOFF.md](docs/HANDOFF.md) — current state, what is verified, what is left
- [SECURITY.md](docs/SECURITY.md) — where the API key lives and how to rotate it
- [CONTRIBUTING.md](docs/CONTRIBUTING.md) — commit conventions and workflow
- [CHANGELOG.md](docs/CHANGELOG.md) — release history

## Requirements

- **PC**: Node.js 18+, a working Hermes install
- **Phone**: Android 8.0 (API 26) or newer
- **Assistant**: Google Play Services, plus a registered App Action

## License

See [LICENSE](LICENSE).
