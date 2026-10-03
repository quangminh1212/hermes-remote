# 🎯 HƯỚNG DẪN SETUP NHANH 10 PHÚT - TIẾNG VIỆT

## ⚡ Bước 1: Kiểm tra Environment (30 giây)

```powershell
cd C:\Dev\Hermes_Android

REM Check Java
java -version

REM Check Android SDK path
echo %ANDROID_HOME%
```

### Nếu chưa có ANDROID_HOME:

**Cách nhanh nhất**: Cài Android Studio
1. Download từ: https://developer.android.com/studio
2. Install với default settings
3. Sau khi cài xong, mở PowerShell và chạy:

```powershell
$env:ANDROID_HOME="C:\Program Files (x86)\Android\android-sdk"
setx ANDROID_HOME $env:ANDROID_HOME
Restart-PowerShell
```

---

## 📱 Bước 2: Tạo Emulator Nhẹ (2 phút)

```powershell
cd emulator-setup
.\create_lite_emulator.bat
```

Script sẽ tự động:
- ✅ Tạo emulator `HermesTestPixel4aLite` 
- ✅ Cấu hình Pixel 4a x86_64 (nhẹ nhất!)
- ✅ Tối ưu RAM: 2GB thay vì 4GB
- ✅ Boot time: ~15-30s

---

## 🔧 Bước 3: Build APK (2 phút)

```powershell
cd android-app

REM Download Gradle wrapper nếu cần
.\download_gradle_wrapper.bat

REM Build debug APK
gradlew.bat assembleDebug
```

APK output tại: `app\build\outputs\apk\debug\app-debug.apk`

---

## 🏃 Bước 4: Start Emulator & Test (3 phút)

### A. Start emulator trong PowerShell 1:

```powershell
%ANDROID_HOME%\emulator\emulator -avd HermesTestPixel4aLite -no-audio
```

Chờ ~30s cho đến khi emulator hiện ra cửa sổ GUI.

### B. Trong PowerShell 2 (cửa sổ mới):

```powershell
REM Chờ emu boot xong
start-sleep -seconds 30

REM Kiểm tra kết nối
adb devices

REM Install app
adb install app\build\outputs\apk\debug\app-debug.apk

REM Grant permissions
adb shell pm grant com.hermes.bridge android.permission.ACCESSIBILITY_STATE
adb shell pm grant com.hermes.bridge android.permission.FOREGROUND_SERVICE

REM Launch app
adb shell monkey -p com.hermes.bridge -c android.intent.category.LAUNCHER 1

REM Xem logs real-time
adb logcat | findstr /C:"WebSocket" /C:"Bridge"
```

---

## 🎮 Bước 5: Testing (Vô tận)

### Option 1: Dùng Android Studio (Dễ nhất)

1. Mở Android Studio → Project Structure → android-app folder
2. Chờ Gradle sync
3. Nhấn ▶️ Run button
4. Chọn target device: emulator hoặc phone thật

### Option 2: Command line

```powershell
# Reinstall/update app anytime
adb install -r app\build\outputs\apk\debug\app-debug.apk

# View real-time logs
adb logcat > test_log.txt

# Take screenshot
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png .
```

---

## 🐛 Xử lý lỗi nhanh

| Lỗi | Giải pháp |
|-----|----------|
| "SDK not found" | Set `setx ANDROID_HOME "path"` |
| "No emulator running" | Start emulator first |
| "ADB permission denied" | Run as admin or restart ADB server |
| "Build failed" | `gradlew clean` then rebuild |
| App không launch | Check accessibility permission granted |

---

## ✅ Checklist Thành công

- [ ] Java/JDK 17+ installed
- [ ] ANDROID_HOME set correctly  
- [ ] Emulator created and running
- [ ] APK built successfully
- [ ] App installed on emulator
- [ ] Accessibility permission granted
- [ ] WebSocket connection established
- [ ] Logs showing successful communication

---

## 🚀 Kết Luận

Tổng thời gian: **10 phút** để setup xong!  

Mỗi lần test sau đó chỉ mất: **30 seconds** (reinstall + run)

**Để bắt đầu ngay:**

```powershell
cd C:\Dev\Hermes_Android
.\chuong_tay_test_automatic.bat
```

(But this requires ANDROID_HOME to be set first!)

---

*Quick Setup Guide - Vietnamese Priority | Complete in 10 minutes*
