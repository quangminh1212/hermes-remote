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
# Android — 103 unit tests (JVM only, no emulator)
cd apps/android && ./gradlew testDebugUnitTest

# Android lint (resources, manifest, locale parity)
cd apps/android && ./gradlew lintDebug

# Pairing CLI — 154 tests (7 skipped by default; see below)
cd packages/pairing-cli && npm test

# Same, with coverage thresholds enforced (80% lines/functions, 75% branches)
cd packages/pairing-cli && npm run test:coverage

# Static checks: parse errors, duplicate exports, unresolvable requires
cd packages/pairing-cli && npm run lint

# End-to-end Assistant hand-off (needs a device)
node scripts/mock-assistant.js --all
```

The policy — what each layer owns, why some tests skip, and what is deliberately
not covered — is in **[docs/TESTING.md](docs/TESTING.md)**.

### Why some tests are skipped by default

The live cases in `quoting-e2e.test.js` spawn a real CLI, which costs tokens and
takes tens of seconds. They are gated behind `CHAIN_E2E=1` because a test that
fails when a model chooses different wording trains people to ignore failures.
The deterministic assertions for the same behaviour live in the unit suites and
always run.

`tunnel.test.js` skips its process-lifecycle cases on Windows: `startTunnel`
spawns its argument directly, and Windows cannot spawn a `.cmd` without a shell
(Node raises `EINVAL`), nor build a spawnable stand-in without a compiler. The
URL-extraction cases, which are the part that decides whether the tunnel came
up, run on every platform.

### Live probes

These drive the real stack. Run them when changing anything that shells out.

```bash
cd packages/pairing-cli

# Prompt round-trips: shell metacharacters, Unicode, blank input
node test/probe-cli-edge.js hermes

# The full HTTP chain: auth, /v1/models, SSE, errors, real answers
node test/probe-bridge-live.js hermes

# The three live-CLI tests inside the quoting suite
CHAIN_E2E=1 node --test test/quoting-e2e.test.js

# Bridge + pairing page WITHOUT the Hermes gateway.
# Useful when you only touched the phone app: the gateway is a separate product
# and refuses to start without provider keys in ~/.hermes/.env.
node test/harness-bridge-only.js --port 8899 --agent hermes
# then read the payload the phone would receive from the QR code:
#   curl http://127.0.0.1:8899/api/connect
```

### Agent support

The bridge serves exactly one agent: **`hermes`**, the Hermes gateway itself.
Earlier versions also fanned out to `claude`, `codex`, `grok` and `opencode`.

That was removed deliberately. Each spawned CLI brought its own flags, its own
failure modes and its own quoting rules, on a phone where the user cannot see
the child process or its stderr — surface area without a user. Adding one back
is cheap if that changes: the adapter contract in `lib/agent-registry.js` is
unchanged, and `routeAgents` in `bin/cli.js` already handles the case where a
spawning adapter and a passthrough coexist.

One consequence worth knowing: because `hermes` is a *passthrough*, the bridge
cannot answer chat for it — it tells you to point at the gateway. So the CLI
starts the bridge only when a spawning adapter exists. With only `hermes`
installed, the QR points the phone straight at the gateway. Before that rule
existed, the CLI advertised a bridge the phone's first message could not use.

### Quoting on Windows

A globally-installed npm CLI on Windows is a `.cmd` shim, which Node can only run
with `shell: true` — and then cmd.exe gets to reinterpret the prompt. The
registry therefore peels the shim to the real `.exe` behind it
(`resolveShimTarget`) so the prompt is passed with no shell at all, and only
falls back to `quoteForCmd` when that is not possible.

This matters more than it looks: the previous `quoteForCmd` doubled every `%` and
escaped every `!`, so a prompt of "100% done, 50! left" reached the model as
"100%% done, 50^^! left". `test/quoting-e2e.test.js` pins both paths by running a
prompt through a real shim and comparing the argv the child received.

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
