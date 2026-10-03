# 📱 HƯỚNG DẪN TẠO VÀ SỬ DỤNG EMULATOR TEST NHẸ

## 🎯 Mục Tiêu
Tạo và sử dụng Android emulator **NHẸ NHẤT**, **NHIỀU NHẤT** để test Hermes Bridge app mà không làm chậm máy.

---

## ⚙️ Cấu Hình Tối Ưu Cho Emulator Nhẹ

### 1. Design Emulator (Recommended)

| Tham số | Giá trị | Lý do chọn |
|---------|---------|-------------|
| **Device Name** | Pixel 4a Lite | Màn hình nhỏ (~5.8"), RAM thấp |
| **Android Version** | API 34 (Android 14) | Phiên bản mới nhất nhưng nhẹ |
| **System Image** | x86_64 Google APIs | CPU giả lập nhanh, không cần Google Play |
| **RAM Allocated** | 2048 MB | Tối ưu cho test automation |
| **Internal Storage** | 4 GB | Đủ cho app + logs |
| **VM Heap Size** | 512 MB | Tiết kiệm RAM |
| **CPU Cores** | 4 | Đa luồng cho hiệu suất tốt |
| **GPU** | Hardware accelerated | Tăng tốc đồ họa phần cứng |
| **Audio** | Disabled | Không cần âm thanh cho test |

### 2. Tại sao cấu hình này?

✅ **x86_64 Architecture**: Chạy native trên CPU Intel/AMD, nhanh gấp 5-10x ARM  
✅ **No Google Play**: Tiết kiệm ~500MB storage và RAM  
✅ **Pixel 4a**: Thiết bị ảo nhỏ gọn, boot nhanh hơn Pixel 6+  
✅ **API 34**: Phiên bản mới nhất với hiệu suất tối ưu  

---

## 🚀 Cách Sử Dụng Tự Động

### Method 1: Chạy Tự Động Hoàn Toàn (Khuyến Nghị)

```bash
cd C:\Dev\Hermes_Android

# Windows
chuong_tay_test_automatic.bat

# Linux/Mac
chmod +x chuong_tay_test_automatic.sh
./chuong_tay_test_automatic.sh
```

**Điều gì xảy ra?**
1. ✅ Kiểm tra môi trường (Java/JDK)
2. ✅ Download Gradle Wrapper nếu cần
3. ✅ Build APK debug từ source code
4. ✅ Tạo hoặc dùng emulator đã có sẵn
5. ✅ Start emulator tự động
6. ✅ Cài đặt ứng dụng lên emulator
7. ✅ Launch app và chụp ảnh màn hình
8. ✅ Hiển thị logs quan trọng
9. ✅ Báo cáo tổng kết

**Thời gian:** ~2-3 phút  
**RAM tiêu thụ:** ~1.5GB cho emulator

---

### Method 2: Từng Bước Thủ Công

#### Bước 1: Tạo Emulator Nhẹ

```bash
# Windows
emu lator-setup\create_lite_emulator.bat

# Linux/Mac  
chmod +x emu lator-setup/create_lite_emulator.sh
mu lator-setup/create_lite_emulator.sh
```

Emulator sẽ được tạo với tên: `HermesTestPixel4aLite`

#### Bước 2: Build APK

```bash
cd android-app
gradlew.bat assembleDebug  # Windows
./gradlew assembleDebug    # Linux/Mac
```

APK output tại: `app/build/outputs/apk/debug/app-debug.apk`

#### Bước 3: Start Emulator

```bash
# Tìm SDK path của bạn
echo %ANDROID_HOME%  # Windows
echo $ANDROID_HOME   # Linux/Mac

# Start emulator
%ANDROID_HOME%\emulator\emulator -avd HermesTestPixel4aLite -no-audio
```

#### Bước 4: Install & Test

```bash
# Chờ 30 giây cho emulator khởi động

# Install app
adb install app/build/outputs/apk/debug/app-debug.apk

# Grant permissions
adb shell pm grant com.hermes.bridge android.permission.ACCESSIBILITY_STATE
adb shell pm grant com.hermes.bridge android.permission.FOREGROUND_SERVICE

# Launch app
adb shell monkey -p com.hermes.bridge -c android.intent.category.LAUNCHER 1

# Xem logs
adb logcat
```

---

## 📊 So Sánh Hiệu Suất

### Cấu hình thông thường (KHÔNG TỐI ƯU)
❌ Pixel 6 Pro → Màng hình lớn, boot chậm  
❌ Google Play image → Extra services, nặng ~2GB  
❌ 4GB RAM allocation → Overhead  
❌ ARM system image → QEMU emulation chậm  

### Cấu hình nhẹ của chúng ta (TỐI ƯU) ✅
✅ Pixel 4a Lite → Màn hình nhỏ, boot ~15s  
✅ No Google Play → System image chỉ ~700MB  
✅ 2GB RAM allocation → Just enough for testing  
✅ x86_64 architecture → Native execution, 10x faster  

---

## 💡 Tips & Troubleshooting

### Nếu Emulator quá chậm

1. **Enable Hyper-V / Virtualization**
   ```powershell
   # Check if virtualization is enabled
   systeminfo | findstr /C:"Hyper-V"
   
   # If not, enable in BIOS/UEFI settings
   ```

2. **Reduce other resource usage**
   - Close Chrome tabs
   - Stop background apps
   - Disable unnecessary Windows services

3. **Use SSD storage**
   - Emulator boots much faster from SSD (~15s vs ~60s on HDD)

4. **Keep only one emulator running**
   - Multiple emulators consume massive RAM/CPU

### Quick Commands Reference

```bash
# List all emulators
%ANDROID_HOME%\emulator\emulator -list-avds
emulator -list-avds  # Linux/Mac

# Start specific emulator
%ANDROID_HOME%\emulator\emulator -avd HermesTestPixel4aLite -no-audio
emulator -avd HermesTestPixel4aLite -no-audio  # Linux/Mac

# Kill emulator
adb kill-server

# Clean build
gradlew clean assembleDebug

# Reboot emulator
adb reboot

# Take screenshot manually
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png

# Real-time logs
adb logcat -v time
```

### Performance Monitoring

```cmd
REM Windows - Monitor emulator processes
tasklist | findstr "emulator"

REM Linux/Mac
ps aux | grep emulator
```

Check memory usage:
```bash
# In emulator terminal or ADB shell
dumpsys meminfo com.hermes.bridge
```

---

## 🔧 Customization Options

### Thay đổi thiết bị emulator

Tạo với Nexus 5 thay vì Pixel 4a:
```bash
yes | avdmanager create avd \
    --name HermesTestNexus5Lite \
    --package "system-images;android-34;google_apis;x86_64" \
    --device "nexus_5" \
    --abi x86_64
```

### Thay đổi Android version

Dùng Android 13 thay vì Android 14:
```bash
yes | avdmanager create avd \
    --name HermesTestA13 \
    --package "system-images;android-33;google_apis;x86_64" \
    --device "pixel_4a" \
    --abi x86_64
```

### Tăng tài nguyên cho emu lator

Tăng RAM lên 3GB:
1. Edit file: `~/.android/avd/HermesTestPixel4aLite.avd/config.ini`
2. Add/change:
   ```ini
   ram.size=3072
   hw.cpu.ncore=4
   vm.heapSize=512
   ```

### Enable GPU for better graphics

Edit `config.ini`:
```ini
gpu.mode=host
hw.gpu.enabled=yes
hw.gpu.mode=auto
```

---

## 📋 Checklist Để Test Hiệu Quả

### Trước khi test:
- [ ] JDK 17+ installed
- [ ] Android SDK properly configured (`ANDROID_HOME` set)
- [ ] At least 4GB free RAM
- [ ] Virtualization enabled in BIOS
- [ ] SSD preferred for emulator storage

### Khi chạy test:
- [ ] Run `chuong_tay_test_automatic.bat` 
- [ ] Wait for complete startup (~2-3 minutes)
- [ ] Observe emulator performance (should boot in <30s)
- [ ] Check app launches without crashes
- [ ] Verify WebSocket connection establishes
- [ ] Review logs for any errors

### Sau khi test xong:
- [ ] Close emulator to free resources
- [ ] Review screenshots taken
- [ ] Check app logs in `adb logcat`
- [ ] Delete emulator if not needed: `avdmanager delete avd -n HermesTestPixel4aLite`

---

## 🎮 Bonus: Quick Emulator Controls

### Keyboard Shortcuts (Emulator Window)
| Key | Action |
|-----|--------|
| `Ctrl+E` | Rotate device |
| `Ctrl+N` | New snapshot |
| `Ctrl+P` | Print window |
| `Ctrl+L` | Network speed |
| `F1-F12` | Volume controls |
| `Esc` | Kill window |

### ADB Fast Boot Testing
```bash
# Create quick test sequence
adb shell am start -n com.hermes.bridge/.MainActivity
adb shell dumpsys window windows | grep "mCurrentFocus"
```

---

## 📈 Expected Metrics

### Boot Time (from cold start)
- **Our config (optimized)**: 15-30 seconds
- **Standard config**: 60-120 seconds

### Memory Usage
- **Our config**: ~1.2-1.5 GB
- **Standard config**: ~2-3 GB

### App Launch Time
- From idle: < 500ms
- Cold start: < 2 seconds

### Overall Test Cycle
- Setup: 2-3 minutes (first time)
- Rebuild + reinstall: 30-60 seconds

---

## 🎉 Kết Luận

Với cấu hình emulator tối ưu này, bạn có thể:

✅ **Test nhanh hơn 5-10 lần** so với emulator mặc định  
✅ **Tiết kiệm tài nguyên hệ thống** (chỉ dùng 1.5GB thay vì 3GB)  
✅ **Boot cực nhanh** (15-30s thay vì 60-120s)  
✅ **Tự động hóa hoàn toàn** với script tích hợp sẵn  

**Chỉ cần một lệnh duy nhất:**
```bash
chuong_tay_test_automatic.bat
```

Và mọi thứ sẽ được xử lý tự động! 🚀

---

*Version: 1.0 | Last Updated: January 2025 | Status: Production Ready*
