# 🚀 Quick Start Guide - Hermes Android Bridge

Hướng dẫn nhanh để thiết lập và chạy Hermes Android Bridge trong 10 phút!

## ⏱️ Timeline

- **Minute 1-2:** Setup Python relay server
- **Minute 3-4:** Build and install Android app  
- **Minute 5-6:** Enable accessibility service
- **Minute 7-8:** Connect and test
- **Minute 9-10:** Troubleshoot (nếu cần)

---

## 📋 Prerequisites Checklist

Before starting, ensure you have:

✅ Python 3.9+ installed  
✅ Android SDK with platform tools  
✅ Java JDK 17+ installed  
✅ Internet connection (for dependencies)  
✅ Android device or emulator  

### Verify Python Installation
```bash
python --version  # Should be 3.9 or higher
python -c "import asyncio; import websockets; print('Python OK')"
```

### Verify Android SDK
```bash
adb version  # Should show version info
$ANDROID_HOME/platform-tools/adb version  # Or full path
```

### Check Java
```bash
java --version  # Should be 17 or higher
```

---

## 🔧 Step 1: Setup Python Relay Server

### Clone Dependencies (Optional)
If not already cloned:

```bash
# Navigate to python directory
cd python-relay-server

# Install dependencies
pip install -r requirements.txt
```

### Configure Server Settings

Create `config.json` in the same directory as `server.py`:

```json
{
  "websocket_port": 8765,
  "device_timeout_seconds": 300,
  "default_max_concurrent_devices": 10
}
```

**Important:** Make sure port 8765 is available:

#### Windows
```powershell
netstat -ano | findstr ":8765"
```

#### Linux/Mac
```bash
lsof -i :8765  # or
ss -tlnp | grep 8765
```

### Start the Server

```bash
# From project root directory
cd python-relay-server
python server.py
```

You should see output like:
```
🚀 Starting Hermes Relay Server...
🌐 WebSocket server listening on ws://0.0.0.0:8765/ws
💡 Generate pairing code with: hermes_pair_device <display_name>
```

Keep this terminal window open! The server needs to stay running.

---

## 📱 Step 2: Build & Install Android App

### Using Android Studio (Recommended for first time)

1. **Open Project**
   ```bash
   cd android-app
   ```
   Then in Android Studio: File → Open → Select `android-app` folder

2. **Wait for Gradle Sync**
   - First build will take 2-5 minutes to download dependencies
   - Watch status bar at bottom of screen

3. **Build Debug APK**
   - Build → Make Project
   - Wait until it says "BUILD SUCCESSFUL"

4. **Install to Device**
   
   **Option A: Run via Android Studio**
   - Click green ▶️ Run button
   - Select your device/emulator
   - Press Run

   **Option B: Command Line**
   ```bash
   # Clean old builds
   ./gradlew clean
   
   # Build debug APK
   ./gradlew assembleDebug
   
   # Install (Windows)
   .\gradlew.bat assembleDebug
   
   # Install APK
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### Expected Output
```
[INFO] APK found at: app/build/outputs/apk/debug/app-debug.apk
[SUCCESS] Installing on device...
Success
```

---

## 🔐 Step 3: Enable Accessibility Service

This is a critical step that many users skip!

### Via Android Settings UI

1. **Open Settings** → System → Advanced → Developer options
   - If you don't see Developer options: Go to About Phone → Tap "Build Number" 7 times

2. **Enable USB Debugging**
   - In Developer Options, turn ON "USB debugging"

3. **Find Accessibility Services**
   - Settings → Accessibility → Installed accessibility services
   - OR: Settings → Apps → [See all apps] → Hermes Bridge → Permissions → All permissions

4. **Enable Hermes Bridge**
   - Find "Hermes Bridge" in the list
   - Toggle it ON
   - Confirm the security warning prompt

### Alternative: Grant via ADB (if UI fails)

```bash
# List current accessibility services
adb shell settings get secure enabled_accessibility_services

