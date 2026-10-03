# Hermes Android Bridge 🚀

**Kết nối điện thoại ANDROID với HERMES AGENT chạy trên PC**

---

## 📋 Giới thiệu

Hermes Android Bridge là hệ thống bridge cho phép **điều khiển điện thoại Android từ Hermes Agent chạy trên máy tính**. 

### 🔥 Tính năng chính:
- ✅ **Real-time control**: Nhận và thực thi lệnh ngay lập tức qua WebSocket
- ✅ **Accessibility integration**: Sử dụng Accessibility Service để tương tác UI
- ✅ **Cross-platform**: Python server + Kotlin Android app
- ✅ **Secure pairing**: Authentication code-based
- ✅ **Multiple actions**: Tap, type text, swipe, launch apps...
- ✅ **Live screenshot**: Capture màn hình real-time
- ✅ **UI tree reading**: Đọc cấu trúc accessibility hierarchy

---

## 🏗️ Kiến trúc tổng thể

```
┌─────────────────────────────────────────────────────────────┐
│                    HERMES AGENT SERVER (PC)                 │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Dashboard / Gateway (Port 9119)                     │  │
│  └──────────────────────────────────────────────────────┘  │
│                            ▲                                │
│                            │ REST API + WebSocket           │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Python Plugin (Relay Server on Port 8765)           │  │
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
│  │  • Executes accessibility actions                   │  │
│  │  • Returns results                                   │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## 📦 Cài đặt

### Yêu cầu hệ thống:
- **PC/Server**: 
  - Python 3.11+
  - Kết nối mạng internet
- **Android**:
  - Android 8.0+ (API 26+)
  - RAM: 2GB minimum
  - Permissions: Accessibility Service

---

## 🚀 Quick Start

### Bước 1: Setup Python Relay Server

```bash
# 1. Tạo virtual environment
cd C:\Dev\Hermes_Android\python-relay-server
python -m venv venv

# 2. Activate venv
venv\Scripts\activate  # Windows
# source venv/bin/activate  # Linux/Mac

# 3. Install dependencies
pip install -r requirements.txt

# 4. Start relay server
python relay_server.py --host 0.0.0.0 --port 8765
```

Server sẽ chạy tại: `ws://localhost:8765/ws`

### Bước 2: Build & Install Android App

#### Option A: Build từ source
```bash
cd C:\Dev\Hermes_Android\android-app

# Mở trong Android Studio
studio .

# Or build from command line:
./gradlew assembleDebug

# Install to device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

#### Option B: Download APK (nếu đã build)
- Truy cập releases page
- Download `.apk` file
- Install trực tiếp trên thiết bị

### Bước 3: Setup trên Điện thoại

1. **Mở app Hermes Bridge**

2. **Enable Accessibility Service**:
   - Settings → Accessibility → Installed accessibility services
   - Tìm "Hermes Bridge" và enable
   - Confirm permissions

3. **Configure Connection**:
   ```
   Server URL: ws://YOUR_PC_IP:8765/ws
   Example: ws://192.168.1.100:8765/ws
   ```

4. **Start Connection**:
   - Tap "Start Connection"
   - Wait for authentication

### Bước 4: Test từ Hermes Agent

Trên PC (chạy Hermes Agent), gọi tools:

```python
# List available tools first
hermes.tools.list()

# Pair new device
result = hermes.tools.call(
    "android_pair_device",
    display_name="My Android Phone"
)

print(f"Pairing Code: {result['pairing_code']}")
# Output: Pairing Code: ABC123

# Enter this code vào Android app

# Then test commands:
result = hermes.tools.call(
    "android_execute_tap",
    x=500, y=300  # Tap at coordinates
)

result = hermes.tools.call(
    "android_type_text",
    text="Hello from Hermes!"
)

result = hermes.tools.call(
    "android_swipe_gesture",
    start_x=500, start_y=1000,  # Swipe down
    end_x=500, end_y=200
)

result = hermes.tools.call(
    "android_get_screenshot"
)

result = hermes.tools.call(
    "android_read_ui_tree",
    depth_limit=10
)

result = hermes.tools.call(
    "android_launch_app",
    package_name="com.android.chrome"  # Launch Chrome
)
```

---

## 🛠️ Available Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `android_pair_device` | Register new Android device | `display_name` (required) |
| `android_execute_tap` | Tap at screen coordinates | `x`, `y`, `duration_ms` |
| `android_type_text` | Type text into input field | `text`, `device_id` |
| `android_swipe_gesture` | Perform swipe gesture | `start_x`, `start_y`, `end_x`, `end_y`, `duration_ms` |
| `android_get_screenshot` | Capture current screen | `device_id` (optional) |
| `android_read_ui_tree` | Read accessibility hierarchy | `device_id`, `depth_limit` |
| `android_launch_app` | Launch application | `package_name`, `device_id` |

---

## 📝 Detailed Usage Examples

### 1. Pair Multiple Devices
```python
# Device 1 - Main Phone
phone1 = hermes.tools.call("android_pair_device", display_name="Main Phone")

