# 🛠️ Common ADB Commands for Hermes Android Bridge

Quick reference guide for Android Debug Bridge commands used with Hermes Bridge.

---

## 📱 Device Connection & Status

### Check Connected Devices
```bash
# List all connected devices/emulators
adb devices

# Output example:
# List of devices attached
# emulator-5554    device
# 192.168.1.100:5555    device
```

### Start Emulator
```bash
# List available AVDs
avdmanager list avd

# Start specific emulator
emulator -avd Pixel_4_API_34

# Start with specific options
emulator -avd Pixel_4_API_34 -wipe-data  # Fresh start
```

### Find Device IP (Physical Device)
```bash
# On physical device over network
adb shell ip addr show | grep "inet "
# or
adb shell ifconfig | grep "inet"
```

---

## 🔍 App Management

### Install APK
```bash
# Install debug build
adb install app/build/outputs/apk/debug/app-debug.apk

# Force update existing app
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Install from URL (requires server running)
adb install https://example.com/app.apk
```

### Uninstall App
```bash
# Remove application completely
adb uninstall com.hermes.bridge

# Keep data and cache
adb uninstall -k com.hermes.bridge
```

### Launch Application
```bash
# Open main activity
adb shell am start -n com.hermes.bridge/com.hermes.bridge.ui.MainActivity

# Open in background
adb shell am start -W -n com.hermes.bridge/.MainActivity

# Clear back stack before launch
adb shell am start -a android.intent.action.MAIN --ez clear-stack true
```

### View Package Information
```bash
# List all installed apps
adb shell pm list packages

# Filter by name
adb shell pm list packages | grep hermès

# Show detailed package info
adb shell dumpsys package com.hermes.bridge
```

---

## 🔧 Accessibility Service Control

### Enable Accessibility Service
```bash
# Check currently enabled services
adb shell settings get secure enabled_accessibility_services

# Enable Hermes Bridge specifically
adb shell settings put secure enabled_accessibility_services \
    com.hermes.bridge/.service.AccessibilityBridgeService

# Verify it's enabled
adb shell settings get secure enabled_accessibility_services
# Should show: com.hermes.bridge/.service.AccessibilityBridgeService;...
```

### Disable All Accessibility Services
```bash
# Turn off all accessibility services (useful for reset)
adb shell settings put secure enabled_accessibility_services ""
```

### Grant Accessibility Permission
```bash
# Grant permission to service
adb shell cmd accessibility service grant-access-to-feature \
    -a com.hermes.bridge

# Or via intent (alternative method)
adb shell pm grant com.hermes.bridge \
    android.permission.ACCESSIBILITY_STATE
```

---

## 📊 Log Management

### View Logs
```bash
# Start logcat monitoring
adb logcat

# Filter by tag
adb logcat | grep HermesBridge

# Filter multiple tags
adb logcat "*:V WebSocket:I AccessibilityBridge:I"

# Save logs to file
adb logcat > hermes_logs.txt

# Clear log buffer
adb logcat -c

# Stop logcat
Ctrl+C
```

### Advanced Log Filtering
```bash
# Only show errors and above
adb logcat *:E

# Specific process filtering
adb logcat :S:1000:*

# Time-based filtering (show last 5 minutes)
adb logcat -d -v time | tail -n 1000

# With thread IDs
adb logcat -v threadtime
```

---

## 🎯 Command Execution Testing

### Simulate Tap Events
```bash
# Send touch event manually
adb shell input tap 500 800

# Long press at coordinates
adb shell input swipe 500 800 500 800 200

# Swipe gesture
adb shell input swipe 500 800 500 400 300

# Pinch gesture
adb shell input pinch 1.0 200 300 1.5 400 500
```

### Text Input Methods
```bash
# Type text using shell
adb shell input text "Hello World!"

# Send specific key codes
adb shell input keyevent KEYCODE_A       # Send 'a'
adb shell input keyevent KEYCODE_ENTER   # Enter key
adb shell input keyevent KEYCODE_ESCAPE  # Escape key

# Paste from clipboard
adb shell input keyevent KEYCODE_PASTE
```

