@echo off
REM ===================================================================
REM Tự động build & chạy Android app trên emu lator - Tối ưu nhẹ nhất
REM Chuỗi lệnh tự động: Build APK -> Tạo/Start Emulator -> Install App -> Test
REM Thời gian ước tính: ~2-3 phút (bao gồm cả thời gian khởi động emulator)
REM RAM tối thiểu: 4GB system, sử dụng ~1.5GB cho emulator
REM ===================================================================

set EMU_NAME=HermesTestPixel4aLite
set PROJECT_DIR=%CD%
set APP_DIR=%PROJECT_DIR%\android-app
set TARGET_APK=%APP_DIR%\app\build\outputs\apk\debug\app-debug.apk

echo ====================================================================
echo 🚀 TỰ ĐỘNG BUILD VÀ TEST HERMES BRIDGE TRÊN EMULATOR
echo ====================================================================
echo.
echo ⏱️  Dự kiến: 2-3 phút
echo 💾 RAM yêu cầu: System có sẵn 4GB+, Emulator dùng ~1.5GB
echo 📱 Thiết bị: Pixel 4a (màn hình nhỏ, tiết kiệm tài nguyên)
echo 🎯 API Level: Android 14 (API 34)
echo.

REM Check prerequisites
echo Kiểm tra môi trường...
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ❌ Java không được cài đặt!
    echo Vui lòng cài JDK 17+ từ https://adoptium.net/
    pause
    exit /b 1
)

java --version 2>&1 | findstr "17\|21" >nul
if %ERRORLEVEL% neq 0 (
    echo ⚠️  Không tìm thấy JDK 17+, đang kiểm tra phiên bản hiện tại...
    java -version 2>&1 | head -1
)

echo ✅ Môi trường OK
echo.

cd %APP_DIR%

REM Step 1: Download Gradle Wrapper nếu cần
echo Bước 1: Kiểm tra Gradle Wrapper...
if not exist gradle\wrapper\gradle-wrapper.jar (
    echo ⚠️  Missing gradle-wrapper.jar, đang tải về...
    
    REM Try PowerShell download first
    powershell -Command "Invoke-WebRequest -Uri 'https://github.com/gradle/gradle/raw/v8.14.5-subprojects/performance/src/main/resources/org/gradle/wrapper/gradle-wrapper.jar' -OutFile 'gradle\wrapper\gradle-wrapper.jar'"
    
    if exist gradle\wrapper\gradle-wrapper.jar (
        echo ✅ Đã tải Gradle Wrapper thành công
    ) else (
        echo ❌ Không thể tải Gradle Wrapper
        echo Manual: Tải từ link trên và lưu vào: android-app\gradle\wrapper\gradle-wrapper.jar
        pause
        exit /b 1
    )
) else (
    echo ✅ Gradle Wrapper đã sẵn sàng
)
echo.

REM Step 2: Build APK
echo Bước 2: Build APK Debug...
call gradlew.bat assembleDebug

if %ERRORLEVEL% neq 0 (
    echo ❌ Build thất bại!
    echo Vui lòng kiểm tra lỗi ở trên
    pause
    exit /b 1
)

echo ✅ Build hoàn tất!
echo APK tại: %TARGET_APK%
if exist %TARGET_APK% (
    dir %TARGET_APK%
)
echo.

REM Step 3: Tìm Android SDK path
echo Bước 3: Chuẩn bị Emulator...
if defined ANDROID_HOME (
    set SDK_PATH=%ANDROID_HOME%
) else if defined ANDROID_SDK_ROOT (
    set SDK_PATH=%ANDROID_SDK_ROOT%
) else (
    if exist "C:\Program Files (x86)\Android\android-sdk" (
        set SDK_PATH="C:\Program Files (x86)\Android\android-sdk"
    ) else if exist "%USERPROFILE%\AppData\Local\Android\Sdk" (
        set SDK_PATH="%USERPROFILE%\AppData\Local\Android\Sdk"
    ) else (
        echo ⚠️  Không tìm thấy Android SDK
        echo Đang tìm kiếm trong các thư mục phổ biến...
        
        REM Try PowerShell to find SDK
        powershell -Command "Get-ChildItem -Path 'C:\' -Filter 'android-sdk' -Directory -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1 | ForEach-Object { $_.FullName }" > sdk_path.txt
        
        if exist sdk_path.txt (
            set /p SDK_PATH=<sdk_path.txt
            del sdk_path.txt
            echo Found: %SDK_PATH%
        ) else (
            echo ❌ Không tìm thấy Android SDK
            echo Vui lòng cài Android Studio hoặc set environment variable ANDROID_HOME
            pause
            exit /b 1
        )
    )
)

echo SDK Path: %SDK_PATH%
echo.

