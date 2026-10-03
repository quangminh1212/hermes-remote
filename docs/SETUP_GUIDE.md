# Hermes Android Bridge - Setup & Installation Guide

## 🎯 Overview

Hermes Android Bridge là một hệ thống cho phép **điều khiển điện thoại Android từ Hermes Agent chạy trên PC**. Hệ thống bao gồm:

1. **Python Relay Server**: Plugin cho Hermes Agent, chạy trên PC (port 8765)
2. **Android App**: Client nhận và thực thi commands từ server

## 📋 Prerequisites

### For Python Server (PC):
- Python 3.11+
- pip package manager
- Network access to port 8765

### For Android App:
- Android Studio Arctic Fox (2020.3.1+) or later
- JDK 17
- Android SDK 34
- Gradle 8.14.5+

### For Testing:
- Android device with Android 8.0+ (API 26+)
- ADB installed (for debugging)
- USB debugging enabled on device

---

## 🚀 Quick Start

### Step 1: Setup Python Relay Server

```bash
# Navigate to relay server directory
cd C:\Dev\Hermes_Android\python-relay-server

# Install dependencies
pip install -r requirements.txt

# Run the server
python relay_server.py --host 0.0.0.0 --port 8765
```

Server sẽ start và listen trên `ws://0.0.0.0:8765/ws`

### Step 2: Build Android App

```bash
# Navigate to Android project
cd C:\Dev\Hermes_Android\android-app

# Open in Android Studio
# File → Open → Select android-app directory

# Build debug APK
./gradlew assembleDebug

# Or via command line (from android-app root)
.\gradlew :app:assembleDebug
```

APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

### Step 3: Install Android App

```bash
# Connect device via USB or use wireless ADB
adb devices

# Install app
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Or manually transfer APK and install from file manager
```

### Step 4: Configure Connection

1. **Open Android app** on your device
2. Enter server URL: `ws://YOUR_PC_IP:8765/ws`
   - Get your PC IP: `ipconfig` (Windows) or `ifconfig` (Linux/Mac)
3. Enable **Accessibility Service**:
   - Settings → Accessibility → Installed services
   - Toggle "Hermes Bridge" ON
4. Tap **"Start Connection"**

### Step 5: Pair Device with Hermes Agent

From Python console or Hermes dashboard, call:

```python
import requests

# Register available tools
tools_response = requests.get("http://localhost:8765/api/v1/tools/register")
print(f"Available tools: {len(tools_response.json()['tools'])}")

# Create pairing
pairing_result = requests.post(
    "http://localhost:9119/api/v1/tools/call",
    json={
        "tool": "android_pair_device",
        "arguments": {
            "display_name": "My Android Phone"
        }
    }
)

pairing_data = pairing_result.json()
print(f"Pairing code: {pairing_data['result']['pairing_code']}")

# Use this code in Android app to complete pairing!
```

---

## 🔧 Detailed Configuration

### Python Server Configuration

#### Custom Host/Port
```bash
# Bind to specific interface
python relay_server.py --host 192.168.1.100 --port 8765

# Or use default settings
python relay_server.py
```

#### Firewall Rules (Windows)
```powershell
# Allow inbound connection on port 8765
New-NetFirewallRule -DisplayName "Hermes Bridge Server" -Direction Inbound -Protocol TCP -LocalPort 8765 -Action Allow

# Allow WebSocket connections
New-NetFirewallRule -DisplayName "Hermes Bridge WebSocket" -Direction Inbound -Protocol TCP -LocalPort 8765 -Action Allow
```

#### Firewall Rules (Linux Ubuntu)
```bash
sudo ufw allow 8765/tcp
sudo ufw reload
```

### Android App Configuration

#### Permissions Required

The app requires these permissions which you'll be prompted for during first launch:

| Permission | Purpose |
|------------|---------|
| INTERNET | WebSocket communication |
| FOREGROUND_SERVICE | Persistent background connection |
| BIND_ACCESSIBILITY_SERVICE | Screen interaction & UI reading |
| POST_NOTIFICATIONS | Foreground service indicator |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Prevent sleeping |

#### Accessibility Service Setup

1. **Enable the service**:
   - Settings → Accessibility → Accessibility Services
   - Find "Hermes Bridge"
   - Toggle ON
   
2. **Grant permission**:
   - Confirm when system prompt appears
   - App may require reboot (uncommon)

#### Network Considerations

For local network testing:
- Both PC and phone must be on same WiFi
- PC firewall must allow incoming connections
- Use IP address (not localhost) in WebSocket URL

