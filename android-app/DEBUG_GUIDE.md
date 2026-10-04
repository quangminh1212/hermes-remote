# 🔧 Debug và Test Hermes Android Bridge App

## 📋 Tổng quan

Hướng dẫn này sẽ giúp bạn debug và test ứng dụng Hermes Android Bridge một cách hiệu quả.

## 🎯 Preparation Environment

### Setup Android Emulator (Khuyến nghị)

**Cách 1: Tạo emulator từ Android Studio**
```bash
# Sử dụng Command Line Tools
$ANDROID_HOME/tools/bin/avdmanager create avd \
    --name "HermesTest" \
    --package "system-images;android-34;google_apis;x86_64" \
    --device "Pixel 4a" \
    --platform "android-34"

# Start emulator
$ANDROID_HOME/emulator/emulator -avd HermesTest
```

**Cách 2: Từ GUI Android Studio**
1. Tools → Device Manager → Create Device
2. Chọn Pixel 4a hoặc thiết bị nhỏ hơn
3. Chọn system image: x86_64 Google APIs (không có Google Play để nhẹ hơn)
4. RAM: 2GB, Heap: 512MB
5. Finish → Start emulator

### Kết nối qua USB (Physical Device)

**Enable Developer Options:**
1. Settings → About Phone
2. Tap "Build Number" 7 times
3. Enable "USB Debugging"
4. Connect to PC via USB
5. Authorize computer when prompted

## 🔍 Testing Workflow

### 1. Build & Deploy

```bash
cd android-app

# Using Gradle wrapper
./gradlew assembleDebug      # Linux/Mac
.\gradlew.bat assembleDebug  # Windows

# Install on device/emulator
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 2. Run Application

```bash
# Start app directly
adb shell am start -n com.hermes.bridge/com.hermes.bridge.ui.MainActivity

# Or use Android Studio to run with debugger attached
```

### 3. Monitor Logs

```bash
# View all logs
adb logcat

# Filter for HermesBridge tags
adb logcat | findstr "HermesBridge"  # Windows
adb logcat | grep "HermesBridge"     # Linux/Mac

# Filter specific components
adb logcat *:V WebSocketBridge:E AccessibilityBridge*I
```

### 4. Check Service Status

```bash
# Check if service is running
adb shell dumpsys activity services | grep -i "hermes"

# Check accessibility service
adb shell settings get secure enabled_accessibility_services

# Check foreground service notification
adb shell dumpsys notification | grep -A 5 "Hermes Bridge"
```

## 🧪 Unit Testing

### Run local unit tests

```bash
./gradlew test
```

### Run instrumentation tests on connected device

```bash
./gradlew connectedAndroidTest
```

## 🐛 Common Issues & Solutions

### Issue 1: Accessibility Permission Denied

**Symptoms:**
- Button "Enable Accessibility Service" shows but doesn't work
- Commands fail silently

**Solution:**
```bash
# Grant accessibility permission manually
adb shell cmd accessibility service grant-access-to-feature \
    -a com.hermes.bridge

# Or from Settings UI:
# Settings → Accessibility → Hermes Bridge → Toggle ON

# Verify status
adb shell settings get secure accessible_services
adb shell settings get secure disabled_accessible_services
```

### Issue 2: Connection Refused

**Symptoms:**
- WebSocket error in logs
- Connection fails immediately

**Logs check:**
```bash
adb logcat | grep "WebSocket"
```

**Solutions:**

1. **Firewall Blocking Port 8765:**
```powershell
# Windows Firewall allow inbound
New-NetFirewallRule -DisplayName "Hermes Bridge WebSocket" `
    -Direction Inbound -LocalPort 8765 -Protocol TCP -Action Allow

# Or disable temporarily for testing
netsh advfirewall set allprofiles state off
```

2. **Wrong IP Address:**
   - Use local LAN IP (e.g., `192.168.1.x`), not `localhost`
   - For localhost, add host routing on Android:
   ```bash
   adb root
   adb remount
   adb shell echo "10.0.2.2 localhost" >> /etc/hosts
   adb shell sync
   adb shell stop
   adb shell start
   ```

3. **Server Not Running:**
```bash
# Check if Python server is running
netstat -an | findstr "8765"  # Windows
ss -tlnp | grep 8765           # Linux

# Restart server
python python-relay-server/server.py
```

### Issue 3: Authentication Failed

**Symptoms:**
- Auth reject message
- Pairing code mismatch

**Debug steps:**
```bash
# Check pairing code on Python side
tail -f python-relay-server/logs/*.log

# Verify code entered correctly (no trailing spaces)
adb shell input text <code_here>
```

### Issue 4: Screenshot Returns Null

**Symptoms:**
- `get_screenshot` returns null
- Screenshot functionality not working

**Current limitation:** The current implementation creates an empty bitmap placeholder.

**Alternative approaches:**

1. **Use MediaProjection API:**
```xml
<!-- Add to AndroidManifest.xml -->
<uses-permission android:name="android.permission.CAPTURE_VIDEO_OUTPUT" />
```