REM Step 4: Kiểm tra/emulator có sẵn chưa
echo Bước 4: Kiểm tra emulator '%EMU_NAME%'...
%SDK_PATH%\emulator\emulator.exe -list-avds > avd_list.txt 2>&1
findstr /i "%EMU_NAME%" avd_list.txt >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo ✅ Đã có sẵn emulator '%EMU_NAME%'
    set HAVE_EMU=1
) else (
    echo ⚠️  Chưa có emulator, đang tạo mới...
    
    REM Delete old one if exists
    %SDK_PATH%\cmdline-tools\latest\bin\avdmanager delete avd -n %EMU_NAME% 2>nul || \
    %SDK_PATH%\tools\bin\avdmanager delete avd -n %EMU_NAME% 2>nul
    
    REM Create new lightweight emulator
    yes | %SDK_PATH%\cmdline-tools\latest\bin\avdmanager create avd ^
        --name %EMU_NAME% ^
        --package "system-images;android-34;google_apis;x86_64" ^
        --device "pixel_4a" ^
        --abi x86_64 2>nul || \
    yes | %SDK_PATH%\tools\bin\avdmanager create avd ^
        --name %EMU_NAME% ^
        --package "system-images;android-34;google_apis;x86_64" ^
        --device "pixel_4a" ^
        --abi x86_64
    
    if %ERRORLEVEL% equ 0 (
        echo ✅ Tạo emulator thành công
        set HAVE_EMU=1
    ) else (
        echo ❌ Không thể tạo emulator
        echo Vui lòng tạo thủ công trong Android Studio
        pause
        exit /b 1
    )
)
del avd_list.txt
echo.

REM Step 5: Start emulator
if %HAVE_EMU% equ 1 (
    echo Bước 5: Khởi động emulator...
    
    REM Kill existing emulator processes
    taskkill /F /IM emulator.exe 2>nul
    timeout /t 5 /nobreak >nul
    
    START "Emulator" /MIN %SDK_PATH%\emulator\emulator.exe -avd %EMU_NAME% -no-audio -wipe-data
    
    echo ⏳ Chờ emulator khởi động (~30 giây)...
    echo (Đừng tắt cửa sổ này!)
    
    REM Wait for emulator to boot
    :WAIT_BOOT
    timeout /t 5 /nobreak >nul
    adb devices | findstr "device" >nul
    if %ERRORLEVEL% neq 0 goto WAIT_BOOT
    
    echo ✅ Emulator đã sẵn sàng!
    echo.
)

REM Step 6: Install APK
echo Bước 6: Cài đặt ứng dụng lên emulator...
adb install -r %TARGET_APK%

if %ERRORLEVEL% neq 0 (
    echo ❌ Install thất bại!
    pause
    exit /b 1
)

echo ✅ Ứng dụng đã được cài đặt!
echo.

REM Step 7: Launch app and take screenshot
echo Bước 7: Khởi động ứng dụng và chụp ảnh màn hình...

REM Grant all necessary permissions
echo Granting permissions...
adb shell pm grant com.hermes.bridge android.permission.ACCESSIBILITY_STATE
adb shell pm grant com.hermes.bridge android.permission.FOREGROUND_SERVICE
timeout /t 2 /nobreak >nul

REM Launch the app
adb shell monkey -p com.hermes.bridge -c android.intent.category.LAUNCHER 1

REM Wait for app to load
echoWaiting for app to launch...
timeout /t 10 /nobreak >nul

REM Take screenshot
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png .\screenshot_test.png

if exist screenshot_test.png (
    echo ✅ Đã chụp ảnh màn hình: .\screenshot_test.png
) else (
    echo ℹ️  Không thể chụp màn hình (có thể app chưa mở kịp)
)
echo.

REM Step 8: Show logs
echo Bước 8: Hiển thị logs quan trọng...
echo ============================================
adb logcat -d | findstr /C:"WebSocket" /C:"Bridge" /C:"MainActivity" /C:"Error" /C:"Exception"
echo ============================================
echo.

REM Step 9: Quick summary
echo ====================================================================
echo ✅ HOÀN TẤT TEST TỰ ĐỘNG!
echo ====================================================================
echo.
echo Tổng kết:
echo   • APK đã được build thành công
echo   • Emulator '%EMU_NAME%' đã khởi động
echo   • Ứng dụng đã được cài đặt lên emulator
echo   • Ứng dụng đã được khởi động
echo   • Screenshots được lưu tại: .\screenshot_test.png
echo.
echo Bạn có thể:
echo  1. Xem emulator đang chạy riêng (không đóng cửa sổ cmd)
echo  2. Xem logs chi tiết hơn với: adb logcat
echo  3. Test lại với: chuong_tay_test_automatic.bat
echo.
echo Để dừng emulator khi xong:
echo  1. Nhấn X trên cửa sổ emulator
echo  2. Hoặc chạy: adb kill-server
echo.
pause
