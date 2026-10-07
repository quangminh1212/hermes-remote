# Test Plan — Hermes Remote

How this project is tested, what each layer is responsible for, what is
deliberately not tested, and how to run it all.

The goal is not "high coverage". It is that a change which breaks something a
user would notice fails here first, and that a failure says which behaviour
broke rather than only which assertion tripped.

---

## 1. Components and their test layers

| Component | Language | Fast suite | Slow / opt-in | CI |
|---|---|---|---|---|
| `apps/android` | Kotlin | JVM unit tests (no emulator) | Instrumented on device | `android-ci.yml` |
| `packages/pairing-cli` | Node | `node --test` | Live-CLI probing | `pairing-cli-ci.yml` |
| `services/legacy-relay` | Python | `pytest` | Live device control | not wired (superseded) |

`services/legacy-relay` is labelled SUPERSEDED in its own README and nothing
active depends on it. Its tests must still pass, because a broken reference
implementation is a trap for the next reader, but it is not part of the release
gate.

---

## 2. What each layer owns

### 2.1 Android unit tests — `npm`-free, JVM only

Run with:

```powershell
cd apps/android
./gradlew testDebugUnitTest
```

No emulator, no device, no network. Fast enough to run on every save.

| Suite | Owns |
|---|---|
| `ServerConfigTest` | Endpoint construction, agent retargeting, the single-agent rule |
| `ConnectPayloadTest` | QR payload → config, including a payload predating the agent fields |
| `ChatApiTest` / `ChatApiStreamTest` / `SseParserTest` | Request shaping, SSE framing, partial-chunk handling |
| `ThemeModeTest` | `SYSTEM`/`DARK`/`LIGHT` resolution and persistence round-trip |
| `PaletteTest` | WCAG contrast on both palettes; neutrality of the dark ramp |
| `AssistantIntentTest` | The Google Assistant "ask Hermes" intent contract |
| `TtsSpeakerTest` | Speech output wiring |
| `UpdateCheckerTest` | Version comparison and rate-limiting |
| `VoiceStateTest` | Voice state transitions |

### 2.2 Android lint

```powershell
cd apps/android
./gradlew lintDebug
```

Wired into `android-ci.yml`. Catches resource and manifest mistakes that unit
tests cannot see, such as a string missing from one locale.

### 2.3 `pairing-cli` unit tests — `node --test`

Run with:

```powershell
cd packages/pairing-cli
npm test              # fast suite
npm run test:coverage # same, with thresholds enforced
npm run lint          # dependency-free static checks
```

| Suite | Owns |
|---|---|
| `agent-registry.test.js` | Adapter registry shape; that removed CLI ids are unknown; `resolveBin`, `quoteForCmd`, `stripAnsi` |
| `agent-bridge.test.js` | HTTP surface: auth, `/v1/models`, `/v1/chat/completions`, SSE framing, error mapping |
| `route-agents.test.js` | Whether to start the bridge at all, and where the QR points |
| `config.test.js` | `.env` discovery and parsing |
| `gateway.test.js` | Health probing against a real socket, including timeout and refusal |
| `tunnel.test.js` | Public-URL extraction and process lifecycle |
| `i18n.test.js` / `language.test.js` | Key parity across `en`/`vi`/`zh`; no placeholder leaks |
| `pairing-page.test.js` | The rendered HTML the phone's browser shows |
| `pack.test.js` | `parseArgs`, `parseEnv`, tunnel naming, page serving |
| `quoting-e2e.test.js` | Prompt integrity through the real spawn path |

### 2.4 Live / opt-in tests

These are skipped unless explicitly enabled, because each spawns a real model or
a real process and can take minutes.

| Suite | Gate | What it proves |
|---|---|---|
| `quoting-e2e.test.js` (live cases) | `CHAIN_E2E=1` | A real CLI receives the prompt byte-for-byte, including `%` and `!` |
| `test/probe-bridge-live.js` | manual | The bridge answers over HTTP as the app expects |
| `test/probe-cli-edge.js` | manual | Hostile prompt content cannot break out |
| `test/probe-default-model.js` | manual | A stale model id retargets instead of 404ing |

Rationale for the gate: a test that fails because a model chose different
wording trains people to ignore failures. The deterministic assertions live in
the unit suites; these add confidence on demand.

---

## 3. Coverage policy

Thresholds are enforced by the `test:coverage` script, so CI and a developer's
machine cannot diverge:

| Metric | Threshold |
|---|---|
| Lines | 80% |
| Functions | 80% |
| Branches | 75% |

Coverage is a floor, not a target. Two rules matter more than the number:

1. **A new branch needs a test that fails without it.** Every regression guard
   added for a real bug in this repo was verified by reintroducing the bug and
   watching the test fail. If it does not fail, it is not a guard.
2. **Excluded code needs a reason.** `test/**` is excluded from the measurement;
   anything else excluded should say why in the script itself.

Files with low coverage and an explicit reason to stay that way:

| File | Why |
|---|---|
| `bin/cli.js` | `main()` orchestrates real processes, a real tunnel and a real gateway. The decisions it makes are extracted into `routeAgents`, which *is* tested. |
| `tunnel.js`, `gateway.js` | The download/restart paths require network and a real `hermes` install. The URL parsing and probing logic is covered. |

---

## 4. Platform policy

`package.json` claims `win32`, `darwin` and `linux`, so CI runs all three. This
is not box-ticking: three real bugs in this codebase were Windows-only.

- A `.cmd` shim cannot be spawned without a shell (Node raises `EINVAL`).
- `quoteForCmd` corrupted `%` and `!` in prompts.
- `spawn` resolution differs between an extensionless shim, a `.ps1` and a real
  `.exe`.

Tests that cannot run on a platform say so with an explicit `skip` and a reason,
rather than being silently absent. `tunnel.test.js` skips its process-lifecycle
cases on Windows because there is no way to create a spawnable stand-in without
a compiler — the pure URL-extraction cases still run everywhere.

---

## 5. What is deliberately not tested

Honesty about gaps is more useful than a coverage number that hides them.

| Not tested | Why |
|---|---|
| Real LLM output | Non-deterministic. The contract (HTTP shape, SSE framing, error mapping) is tested instead. |
| The cloudflared download | Requires network and a 30 MB binary. `extractTunnelUrl` and `platformAsset` are covered. |
| Emulator UI rendering | Screenshots on a physical device are the evidence; the values driving them are unit-tested. |
| `services/legacy-relay` live control | Needs a real phone with a legacy bridge. |

---

## 6. Manual release checklist

Automated tests cannot cover the parts a human has to look at.

1. `npm test` and `npm run lint` in `packages/pairing-cli` — all green.
2. `./gradlew testDebugUnitTest lintDebug` in `apps/android` — all green.
3. Install the debug APK on a physical device.
4. Confirm both themes: Settings → Appearance → Dark, then Light, then System.
5. Pair with the real `npx hermes-remote`, scan the QR, send a message that
   contains `%` and `!` and confirm the reply preserves both.
6. Confirm the status line names the agent and the transcript is legible in
   direct sunlight on the light theme.

Steps 3–6 are the only place the actual product is observed; everything above
them exists to make those steps short.