### Navigation Button Simulation
```bash
# Home button
adb shell input keyevent KEYCODE_HOME

# Back button
adb shell input keyevent KEYCODE_BACK

# Recent apps
adb shell input keyevent KEYCODE_RECENT

# Volume controls
adb shell input keyevent KEYCODE_VOLUME_UP
adb shell input keyevent KEYCODE_VOLUME_DOWN
adb shell input keyevent KEYCODE_MUTE

# Power button (may not work on all devices)
adb shell input keyevent KEYCODE_POWER
```

### Window Switching
```bash
# Move between apps
adb shell input keyevent KEYCODE_APP_SWITCH

# Go to home screen
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME

# Return to previous app
adb shell input keyevent 176  # BACK_SPACE alternative
```

---

## 🖼️ Screen Capture

### Take Screenshot
```bash
# Capture screen to device
adb shell screencap -p /sdcard/screenshot.png

# Pull screenshot to PC
adb pull /sdcard/screenshot.png ./screenshot.png

# Quick capture and pull in one command
adb shell screencap -p | adb exec-out cat > screenshot.png
```

### Record Screen
```bash
# Start recording (needs root/system access)
adb shell screenrecord /sdcard/recording.mp4

# Limit duration
adb shell screenrecord --duration=30 /sdcard/recording.mp4

# Set resolution
adb shell screenrecord --size=1280x720 /sdcard/recording.mp4
```

---

## ⚙️ System Settings

### Change Display Settings
```bash
# Get current display metrics
adb shell wm size
adb shell wm density

# Set custom resolution
adb shell wm size 1920x1080

# Reset to default
adb shell wm size reset
```

### Animation Scale
```bash
# Slow down animations (for debugging)
adb shell settings put global window_animation_scale 1.0
adb shell settings put global transition_animation_scale 1.0
adb shell settings put global animator_duration_scale 1.0

# Speed up animations
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5

# Turn off animations completely
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
```

### Battery Optimization
```bash
# Check battery optimization status
adb shell dumpsys battery

# Disable battery optimization for Hermes Bridge
adb shell appops set com.hermes.bridge RUN_IN_BACKGROUND allow
adb shell appops set com.hermes.bridge SYSTEM_ALERT_WINDOW allow

# Re-enable later
adb shell appops reset com.hermes.bridge
```

---

## 🔐 Permissions Management

### Check Current Permissions
```bash
# List all permissions granted to app
adb shell dumpsys package com.hermes.bridge | grep "Granted"

# Check specific permission
adb shell dumpsys package com.hermes.bridge | grep "ACCESSIBILITY_STATE"

# View all dangerous permissions
adb shell pm list permissions | grep -i dangerous
```

### Grant/Deny Permissions
```bash
# Grant runtime permission
adb shell pm grant com.hermes.bridge android.permission.PASTE

# Deny permission
adb shell pm revoke com.hermes.bridge android.permission.PASTE

# Check if permission is granted
adb shell pm has-perm 'com.hermes.bridge' android.permission.PASTE
```

### Required Permissions for Full Functionality
```bash
# Execute these commands to enable full capabilities
adb shell pm grant com.hermes.bridge android.permission.INTERACT_ACROSS_USERS
adb shell pm grant com.hermes.bridge android.permission.CAPTURE_AUDIO_OUTPUT
adb shell pm grant com.hermes.bridge android.permission.MODIFY_AUDIO_SETTINGS
adb shell pm grant com.hermes.bridge android.permission.RECORD_AUDIO
```

---

## 🔄 Service Lifecycle Control

### Restart Services
```bash
# Force stop and restart app
adb shell am force-stop com.hermes.bridge
adb shell monkey -p com.hermes.bridge -c android.intent.category.LAUNCHER 1

# Restart specific service
adb shell cmd service_manager restart com.hermes.bridge.service.AccessibilityBridgeService
```

### Monitor Service Status
```bash
# Check if accessibility service is running
adb shell dumpsys activity services | grep -i accessibility

# View foreground services
adb shell dumpsys service | grep -A 5 "foreground"

# Check notification status
adb shell dumpsys notification | grep -A 10 "Hermes Bridge"
```

---

## 📦 File Operations

