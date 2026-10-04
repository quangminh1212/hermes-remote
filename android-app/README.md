# Hermes Bridge — Android

Android client for the Hermes Agent. Connects to the Python relay server over
WebSocket and executes device interaction commands (tap, swipe, text input,
UI-tree reading, screenshots) through a real `AccessibilityService`.

## Architecture

```
app/src/main/java/com/hermes/bridge/
├── HermesBridgeApplication.kt     Application entry point
├── protocol/                      Wire protocol (shared with the Python server)
│   ├── MessageType.kt             Message + action name constants
│   └── CommandRequest.kt          Inbound command DTO + typed param accessors
├── service/
│   ├── AccessibilityBridgeService.kt   Real gestures via dispatchGesture,
│   │                                   text via ACTION_SET_TEXT, screenshot,
│   │                                   UI tree, app launch
│   ├── CommandExecutor.kt              Maps protocol actions -> service calls
│   └── WebSocketConnectionService.kt   Foreground service holding the socket
├── websocket/
│   ├── WebSocketClient.kt         OkHttp WS, auth, reconnect w/ backoff
│   └── ConnectionManager.kt       Process-wide singleton + observable state
└── ui/
    ├── MainActivity.kt            Compose screen
    └── MainViewModel.kt           UI state + accessibility status
```

## Build

```bash
cd android-app
./gradlew assembleDebug      # Linux/macOS
gradlew.bat assembleDebug    # Windows
```

Requires JDK 17 and Android SDK 34. Create `local.properties` from
`local.properties.template` and point `sdk.dir` at your Android SDK:

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

## Test

```bash
./gradlew test                   # unit tests
./gradlew connectedAndroidTest   # instrumented (device/emulator)
```

## Usage

1. Install the app, open it.
2. Tap **Enable Accessibility Service** and enable *Hermes Bridge*.
3. (Recommended) Tap **Disable Battery Optimization**.
4. Enter the relay server WebSocket URL and the pairing code.
5. Tap **Start**. Status turns green when connected.

Screenshots require Android 11 (API 30) or newer.

## Protocol

See `protocol/` and the server's `relay_server.py`. Device→server frames are
`auth.request`, `command.complete`, `command.error`; server→device frames are
`auth.grant`, `auth.reject`, `command.execute`.