Example URLs:
- Local test: `ws://localhost:8765/ws` (requires USB tethering)
- LAN: `ws://192.168.1.100:8765/ws`
- Remote (needs reverse proxy + TLS): `wss://your-domain.com/ws`

---

## 🛠️ Available Tools & Usage

Once paired, call these tools from Hermes Agent:

### 1. android_pair_device
Register new device for control

```python
# Tool usage example
{
  "name": "android_pair_device",
  "arguments": {
    "display_name": "My iPhone Clone"
  }
}

# Response:
{
  "status": "success",
  "device_id": "abc-123-def",
  "pairing_code": "XYZ789",
  "instructions": "Open Android app and enter pairing code: XYZ789"
}
```

### 2. android_execute_tap
Tap screen at coordinates

```python
{
  "name": "android_execute_tap",
  "arguments": {
    "x": 500.5,
    "y": 300.2,
    "duration_ms": 100
  },
  "device_id": "abc-123-def"
}
```

### 3. android_type_text
Type text into active input field

```python
{
  "name": "android_type_text",
  "arguments": {
    "text": "Hello from Hermes!",
    "device_id": "abc-123-def"
  }
}
```

### 4. android_swipe_gesture
Swipe between two points

```python
{
  "name": "android_swipe_gesture",
  "arguments": {
    "start_x": 100,
    "start_y": 500,
    "end_x": 100,
    "end_y": 200,
    "duration_ms": 300
  }
}
```

### 5. android_get_screenshot
Capture current screen

```python
{
  "name": "android_get_screenshot",
  "arguments": {
    "device_id": "abc-123-def"
  }
}
```

### 6. android_read_ui_tree
Get accessibility tree structure

```python
{
  "name": "android_read_ui_tree",
  "arguments": {
    "depth_limit": 10,
    "device_id": "abc-123-def"
  }
}
```

### 7. android_launch_app
Launch application by package name

```python
{
  "name": "android_launch_app",
  "arguments": {
    "package_name": "com.android.chrome",
    "device_id": "abc-123-def"
  }
}
```

---

## 🐛 Troubleshooting

### Connection Issues

**Problem**: "Cannot connect to server"

**Solutions**:
1. Verify server is running: `curl http://localhost:8765`
2. Check firewall rules
3. Ensure correct IP address (not localhost unless USB tethering)
4. Try different network (WiFi vs Ethernet)

### Accessibility Service Won't Enable

**Problem**: Service keeps disabling after enabling

**Solution**:
1. Force stop app completely
2. Clear app data (Settings → Apps → Hermes Bridge)
3. Reboot device
4. Try enabling again

### Pairing Code Not Working

**Problem**: Authentication rejected

**Solutions**:
1. Generate new pairing code
2. Check time sync between devices
3. Verify pairing code format (6-char alphanumeric)
4. Restart both server and client apps

### Commands Not Executing

**Problem**: Tool calls succeed but no action on phone

**Possible causes**:
- Accessibility service permissions not granted
- Timeout exceeded (increase timeout_ms parameter)
- Wrong coordinate system (screen vs virtual)
- Another overlay blocking interactions

**Debug steps**:
1. Enable verbose logging in Android app
2. Check logcat output: `adb logcat | grep Hermes`
3. Verify device is unlocked during testing
4. Test basic tap at (100, 100) first

---

## 📊 Performance Notes

- **Latency**: ~100-300ms typical for simple commands
- **Max concurrent devices**: Depends on server resources (~10-20 recommended)
- **Battery impact**: Low with foreground service enabled
- **Network usage**: Minimal (text-based protocol)

---

## 🔐 Security Best Practices

1. **Never expose port 8765 publicly** without authentication
2. **Use WSS** (WebSocket Secure) for remote connections
3. **Rate limit** pairing code attempts
4. **Regular token rotation** for sessions
5. **Network isolation** using VLANs or private networks

---

## 🤝 Contributing

To contribute to this project:

1. Fork the repository
2. Create feature branch: `git checkout -b feat/amazing-feature`
3. Make changes and commit following conventions
4. Push to branch and open PR

See `docs/CONTRIBUTING.md` for detailed guidelines.

---

## 📞 Support & Resources

- **GitHub Issues**: Report bugs here
- **Discussions**: Ask questions, share ideas
- **Wiki**: Documentation and tutorials
- **Chat**: Join our community server

---

## ⚖️ License

This project is licensed under MIT License - see LICENSE file for details.

---

**Happy automating! 🚀**
