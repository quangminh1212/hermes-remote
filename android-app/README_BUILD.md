# 🚀 HƯỚNG DẪN BUILD & TEST ANDROID APP

## 📋 Yêu Cầu Hệ Thống

### Cần cài đặt trước:
1. **Android Studio Arctic Fox (2020.3.1)** hoặc mới hơn
   - [Download Android Studio](https://developer.android.com/studio)

2. **JDK 17** hoặc cao hơn
   ```bash
   java --version  # Kiểm tra
   ```

3. **Gradle 8.14.5+** (cập nhật trong gradle-wrapper.properties)

4. **Android SDK** với:
   - Minimum SDK: API 23 (Android 6.0)
   - Target SDK: API 34 (Android 14)
   - Build Tools: 34.0.0

---

## 🔧 Setup ban đầu

### Bước 1: Tạo local.properties
```cmd
cd C:\Dev\Hermes_Android\android-app

REM Copy template và sửa đường dẫn cho phù hợp
copy local.properties.template local.properties

REM Edit local.properties để set Android SDK path của bạn
notepad local.properties
```

Ví dụ Windows:
```properties
sdk.dir=C:\\Android\\SDK
```

Ví dụ Linux/Mac:
```properties
sdk.dir=/home/username/Library/Android/sdk
```

### Bước 2: Download Gradle Wrapper JAR
```cmd
REM Method 1: Chạy script tự động
download_gradle_wrapper.bat

REM Method 2: Manual download
REM Tải từ: https://github.com/gradle/gradle/raw/v8.14.5-subprojects/performance/src/main/resources/org/gradle/wrapper/gradle-wrapper.jar
REM Lưu vào: gradle\wrapper\gradle-wrapper.jar
```

### Bước 3: Import Project vào Android Studio

1. Mở Android Studio
2. Chọn `File` → `New` → `Project from Existing Sources...`
3. Chọn thư mục: `C:\Dev\Hermes_Android\android-app`
4. Chọn `Kotlin` khi được hỏi language
5. Đợi Gradle sync finish ⏳

---

## 🏗️ Build App

### Cách 1: Từ Android Studio
1. Chọn menu `Build` → `Make Project` (hoặc F9)
2. Đợi build complete ✅
3. APK nằm tại: `app/build/outputs/apk/debug/app-debug.apk`

### Cách 2: Từ Command Line
```cmd
cd C:\Dev\Hermes_Android\android-app

REM Clean build
gradlew.bat clean

REM Debug build
gradlew.bat assembleDebug

Rem Release build (cần signing config)
gradlew.bat assembleRelease
```

### Cách 3: Dùng terminal tích hợp
```bash
./gradlew build              # Build + run tests
./gradlew assemble           # Build APK only
./gradlew connectedAndroidTest  # Run instrumentation tests
```

---

## 📱 Install APK

### Qua USB Connection
```cmd
adb devices  # Kết nối device/emulator

adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Từ Android Studio
1. Đảm bảo device/emulator đang kết nối
2. Nhấn nút `Run` (▶️) trên toolbar
3. Chọn target device → OK

### Transfer thủ công
```cmd
REM Gửi file qua mạng LAN hoặc cloud storage
REM Sau đó mở file manager trên điện thoại → chọn file .apk → Install
```

---

## 🔍 Debug & Testing

### Xem Logs
```cmd
adb logcat | findstr "WebSocket|Bridge|MainActivity"
```

### Clear Data trước khi test
```cmd
adb shell pm clear com.hermes.bridge
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Force Stop
```cmd
adb shell am force-stop com.hermes.bridge
```

### Kiểm tra permissions
```cmd
adb shell appops get com.hermes.bridge ACCESSIBILITY_STATE
```

---

## 🐛 Troubleshooting

### Error: "SDK location not found"
**Giải pháp:** Set `ANDROID_HOME` environment variable
```cmd
setx ANDROID_HOME "C:\Program Files (x86)\Android\android-sdk"
```

### Error: "BUILD FAILED"
**Kiểm tra:**
- JDK version phù hợp (17+)
- Android SDK components đã cài đủ
- local.properties có đúng đường dẫn

### Error: "Gradle wrapper not found"
**Giải pháp:** 
```cmd
REM Tái tạo gradle wrapper
gradle wrapper
```

### Gradle sync timeout
**Tăng timeout:**
```properties
// Trong gradle.properties
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.configureondemand=true
```

### Memory allocation issues
```properties
// Trong gradle.properties
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=512m
```

---

## 🎯 Test Checklist

### Pre-build Checks:
- [ ] Android SDK installed
- [ ] JDK 17+ installed and configured
- [ ] local.properties created with correct SDK path
- [ ] Gradle wrapper JAR downloaded
- [ ] Project imports successfully into Android Studio

### Post-build Checks:
- [ ] APK generated without errors
- [ ] File size reasonable (<20MB for debug)
- [ ] No lint warnings blocking build
- [ ] All tests pass (`./gradlew test`)

### Runtime Tests:
- [ ] App installs on device/emulator
- [ ] App opens without crashing
- [ ] Accessibility service can be enabled
- [ ] Can configure WebSocket URL
- [ ] Connection establishes successfully
- [ ] Pairing code authenticates correctly
- [ ] Commands execute properly (tap, type, swipe)

---

## 📊 Performance Metrics

### Expected Build Times:
- **First build:** 5-10 minutes (downloading dependencies)
- **Subsequent builds:** 1-2 minutes (incremental)
- **Clean rebuild:** 3-5 minutes

### APK Sizes:
- **Debug:** ~8-12 MB
- **Release (optimized):** ~3-5 MB

### App Performance:
- **Cold start:** <2 seconds
- **Main screen render:** <500ms  
- **WebSocket connection:** <1 second
- **Command latency:** 100-300ms

---

## 🔐 Signing for Release

### Create Keystore
```cmd
keytool -genkeypair ^
  -v ^
  -keystore hermes-bridge.keystore ^
  -alias hermes-bridge ^
  -keyalg RSA ^
  -keysize 2048 ^
  -validity 10000 ^
  -storepass your-password ^
  -keypass your-password
```

### Configure signing in app/build.gradle.kts
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../hermes-bridge.keystore")
            storePassword = System.getenv("STORE_PASSWORD")
            keyAlias = "hermes-bridge"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

---

## 🚀 Quick Start Summary

```bash
# 1. Setup
cd android-app
copy local.properties.template local.properties
edit local.properties  # Set your SDK path

# 2. Download Gradle wrapper
download_gradle_wrapper.bat

# 3. Build
gradlew.bat assembleDebug

# 4. Install to device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 5. Run!
adb shell monkey -p com.hermes.bridge -c android.intent.category.LAUNCHER 1
```

---

## 💡 Tips

1. **Enable Gradle Daemon** - speeds up incremental builds
2. **Use Android Profiler** - monitor memory/CPU in real-time
3. **Check ADB Forwarding** - `adb forward --list`
4. **Use Emulators wisely** - x86_64 emulators faster than ARM
5. **Keep cache updated** - Invalidate caches when experiencing weird issues

---

**Happy Building! 🎉**

*Version: 1.0 | Last Updated: January 2025*
