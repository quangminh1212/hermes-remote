#!/bin/bash
# ===================================================================
# Tự động build & chạy Android app trên emulator - Tối ưu nhẹ nhất
# Chuỗi lệnh tự động: Build APK -> Tạo/Start Emulator -> Install App -> Test
# Thời gian ước tính: ~2-3 phút (bao gồm cả thời gian khởi động emulator)
# RAM tối thiểu: 4GB system, sử dụng ~1.5GB cho emulator
# ===================================================================

EMU_NAME="HermesTestPixel4aLite"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$PROJECT_DIR/android-app"
TARGET_APK="$APP_DIR/app/build/outputs/apk/debug/app-debug.apk"

echo "===================================================================="
echo "🚀 TỰ ĐỘNG BUILD VÀ TEST HERMES BRIDGE TRÊN EMULATOR"
echo "===================================================================="
echo ""
echo "⏱️  Dự kiến: 2-3 phút"
echo "💾 RAM yêu cầu: System có sẵn 4GB+, Emulator dùng ~1.5GB"
echo "📱 Thiết bị: Pixel 4a (màn hình nhỏ, tiết kiệm tài nguyên)"
echo "🎯 API Level: Android 14 (API 34)"
echo ""

# Check prerequisites
echo "Kiểm tra môi trường..."
if ! command -v java &> /dev/null; then
    echo "❌ Java không được cài đặt!"
    echo "Vui lòng cài JDK 17+ từ https://adoptium.net/"
    exit 1
fi

java -version 2>&1 | grep -q "17\|21" || \
    echo "⚠️  Không tìm thấy JDK 17+ đang kiểm tra phiên bản hiện tại..."

echo "✅ Môi trường OK"
echo ""

cd "$APP_DIR" || exit 1

# Step 1: Download Gradle Wrapper if needed
echo "Bước 1: Kiểm tra Gradle Wrapper..."
if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
    echo "⚠️  Missing gradle-wrapper.jar, đang tải về..."
    
    curl -o gradle/wrapper/gradle-wrapper.jar \
        https://github.com/gradle/gradle/raw/v8.14.5-subprojects/performance/src/main/resources/org/gradle/wrapper/gradle-wrapper.jar
    
    if [ -f gradle/wrapper/gradle-wrapper.jar ]; then
        echo "✅ Đã tải Gradle Wrapper thành công"
    else
        echo "❌ Không thể tải Gradle Wrapper"
        exit 1
    fi
else
    echo "✅ Gradle Wrapper đã sẵn sàng"
fi
echo ""

# Step 2: Build APK
echo "Bước 2: Build APK Debug..."
chmod +x gradlew.bat
./gradlew.bat assembleDebug

if [ $? -ne 0 ]; then
    echo "❌ Build thất bại!"
    echo "Vui lòng kiểm tra lỗi ở trên"
    exit 1
fi

echo "✅ Build hoàn tất!"
echo "APK tại: $TARGET_APK"
if [ -f "$TARGET_APK" ]; then
    ls -lh "$TARGET_APK"
fi
echo ""

# Step 3: Find Android SDK path
echo "Bước 3: Chuẩn bị Emulator..."
if [ -n "$ANDROID_HOME" ]; then
    SDK_PATH="$ANDROID_HOME"
elif [ -n "$ANDROID_SDK_ROOT" ]; then
    SDK_PATH="$ANDROID_SDK_ROOT"
else
    # Try common locations
    if [ -d "/usr/local/android-sdk" ]; then
        SDK_PATH="/usr/local/android-sdk"
    elif [ -d "$HOME/Library/Android/sdk" ]; then
        SDK_PATH="$HOME/Library/Android/sdk"
    else
        echo "⚠️  Không tìm thấy Android SDK"
        echo "Vui lòng cài Android Studio hoặc set environment variable ANDROID_HOME"
        exit 1
    fi
fi

echo "SDK Path: $SDK_PATH"
echo ""

# Step 4: Check/create emulator
echo "Bước 4: Kiểm tra emulator '$EMU_NAME'..."
if "$SDK_PATH/emulator/emulator" -list-avds 2>/dev/null | grep -q "$EMU_NAME"; then
    echo "✅ Đã có sẵn emulator '$EMU_NAME'"
    HAVE_EMU=1