2. **Use ADB screencap:**
```bash
adb shell screencap -p > screenshot.png
adb pull screenshot.png
```

3. **Enable display capture:**
```kotlin
// In AccessibilityBridgeService.kt
val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
val metrics = DisplayMetrics()
windowManager.defaultDisplay.getRealMetrics(metrics)

// Then use Bitmap.createBitmap() and copyPixelsFromBuffer()
```

### Issue 5: Text Input Fails

**Symptoms:**
- `type_text` commands don't insert text
- Keyboard doesn't appear

**Solutions:**

1. **Ensure input field has focus:**
   - Tap on the input field first before typing
   - Or use scroll to bring it into view

2. **Check UiAutomation is available:**
```bash
adb shell dumpsys ui | grep -i "automation"
```

3. **Try clipboard method:**
```bash
# Manually test clipboard paste
adb shell pm grant com.hermes.bridge android.permission.PASTE
```

### Issue 6: Battery Optimization Kills Background Service

**Symptoms:**
- Service stops after some time
- App disconnects unexpectedly

**Solution:**
```bash
# Disable battery optimization for this app
adb shell appops set com.hermes.bridge BACKGROUND_RUN_ALLOWED allow

# Or through Settings:
# Settings → Apps → Hermes Bridge → Battery → Unrestricted
```

## 📊 Performance Testing

### Benchmark Touch Events

```javascript
// Example timing measurement
const startTime = Date.now();
for (let i = 0; i < 100; i++) {
  await hermesServer.android_execute_command(deviceId, {
    action: "tap",
    params: {x: 500, y: 800}
  });
}
const endTime = Date.now();
console.log(`Average latency: ${(endTime - startTime) / 100}ms`);
```

### Network Latency

```bash
# Measure round-trip time
ping 10.0.2.2  # For emulator localhost mapping
ping 192.168.1.x  # For physical device over network
```

### Memory Usage

```bash
# Check memory consumption
adb shell dumpsys memapp com.hermes.bridge

# Monitor heap size
adb shell am dumpheap com.hermes.bridge /sdcard/hermes_dump.hprof
adb pull /sdcard/hermes_dump.hprof
```

## 🎬 Recording Session Commands

### Enable verbose logging

```kotlin
// In MainActivity.kt or Application class
import android.util.Log

class HermesBridgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.isLoggable("HermesBridge", Log.VERBOSE) = true
    }
}
```

### Capture command history

```bash
# Extract only command execution logs
adb logcat | grep "Command received" > command_history.txt
adb logcat | grep "command.complete" >> command_history.txt
```

## 🔑 Useful Shell Commands

### Reset Accessibility Service State

```bash
# Disable all accessibility services
adb shell settings put secure enabled_accessibility_services ""

# Re-enable specific service
adb shell settings put secure enabled_accessibility_services com.hermes.bridge/.service.AccessibilityBridgeService

# Restart service
adb shell am force-stop com.hermes.bridge
adb shell am start-secure com.hermes.bridge/com.hermes.bridge.ui.MainActivity
```

### Clear App Data

```bash
# Full reset
adb uninstall com.hermes.bridge
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Export/import configuration

```bash
# Backup preferences (if saved)
adb shell dumpsys sharedpreferences > prefs_backup.txt

# List all shared preferences
adb shell dumpsys sharedpreferences | grep -A 20 "com.hermes.bridge"
```

## 🚀 Advanced Debugging

### Hook into service methods

```kotlin
// Add breakpoint in AccessibilityBridgeService.kt
fun executeTap(x: Float, y: Float, durationMs: Int) {
    // Debugger will pause here
    scope.launch { ... }
}
```

### Instrument WebSocket traffic

```bash
# Add OkHttp logger interceptor to see raw messages
// In WebSocketClient.kt
val client = OkHttpClient.Builder()
    .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
    .build()
```

### Trace command execution

```bash
# Start tracing specific process
adb shell trace-cmd record -o /sdcard/hermes_trace.trace
adb shell chmod 777 /sdcard/hermes_trace.trace

# Stop tracing
adb shell trace-cmd stop
adb shell trace-cmd report -o output.txt < /sdcard/hermes_trace.trace

# Pull trace data
adb pull /sdcard/output.txt
```

## 📝 Checklist for Successful Testing

- [ ] Python relay server running on port 8765
- [ ] Device/emulator connected and visible in `adb devices`
- [ ] Accessibility Service enabled and granted permissions
- [ ] No firewall blocking WebSocket connections
- [ ] Pairing code matches between Python and Android
- [ ] Foreground service notification visible
- [ ] Logs show successful auth.grant response
- [ ] tap/swipe commands work on a simple app (e.g., Chrome)
- [ ] type_text works in a known input field
- [ ] read_ui_tree returns valid JSON structure
- [ ] Error handling works properly (timeout, invalid coordinates)

---

**Note:** Some debugging features may require root access or special permissions. Always test in controlled environments before production deployment.