### Push/Pull Files
```bash
# Copy file from PC to device
adb push config.json /sdcard/hermes/config.json

# Copy file from device to PC
adb pull /sdcard/hermes/config.json ./backup_config.json

# Bulk copy directory
adb push ./* /sdcard/hermes/

# Recursive pull
adb pull /sdcard/hermes ./backups/hermes/
```

### Create Directories
```bash
# Create single directory
adb shell mkdir /sdcard/hermes/logs

# Create nested directories
adb shell mkdir -p /sdcard/hermes/{logs,config,cache}

# Set proper permissions
adb shell chmod 755 /sdcard/hermes
```

### Clean Up Temporary Files
```bash
# Clear temp files
adb shell rm -rf /sdcard/hermes/cache/*
adb shell rm -rf /sdcard/hermes/logs/*.log
```

---

## 🌐 Network Diagnostics

### Test Network Connectivity
```bash
# Ping Python server from device
adb shell ping 10.0.2.2     # For localhost mapping in emulator
adb shell ping 192.168.1.x  # Physical device IP

# Check port availability
adb shell netstat -an | grep 8765

# Verify routing table
adb shell route -n
```

### Configure DNS (if needed)
```bash
# Set DNS for testing
adb shell ip route replace default via 192.168.1.1 dev wlan0

# Flush DNS cache
adb shell ip rule flush
```

---

## 🧪 Performance Monitoring

### Memory Usage
```bash
# Check memory consumption
adb shell dumpsys meminfo com.hermes.bridge

# Export heap snapshot
adb shell am dumpheap com.hermes.bridge /sdcard/dump.hprof
adb pull /sdcard/dump.hprof

# View VM state
adb shell dumpsys activity processes | head -50
```

### CPU Usage
```bash
# Monitor CPU usage per app
adb shell top -n 5 | grep com.hermes.bridge

# Get detailed system stats
adb shelldumpsys cpuinfo

# Profile execution time
adb shell profile start com.hermes.bridge
adb shell profile stop
adb shell profile dump
```

---

## 🚨 Emergency Recovery

### Factory Reset (Last Resort)
```bash
# Wipe all user data and reset
adb reboot recovery

# In recovery mode select "Wipe data/factory reset"

# Then boot normally
adb reboot
```

### Rollback Previous Version
```bash
# Backup current version
adb backup -f hermes_backup.ab com.hermes.bridge

# Restore backup
adb restore hermes_backup.ab
```

### Reboot Device
```bash
# Soft reboot
adb reboot

# Bootloader mode
adb reboot bootloader

# Download mode (Samsung)
adb reboot download

# Recovery mode
adb reboot recovery
```

---

## 💡 Pro Tips

### Batch Commands Script
Create `adb_commands.sh`:
```bash
#!/bin/bash
echo "Starting Hermes diagnostic sequence..."
adb devices
adb shell dumpsys activity services | grep accessibility
adb shell settings get secure enabled_accessibility_services
adb logcat -c
echo "Diagnostic complete!"
```

### One-Liner Tests
```bash
# Full connection test
(adb devices && adb shell input keyevent KEYCODE_HOME && echo "✅ Connection OK")

# Quick accessibility check
adb shell settings get secure enabled_accessibility_services | grep hermès && echo "✅ Access OK"

# Performance benchmark
time (adb shell input tap 500 800 && echo "Tap completed")
```

### Useful Aliases
Add to `.bashrc` or `.zshrc`:
```bash
alias adb-h='adb shell input tap 400 800'  # Tap center
alias adb-s='adb shell screencap -p | tee screenshot.png'
alias adb-log='adb logcat | grep -i HermesBridge'
alias adb-clear='adb logcat -c && echo "Logs cleared"'
```

---

## ⚠️ Safety Warnings

### Dangerous Commands to Use Carefully
```bash
# Don't wipe without backup
adb shell pm clear com.hermes.bridge  # ❌ May lose all data

# Be careful with system properties
adb shell setprop sys.xxx.yyy zzz      # ❌ Can break system

# Root commands require caution
adb root                                # ⚠️ Only on rooted devices
```

### Best Practices
1. ✅ Always backup before major changes
2. ✅ Test destructive commands on emulator first
3. ✅ Document any modified system settings
4. ✅ Keep track of ADB sessions and tokens

---

*Quick Reference Guide v0.1.0*  
*Maintained by Hermes Bridge Team*
