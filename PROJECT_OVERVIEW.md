# Hermes Android Bridge - Project Overview

## 🎯 Mục tiêu

Xây dựng hệ thống bridge cho phép **điện thoại kết nối vào Hermes Agent chạy trên PC**, bao gồm:

1. **Android App**: Client nhận command từ server, thực thi actions (click, type, swipe...)
2. **Python Relay Server**: Plugin cho Hermes Agent, nhận command và relay về điện thoại
3. **WebSocket Connection**: Kết nối real-time, hai chiều giữa phone ↔ server

## 📋 Kiến trúc tổng thể

```
┌─────────────────────────────────────────────────────────────┐
│                    HERMES AGENT SERVER (PC)                 │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Dashboard / Gateway (Port 9119)                     │  │
│  └──────────────────────────────────────────────────────┘  │
│                            ▲                                │
│                            │ REST API + WebSocket           │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Python Plugin (Relay Server on Port 8765/8767)      │  │
│  │  • Receives tool calls from agent                    │  │
│  │  • Serializes commands                               │  │
│  │  • Sends via WebSocket to mobile app                 │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ WebSocket (Encrypted WSS)
                            │
┌─────────────────────────────────────────────────────────────┐
│                   ANDROID MOBILE DEVICE                      │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Android App (Bridge Client)                         │  │
│  │  • Connects to relay server                          │  │
│  │  • Authenticates with pairing code                   │  │
│  │  • Receives commands                                 │  │
│  │  • Executes accessibility actions                   │  │
│  │  • Returns results                                   │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## 🏗️ Các thành phần chính

### 1. Android App Component
- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Connection**: OkHttp WebSocket
- **Permissions**: Accessibility Service, Foreground Service
- **Actions Supported**:
  - Tap/click at coordinates
  - Type text input
  - Swipe gestures
  - Get screen screenshot
  - Read UI tree structure
  - Launch apps
  - Press hardware keys

### 2. Python Relay Plugin
- **Runtime**: Python 3.11+
- **Server**: aiohttp (async HTTP + WebSocket)
- **Authentication**: Pairing code-based with rate limiting
- **Tools Registered**:
  - `android_pair_device` - Register new device
  - `android_execute_tap` - Tap at coordinates
  - `android_type_text` - Send text input
  - `android_swipe_gesture` - Perform swipe
  - `android_get_screenshot` - Capture screen
  - `android_read_ui_tree` - Access UI hierarchy
  - `android_launch_app` - Open application
  - `android_press_key` - Hardware key events

### 3. Communication Protocol

#### Message Format (JSON over WebSocket)

**Client → Server:**
```json
{
  "type": "auth.request",
  "pairing_code": "ABC123"
}

{
  "type": "command.complete",
  "request_id": "req_12345",
  "success": true,
  "result": {...}
}

{
  "type": "command.error",
  "request_id": "req_12345",
  "error": "Action failed: reason"
}
```

**Server → Client:**
```json
{
  "type": "command.execute",
  "request_id": "req_12345",
  "action": "tap",
  "params": {"x": 500, "y": 300}
}

{
  "type": "auth.grant",
  "session_token": "abc...xyz",
  "permissions": ["tap", "type", "swipe", "screenshot"]
}
```

## 🚀 Implementation Plan

### Phase 1: Foundation (Week 1)
- ✅ Setup project structure
- ✅ Implement basic WebSocket communication
- ✅ Create Android auth flow
- ✅ Build simple tap action

### Phase 2: Core Features (Week 2)
- ⏳ Implement all accessibility actions
- ⏳ Add security & authentication
- ⏳ Build pairing mechanism
- ⏳ Test with Hermes Agent tools

### Phase 3: Polish & Release (Week 3)
- ⏳ Improve error handling
- ⏳ Add logging & diagnostics
- ⏳ Performance optimization
- ⏳ Documentation & release

## 📦 Dependencies

### Android Side
```kotlin
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
}
```

### Python Side
```python
requirements.txt:
aiohttp>=3.9.0
pycryptodome>=3.19.0
numpy>=1.24.0
opencv-python>=4.8.0
pillow>=10.0.0
pyautogui>=0.9.54
```

## 🔒 Security Considerations

- **Pairing Code**: Random 6-character alphanumeric, rate-limited
- **Encryption**: WSS (WebSocket Secure) when available
- **Session Tokens**: Short-lived JWT tokens
- **Permission Scoping**: Explicit consent for each action type
- **No Credentials Storage**: Only ephemeral session state

## 📝 Next Steps

Bắt đầu implement theo phases. Xem chi tiết các file trong từng phase để track progress!
