# Quick Test Guide - Hermes Android Bridge

## 🎯 Mục tiêu
Test hệ thống để đảm bảo mọi thứ hoạt động đúng trước khi deploy production.

---

## 🚀 TEST 1: Python Relay Server

### Bước 1: Start server
```bash
cd C:\Dev\Hermes_Android\python-relay-server

# Install dependencies nếu chưa làm
pip install -r requirements.txt

# Start server
python relay_server.py --port 8765
```

Server sẽ start và hiển thị:
```
🚀 Hermes Android Bridge - Relay Server
Starting server on http://0.0.0.0:8765
WebSocket endpoint: ws://0.0.0.0:8765/ws
Available tools: 7
```

### Bước 2: Test health endpoint
```bash
curl http://localhost:8765/
```

**Expected output:**
```json
{
  "status": "healthy",
  "connected_devices": 0,
  "uptime": "active"
}
```

### Bước 3: Test tools registration
```bash
curl http://localhost:8765/api/v1/tools/register | jq '.tools[].name'
```

**Expected output (7 tools):**
- `android_pair_device`
- `android_execute_tap`
- `android_type_text`
- `android_swipe_gesture`
- `android_get_screenshot`
- `android_read_ui_tree`
- `android_launch_app`

### Bước 4: Run unit tests
```bash
pytest test_relay_server.py -v
```

**Expected**: All tests passing ✅

---

## 📱 TEST 2: Android App Build & Installation

### Bước 1: Build APK (Nếu chưa có)
```bash
cd C:\Dev\Hermes_Android\android-app

# Clean build
./gradlew clean

# Debug build
./gradlew assembleDebug
```

APK location: `app/build/outputs/apk/debug/app-debug.apk`

### Bước 2: Install to device/emulator
```bash
# Connect device via USB or start emulator
adb devices

# Install app
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Bước 3: Verify installation
```bash
adb shell pm list packages | grep hermes
```

**Expected**: Output should include `com.hermes.bridge`

---

## 🔗 TEST 3: End-to-End Connection

### Bước 1: Note PC IP address
```bash
# Windows
ipconfig

# Look for IPv4 Address, e.g., 192.168.1.100

# Linux/Mac
ifconfig
```

### Bước 2: Configure Android app
1. Open "Hermes Bridge" app
2. Enter WebSocket URL:
   ```
   ws://YOUR_IP_ADDRESS:8765/ws
   Example: ws://192.168.1.100:8765/ws
   ```
3. Tap "Start Connection"
4. Wait for connection notification

### Bước 3: Check logs
```bash
adb logcat | grep -E "WebSocket|Bridge"
```

**Expected messages:**
- `✅ WebSocket connected successfully`
- `Authentication successful!`
- `Session token received`

---

## 🛠️ TEST 4: Tool Execution Flow

### Cách 1: Từ Python Console

```python
import asyncio
from aiohttp import ClientSession

async def test_commands():
    # Create pairing
    async with ClientSession() as session:
        async with session.post('http://localhost:8765/api/v1/tools/call', json={
            'tool': 'android_pair_device',
            'arguments': {'display_name': 'My Phone'}
        }) as response:
            result = await response.json()
            print(f"Pairing code: {result['result']['pairing_code']}")
            
            # Use this code in Android app
            
            # Then test tap command
            async with session.post('http://localhost:8765/api/v1/tools/call', json={
                'tool': 'android_execute_tap',
                'arguments': {'x': 500, 'y': 300}
            }) as response:
                result = await response.json()
                print("Tap command sent:", result.get('status'))

asyncio.run(test_commands())
```

### Cách 2: Từ Hermes Agent (nếu đã tích hợp)
```python
# Call from Hermes Agent
result = hermes.tools.call(
    "android_pair_device", 
    display_name="My Testing Phone"
)

print(f"Pairing Code: {result['pairing_code']}")
# Enter this code into Android app

# After pairing:
result = hermes.tools.call(
    "android_execute_tap",
    x=500, y=300
)

assert result['status'] == 'command_sent'
```

---

## ✨ TEST 5: Accessibility Actions

### Action 1: Tap at screen
**Test**: Execute tap at (100, 100)
**Expected**: Touch event registered, visible feedback

### Action 2: Type text
**Test**: Send "Hello World" text input
**Expected**: Text appears in active input field

### Action 3: Swipe gesture
**Test**: Swipe up from bottom of screen
**Expected**: Screen scrolls upward

### Action 4: Launch app
**Test**: Launch Chrome browser
```python
hermes.tools.call(
    "android_launch_app",
    package_name="com.android.chrome"
)
```
**Expected**: Chrome opens

---

## 🐛 Troubleshooting Common Issues

### Issue 1: Connection Refused
**Symptoms**: Android app shows "Cannot connect"
**Solutions**:
1. Verify server running: `curl http://localhost:8765/`
2. Check firewall allows port 8765
3. Ensure correct IP address (not localhost unless USB tethering)

### Issue 2: Pairing Code Rejected
**Symptoms**: "Authentication failed" message
**Solutions**:
1. Generate new pairing code
2. Check time sync between PC and phone
3. Verify pairing code format (6-char alphanumeric)

### Issue 3: Tap Not Working
**Symptoms**: No visual feedback when tap executed
**Causes**:
1. Accessibility service permissions not granted
2. Wrong coordinate system
3. Another overlay blocking gestures

**Debug commands**:
```bash
# Check accessibility permissions
adb shell dumpsys accessibility | grep "mEnabledServiceNames"

# Grant runtime permission
adb shell appops set com.hermes.bridge ACCESSIBILITY_STATE allow
```

---

## ✅ Success Criteria

All tests pass if:
- [ ] Server responds to health check (`/` endpoint returns JSON)
- [ ] All 7 tools are registered correctly
- [ ] Unit tests pass with green results
- [ ] Android app installs without errors
- [ ] Connection established successfully
- [ ] Authentication with pairing code works
- [ ] At least one action executes (tap or type)
- [ ] Logs show no critical errors

---

## 📊 Performance Metrics (Should Meet These)

| Metric | Target | How to Measure |
|--------|--------|----------------|
| Pairing time | <5s | From code generation to auth complete |
| Tap latency | 100-300ms | WebSocket send → action complete |
| Message throughput | >10 msg/s | Commands processed per second |
| Memory usage | <100MB | Server RSS memory |

---

## 🔍 Debugging Tools

### Python Server Debugging
```bash
# Verbose logging
python relay_server.py --debug

# Inspect WebSocket connections
netstat -ano | findstr :8765
```

### Android App Debugging
```bash
# View detailed logs
adb logcat *:D Hermes:* V

# Force stop app
adb shell am force-stop com.hermes.bridge

# Clear app data
adb shell pm clear com.hermes.bridge

# Reinstall fresh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

**Happy testing! 🚀**

If any issues encountered, check logs first before reporting.