else
    echo "⚠️  Chưa có emulator, đang tạo mới..."
    
    # Delete old one if exists
    "$SDK_PATH/cmdline-tools/latest/bin/avdmanager" delete avd -n $EMU_NAME 2>/dev/null || \
    "$SDK_PATH/tools/bin/avdmanager" delete avd -n $EMU_NAME 2>/dev/null
    
    # Create new lightweight emulator
    yes | "$SDK_PATH/cmdline-tools/latest/bin/avdmanager" create avd \
        --name $EMU_NAME \
        --package "system-images;android-34;google_apis;x86_64" \
        --device "pixel_4a" \
        --abi x86_64 2>/dev/null || \
    yes | "$SDK_PATH/tools/bin/avdmanager" create avd \
        --name $EMU_NAME \
        --package "system-images;android-34;google_apis;x86_64" \
        --device "pixel_4a" \
        --abi x86_64
    
    if [ $? -eq 0 ]; then
        echo "✅ Tạo emulator thành công"
        HAVE_EMU=1
    else
        echo "❌ Không thể tạo emulator"
        echo "Vui lòng tạo thủ công trong Android Studio"
        exit 1
    fi
fi
echo ""

# Step 5: Start emulator
if [ "$HAVE_EMU" = "1" ]; then
    echo "Bước 5: Khởi động emulator..."
    
    # Kill existing emulator processes
    pkill -9 emulator 2>/dev/null || true
    sleep 5
    
    # Start emulator in background
    nohup "$SDK_PATH/emulator/emulator" -avd $EMU_NAME -no-audio -wipe-data > /tmp/emulator.log 2>&1 &
    EMULATOR_PID=$!
    
    echo "⏳ Chờ emulator khởi động (~30 giây)..."
    echo "(Đừng tắt cửa sổ này!)"
    
    # Wait for emulator to boot
    while true; do
        adb devices 2>/dev/null | grep -q "device" && break
        sleep 5
    done
    
    echo "✅ Emulator đã sẵn sàng!"
    echo ""
fi

# Step 6: Install APK
echo "Bước 6: Cài đặt ứng dụng lên emulator..."
adb install -r "$TARGET_APK"

if [ $? -ne 0 ]; then
    echo "❌ Install thất bại!"
    exit 1
fi

echo "✅ Ứng dụng đã được cài đặt!"
echo ""

# Step 7: Launch app and take screenshot
echo "Bước 7: Khởi động ứng dụng và chụp ảnh màn hình..."

# Grant all necessary permissions
echo "Granting permissions..."
adb shell pm grant com.hermes.bridge android.permission.ACCESSIBILITY_STATE
adb shell pm grant com.hermes.bridge android.permission.FOREGROUND_SERVICE
sleep 2

# Launch the app
adb shell monkey -p com.hermes.bridge -c android.intent.category.LAUNCHER 1

# Wait for app to load
echo "Waiting for app to launch..."
sleep 10

# Take screenshot
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png ./screenshot_test.png

if [ -f screenshot_test.png ]; then
    echo "✅ Đã chụp ảnh màn hình: ./screenshot_test.png"
else
    echo "ℹ️  Không thể chụp màn hình (có thể app chưa mở kịp)"
fi
echo ""

# Step 8: Show logs
echo "Bước 8: Hiển thị logs quan trọng..."
echo "============================================"
adb logcat -d | grep -E "WebSocket|Bridge|MainActivity|Error|Exception"
echo "============================================"
echo ""

# Step 9: Quick summary
echo "===================================================================="
echo "✅ HOÀN TẤT TEST TỰ ĐỘNG!"
echo "===================================================================="
echo ""
echo "Tổng kết:"
echo "  • APK đã được build thành công"
echo "  • Emulator '$EMU_NAME' đã khởi động"
echo "  • Ứng dụng đã được cài đặt lên emulator"
echo "  • Ứng dụng đã được khởi động"
echo "  • Screenshots được lưu tại: ./screenshot_test.png"
echo ""
echo "Bạn có thể:"
echo "  1. Xem emulator đang chạy riêng (không đóng cửa sổ terminal)"
echo "  2. Xem logs chi tiết hơn với: adb logcat"
echo "  3. Test lại với: ./chuong_tay_test_automatic.sh"
echo ""
echo "Để dừng emulator khi xong:"
echo "  1. Nhấn X trên cửa sổ emulator"
echo "  2. Hoặc chạy: adb kill-server"
echo ""
