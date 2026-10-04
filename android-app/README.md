# 📱 Hermes Android Bridge App

Ứng dụng Android kết nối với Hermé Agent Server trên PC qua WebSocket, cung cấp khả năng điều khiển device Android từ xa thông qua Accessibility Service.

## ✨ Tính năng chính

### 1. **Kết nối WebSocket bền vững**
- Duy trì kết nối liên tục với Python relay server
- Tự động reconnect khi mất kết nối
- Authentication bằng pairing code

### 2. **Accessibility Bridge**
- Thực hiện các thao tác touch/swipe ở vị trí cụ thể
- Nhập văn bản vào input field
- Đọc UI tree của màn hình
- Chụp screenshot (base64 encoded)
- Phím cứng (Home, Back, Recent Apps, Volume)
- Điều hướng cuộn trang
- Khởi chạy ứng dụng khác

### 3. **Foreground Service**
- Dịch vụ chạy nền để duy trì kết nối
- Notification hiển thị trạng thái
- Tự động restart khi cần thiết

## 🏗️ Kiến trúc

```
MainActivity
    ├── MainViewModel (State management)
    └── HomeScreen UI
    
WebSocketConnectionService (Foreground Service)
    └── WebSocketClient
        └── Handles WebSocket connection
        
AccessibilityBridgeService
    └── Executes commands received from server
        ├── Tap at coordinates
        ├── Type text
        ├── Swipe gestures
        ├── Read UI tree
        ├── Get screenshot
        ├── Device controls
```

## 🛠️ Build & Setup

### Yêu cầu
- **Android Studio Arctic Fox hoặc mới hơn**
- **JDK 17+**
- **Android SDK API 34** (compileSdk), minSdk 26

### Các bước setup

#### 1. Clone repository
```bash
cd android-app
```

#### 2. Mở trong Android Studio
- File → Open → Chọn thư mục `android-app`
- Đợi Gradle sync hoàn tất

#### 3. Build Debug
```bash
./gradlew assembleDebug
```

hoặc trong Android Studio:
- Build → Make Project
- Build → Build Bundle(s) / APK(s) → Build APK(s)

#### 4. Deploy lên emulator/device
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 🔧 Configuration

### Server URL
Mặc định: `ws://localhost:8765/ws`

Để kết nối với server khác, thay đổi URL trong app UI hoặc edit constant:

```kotlin
// In WebSocketConnectionService.kt
val defaultUrl = "ws://your-server-ip:8765/ws"
```

### Pairing Code
Được sinh ra bởi Python server và truyền đến Android thông qua:
1. Gọi function `android_pair_device(display_name)` từ Hermes Agent tools
2. Hiển thị pairing code trên UI Android
3. Xác thực khi start connection

## 🚀 Sử dụng

### Trên Android App
1. **Enable Accessibility Service**:
   - Click "Enable Accessibility Service" button
   - Đi đến Settings → Accessibility
   - Bật toggle cho "Hermes Bridge"
   - Confirm prompt

2. **Configure Connection**:
   - Nhập WebSocket URL (nếu không dùng localhost)
   - Nếu có pairing code, nhập vào trường Pairing Code

3. **Start Connection**:
   - Click "Start Connection"
   - Wait for notification "Hermes Bridge Connected"
   - Check status indicator (đỏ/vàng/xanh)

### Trên Python Relay Server
```python
# Example in Python
from hermes_relay_server import HermesServer

server = HermesServer()

# Register a device
device_id = server.android_pair_device("My Phone")
print(f"Pairing code: {device_id}")

# Send commands to device
await server.android_execute_command(device_id, {
    "action": "tap",
    "params": {"x": 500, "y": 800}
})
```

## 📝 Commands Supported

### Input Actions
- **tap**: `{x, y, duration_ms}`
- **type_text**: `{text: String}`
- **swipe**: `{start_x, start_y, end_x, end_y, duration_ms}`

### UI Operations
- **get_screenshot**: Returns base64 PNG
- **read_ui_tree**: Returns JSON tree structure `{depth_limit: Int}`
- **launch_app**: `{package_name: String}`

### System Controls
- **press_key**: Hardware key code
- **press_home**, **press_back**, **press_recent_apps**
- **press_volume_up**, **press_volume_down**, **press_mute**
- **scroll**: "up", "down", "left", "right"
- **clear_clipboard**
- **get_device_info**: Returns device specifications

## 🔒 Permissions Required

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

## 🐛 Troubleshooting

### Connection issues
1. Ensure Python relay server is running on port 8765
2. Check firewall settings allow WebSocket connections
3. Verify pairing code matches between server and device

### Accessibility Service not working
1. Go to Settings → Accessibility
2. Find "Hermes Bridge" in list
3. Enable the toggle
4. Restart app if needed

### Screenshot returns null
The current implementation creates an empty bitmap. To capture actual screen content:
1. Add `android.permission.CAPTURE_VIDEO_OUTPUT` permission
2. Use MediaProjection API with user consent
3. Or use ADB shell: `adb exec-out screencap -p`

### Auto-reconnect fails
Check network stability and ensure server supports persistent WebSocket connections with ping/pong heartbeats.

## 📦 Dependencies

```kotlin
// Compose
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")

// Network
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// Serialization
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
```

## 🔄 Next Steps

- [ ] Implement proper MediaProjection screenshot capture
- [ ] Add keyboard overlay for easier text input
- [ ] Support multi-touch gestures
- [ ] Add gesture recording/replay feature
- [ ] Implement command queuing and rate limiting
- [ ] Add diagnostic logging dashboard

## 📄 License

MIT License - feel free to use and modify as needed.

## 👥 Contributing

Contributions welcome! Please submit PRs or report issues via GitHub.

---

**Developer Notes**: This app requires root access or special permissions for full functionality. Some features like screenshot capture may require additional permissions or system-level access.
