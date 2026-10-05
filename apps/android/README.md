# Hermes Remote — Android client

Android client for the Hermes Agent on your PC. Pairs by scanning a QR code
(from the `hermes-remote` CLI), then chats with the agent over HTTP + SSE and —
optionally — by voice.

- **Application ID:** `io.hermes.remote`
- **Min SDK:** 26 (Android 8.0) · **Target SDK:** 34

## Features

- **QR pairing** — scan the code from the `hermes-remote` page; the app fills in
  server URL, API key, profile and model, then connects.
- **Streaming chat** — prompts are sent to `/v1/chat/completions` and the reply
  streams back token by token.
- **Voice assistant** — tap the 🎙 mic to talk (system speech-to-text, replies
  read aloud with text-to-speech). Optionally enable the always-listening
  **"Hey Hermes"** wake word (Picovoice Porcupine — needs a free AccessKey).
- **Google Assistant (App Actions)** — "Hey Google, ask Hermes …" opens the app
  with the question pre-filled and sends it (`res/xml/shortcuts.xml`). See
  [`scripts/mock-assistant.js`](../../scripts/mock-assistant.js) for how this is
  tested without a Play-enabled device.
- **Voice webhook** — the `hermes-remote` CLI can expose a webhook so an IFTTT
  applet / Google Home routine can ask Hermes a question (see its README).
- **Self-update** — checks GitHub Releases and offers to download + install a
  newer APK.

## Architecture

```
app/src/main/java/io/hermes/remote/
├── HermesRemoteApplication.kt     Application entry point
├── data/
│   ├── ChatApi.kt                 HTTP + SSE client for /v1/chat/completions
│   ├── ConfigStore.kt             SharedPreferences: server config + voice opts
│   └── SseParser.kt               Incremental SSE "data:" line parser
├── model/                         ChatMessage, ServerConfig, ConnectPayload
├── voice/
│   ├── SpeechRecognizerManager.kt System speech-to-text wrapper
│   ├── TtsSpeaker.kt              System text-to-speech (+ markdown sanitizer)
│   ├── WakeWordDetector.kt        Porcupine "Hey Hermes" (optional, graceful)
│   ├── VoiceAssistantService.kt   Foreground service for the wake word
│   ├── AndroidVoiceController.kt  Concrete VoiceController for the ViewModel
│   ├── AssistantIntent.kt         Parses the Google Assistant hand-off intent
│   ├── VoiceBus.kt                Service → UI signal (wake events)
│   └── BootReceiver.kt            Re-arms the assistant after reboot
├── updater/                       GitHub Releases self-update
└── ui/
    ├── MainActivity.kt            Compose chat screen + App Action handling
    ├── MainViewModel.kt           Chat + voice state, exposes VoiceController
    ├── QrScanActivity.kt          QR scanner for pairing
    └── theme/XLabTheme.kt         Shared design tokens (XLab palette)
```

The ViewModel talks to voice through a small `VoiceController` interface so it
stays free of Android framework classes and unit-testable on the JVM.

## Build

```bash
cd apps/android
./gradlew assembleDebug      # Linux/macOS
gradlew.bat assembleDebug    # Windows
```

Requires JDK 17 and Android SDK 34. Create `local.properties` from
`local.properties.template` and point `sdk.dir` at your Android SDK:

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

Release builds read `keystore.properties` (kept out of git); if it is missing
the release falls back to unsigned.

## Test

```bash
./gradlew testDebugUnitTest   # JVM unit tests
```

Unit tests cover the SSE parser, HTTP client (MockWebServer), config/URL
building, the update checker, and the TTS markdown sanitizer. Hardware paths
(mic, TTS, camera, wake word) are verified on an emulator/device.

## Usage

1. On the PC run `npx hermes-remote` and open the QR page.
2. In the app tap **Quét QR** and scan it — the app configures and connects.
3. Type a message, tap a quick-action chip, or tap the 🎙 **mic** to talk.
4. To use the wake word: open ⚙ settings → enter your Picovoice AccessKey →
   enable **Nghe "Hey Hermes"**. Put a `hey_hermes.ppn` keyword in `assets/`
   (Porcupine) — without a key or keyword the app stays in tap-to-talk mode.

### Google Assistant (App Actions)

The app registers the `com.hermes.bridge.action.ASK` action (see
`res/xml/shortcuts.xml`). App Actions are validated through the Google
Assistant plugin / Play Console and need Google Play Services; they cannot be
fully exercised on a bare AOSP emulator. The intent contract is testable with:

```bash
adb shell am start -n com.hermes.bridge.debug/com.hermes.bridge.ui.MainActivity \
  -a com.hermes.bridge.action.ASK --es query "trạng thái git"
```

## Permissions

`INTERNET`, `ACCESS_NETWORK_STATE`, `CAMERA` (QR), `REQUEST_INSTALL_PACKAGES`
(self-update), and for voice: `RECORD_AUDIO`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`.
The microphone is only used when the user taps the mic or explicitly enables the
wake-word service (which shows a persistent notification).