# Enable specifically
adb shell settings put secure enabled_accessibility_services com.hermes.bridge/.service.AccessibilityBridgeService

# Restart app
adb shell am force-stop com.hermes.bridge
adb shell monkey -p com.hermes.bridge -c android.intent.category.LAUNCHER 1
```

---

## 🔌 Step 4: Connect and Pair Devices

### Method 1: Using Python Server GUI

In the Python relay server terminal where you started the server:

```python
# Type this in the Python console/REPL
from hermes_relay_server import register_hermes_tools
register_hermes_tools()  # Register command-line tools

# Now generate pairing code:
hermes_pair_device "My Test Phone"

# Output will be something like:
# ✅ Registered device 'My Test Phone' with ID: TEST-ABC-123
# Pairing code: TEST-ABC-123
```

Copy the pairing code! It will look like: `TEST-ABC-123`

### Method 2: Manual Pairing Code Generation

```bash
# Generate random pairing code format
echo "TEST-$RANDOM-$RANDOM"  # Just an example
```

Or use the actual UUID format:
```python
import uuid
print(f"PAIR-{uuid.uuid4().hex[:6].upper()}")
```

---

## 📲 Step 5: Launch Android App & Input Pairing Code

1. **Open App**
   - On Android device: Tap "Hermes Bridge" icon
   - OR via ADB:
   ```bash
   adb shell am start -n com.hermes.bridge/com.hermes.bridge.ui.MainActivity
   ```

2. **Wait for Notification**
   - You might see a notification "Connect via Hermes"
   - Or tap the app icon from launcher

3. **Enter Configuration**
   - Server URL should be: `ws://localhost:8765/ws` 
     - For physical device over network: `ws://YOUR_PC_IP:8765/ws`
   - Enter pairing code you generated
   
4. **Click "Start Connection"**
   - Status indicator should change from red → yellow → green
   - Green means connected successfully!

---

## 🧪 Step 6: Test Basic Commands

Use the test client we created:

```bash
cd python-relay-server
python test_android_bridge.py YOUR_PAIRING_CODE_HERE
```

Expected output:
```
🔧 Hermes Android Bridge Test Client
Enter pairing code: TEST-ABC-123
Connecting to ws://localhost:8765/ws...
✅ Authentication successful!
📋 Granted permissions: ['tap', 'type_text', 'swipe', 'ui_read']

🎯 RUNNING DEMO SEQUENCE
============================================================

📊 1. Getting device information...
   Model: Pixel 7 Pro
   Android Version: 14
   Screen: 1440x3120

🔍 2. Reading current UI tree...
   Root node: com.android.launcher3.LauncherAppWidgetHostView

🔄 3. Simulating tap at center...
✅ Command completed successfully

📜 4. Scrolling down...
✅ Command completed successfully

✍️ 5. Testing text input...
✅ Command completed successfully

🚀 6. Attempting to launch Chrome...
✅ Command completed successfully

🏠 7. Pressing home button to exit...
✅ Command completed successfully

✅ Demo sequence completed!
```

---

## 🎉 Success! What's Next?

Congratulations! Your Android device is now controlled by Python via WebSocket!

### Try These Commands Manually

```python
# Import your bridge client
from your_client import AndroidBridgeTester

tester = AndroidBridgeTester()
await tester.connect("YOUR-PAIRING-CODE")

# Various commands you can try:
await tester.tap(500, 800)                    # Tap at position
await tester.swipe(500, 1000, 500, 200)       # Swipe up
await tester.type_text("Hello World!")        # Type text
await tester.read_ui_tree()                    # Read screen structure
await tester.get_screenshot()                  # Get screenshot
await tester.press_home()                      # Press home button
await tester.scroll("down")                    # Scroll down
await tester.launch_app("com.android.chrome")  # Open Chrome
```