# Device 2 - Tablet
tablet = hermes.tools.call("android_pair_device", display_name="Tablet")
```

### 2. Navigate UI
```python
# Tap search button
hermes.tools.call("android_execute_tap", x=100, y=50)

# Scroll down
hermes.tools.call("android_swipe_gesture", 
                  start_x=500, start_y=1000, 
                  end_x=500, end_y=200)

# Back navigation
hermes.tools.call("android_press_key", key_code="BACK")
```

### 3. Interactive Automation
```python
def search_and_click(keyword):
    # Open app
    hermes.tools.call("android_launch_app", package_name="com.google.android.googlequicksearchbox")
    
    # Tap search bar
    hermes.tools.call("android_execute_tap", x=300, y=100)
    
    # Type keyword
    hermes.tools.call("android_type_text", text=keyword)
    
    # Press enter
    hermes.tools.call("android_execute_tap", x=500, y=50)
```

### 4. Screenshot Analysis Workflow
```python
# Get current screen
screenshot = hermes.tools.call("android_get_screenshot")

# Process image (Python side)
import cv2
import numpy as np

img_data = base64.b64decode(screenshot['screenshot_base64'])
img_array = np.frombuffer(img_data, dtype=np.uint8).reshape(...)

# Find element location using computer vision
element_coords = find_element_in_image(img_array, target_icon)

# Click if found
if element_coords:
    hermes.tools.call("android_execute_tap", 
                      x=element_coords[0], y=element_coords[1])
```

---

## 🔒 Security Features

- **Pairing Code**: Random 6-character alphanumeric (e.g., "ABC123")
- **Rate Limiting**: Max 3 pairing attempts per minute
- **Session Tokens**: Short-lived JWT tokens (1 hour expiry)
- **Permission Scoping**: Explicit consent required
- **No Credential Storage**: Only ephemeral session state

---

## 🐛 Troubleshooting

### "Connection failed"
✅ Solutions:
1. Check server is running: `curl http://localhost:8765/`
2. Verify firewall allows port 8765
3. Ensure phone and PC are on same network
4. Try `wss://` instead of `ws://` if SSL enabled

### "Accessibility permission denied"
✅ Solutions:
1. Go to Settings → Accessibility
2. Enable "Hermes Bridge" service
3. Grant all requested permissions

### "Command timeout"
✅ Solutions:
1. Check WebSocket connection status
2. Verify pairing code is correct
3. Restart both server and app
4. Check device battery optimization settings

### "Tap doesn't work"
✅ Solutions:
1. Ensure Accessibility Service has "canPerformGestures" flag
2. Check coordinate system (screen dimensions may vary)
3. Try increasing duration_ms parameter

---

## 🧪 Development & Testing

### Run tests
```bash
# Python tests
pytest tests/ -v

# Android instrumentation tests
./gradlew :app:testDebugUnitTest
./gradlew :app connectedAndroidTest
```

### Debug logs
```bash
# Android logcat
adb logcat | grep -E "WebSocket|Accessibility|Bridge"

# Python server logs
python relay_server.py --debug
```

---

## 📚 Files Structure

```
C:\Dev\Hermes_Android/
├── android-app/
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/hermes/bridge/
│   │   │   │   ├── service/
│   │   │   │   │   ├── AccessibilityBridgeService.kt
│   │   │   │   │   └── WebSocketConnectionService.kt
│   │   │   │   ├── ui/
│   │   │   │   │   ├── MainActivity.kt
│   │   │   │   │   └── MainViewModel.kt
│   │   │   │   └── websocket/
│   │   │   │       └── WebSocketClient.kt
│   │   │   ├── res/
│   │   │   │   └── xml/accessibility_service_config.xml
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle.kts
│   └── build.gradle.kts
│
├── python-relay-server/
│   ├── relay_server.py
│   └── requirements.txt
│
├── PROJECT_OVERVIEW.md
└── README.md
```

---

## 🤝 Contributing

Contributions welcome! Here's how you can help:

1. **Improve accessibility actions** - Add more gesture types
2. **Enhance security** - Better encryption, authentication methods
3. **Add features** - Voice commands, automation flows
4. **Fix bugs** - Report and fix issues
5. **Improve docs** - Clearer instructions, examples

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

## 📄 License

MIT License - see LICENSE file for details

---

## 🙏 Acknowledgments

- Built for [Hermes Agent](https://github.com/NousResearch/hermes-agent)
- Inspired by accessibility automation projects
- Uses OkHttp, aiohttp libraries

---

## 📞 Support

- GitHub Issues: https://github.com/YOUR_USERNAME/hermes-android-bridge/issues
- Email: support@hermes-bridge.com
- Discord: Join our community server

---

**Made with ❤️ for the open-source community**

*Last updated: January 2025*