### Useful Next Steps

1. **Explore the API Documentation** (`API_DOCUMENTATION.md`)
2. **Read troubleshooting guide** (`DEBUG_GUIDE.md`)
3. **Customize build.gradle.kts** for your specific needs
4. **Add new features** based on your requirements
5. **Deploy to production** with proper security settings

---

## ⚡ Common Issues & Quick Fixes

### Issue: "Connection refused"

**Quick Fix:** Check if server is running
```bash
# In one terminal
nc -zv localhost 8765    # Linux/Mac
telnet localhost 8765    # Windows
```

**If no response:**
- Make sure `server.py` is still running
- Check firewall settings
- Try different port if 8765 is blocked

### Issue: "Pairing code invalid"

**Quick Fix:**
1. Regenerate pairing code: `hermes_pair_device "New Name"`
2. Ensure exact match (no spaces/tabs)
3. Server and Android must be on the same LAN

### Issue: "Accessibility denied"

**Quick Fix:**
```bash
# Re-enable via ADB
adb shell settings put secure enabled_accessibility_services com.hermes.bridge/.service.AccessibilityBridgeService
```

Then reboot device or restart app.

### Issue: "Commands work slowly"

**Check network latency:**
```bash
ping localhost          # Emulator
ping YOUR_PC_IP         # Physical device
```

High latency (>100ms) indicates network bottleneck. Use same WiFi network.

---

## 📞 Support & Help

If you're stuck:

1. **Check logs:**
   ```bash
   adb logcat | grep HermesBridge
   ```

2. **Verify connectivity:**
   ```bash
   adb shell ping YOUR_PC_IP
   ```

3. **Review documentation:**
   - README.md
   - API_DOCUMENTATION.md
   - DEBUG_GUIDE.md

4. **GitHub Issues:** Report bugs with error messages

---

## 🎯 Example: Remote Android Control Session

Here's a complete session flow:

```python
import asyncio
import websockets
import json

async def remote_control_session():
    async with websockets.connect('ws://localhost:8765/ws') as ws:
        
        # Authenticate
        await ws.send(json.dumps({
            'type': 'auth.request',
            'pairing_code': 'TEST-ABC-123'
        }))
        
        auth_resp = await ws.recv()
        print(f"Auth: {auth_resp}")
        
        # Navigate to calculator app
        await ws.send(json.dumps({
            'type': 'command.execute',
            'request_id': 'open_calculator',
            'action': 'launch_app',
            'params': {'package_name': 'com.google.calculator'}
        }))
        
        result = await ws.recv()
        print(f"Calculator: {result}")
        
        # Tap number buttons
        for num in [1, 2, 3, '+']:
            coords = {"x": 300 + num*50, "y": 600}
            await ws.send(json.dumps({
                'type': 'command.execute',
                'request_id': f'btn_{num}',
                'action': 'tap',
                'params': coords
            }))
            
        # Home to close
        await ws.send(json.dumps({
            'type': 'command.execute',
            'request_id': 'home',
            'action': 'press_home'
        }))
        
        # Close connection
        await ws.close()

# Run the session
asyncio.run(remote_control_session())
```

---

## 🎁 Bonus Tips

### Disable Battery Optimization for Background Service

To prevent Android from killing the service while idle:

```bash
adb shell appops set com.hermes.bridge RUN_IN_BACKGROUND allow
adb shell appops set com.hermes_ignore_background allow
```

### Increase Animation Speeds

For faster interactions during testing:

```bash
adb shell settings put global window_animation_scale 0.5x
adb shell settings put global transition_animation_scale 0.5x
adb shell settings put global animator_duration_scale 0.5x
```

### Create Custom Shortcut

Add home screen shortcut to quickly launch the app:

1. Long press home screen → Widgets
2. Find any launcher widget
3. Search for "Hermes Bridge"
4. Drag to home screen

---

Happy automating! 🤖📱
