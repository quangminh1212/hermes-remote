@echo off
REM ===================================================================
REM Create Android emu lator with lightest configuration for fast testing
REM Uses Pixel 4a without Google Play + x86_64 architecture
REM Optimized for minimal RAM usage and fastest startup time
REM ===================================================================

set EMU_NAME=HermesTestPixel4aLite
set TARGET=system-images;android-34;google_apis;x86_64
set AVD_CONFIG=low_ram

echo ====================================================================
echo 📱 Creating Lightest Android Emulator for Hermes Bridge Testing
echo ====================================================================
echo.

REM Check if avdmanager exists
where avdmanager >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ❌ avdmanager not found! Please set ANDROID_HOME environment variable
    echo Example: setx ANDROID_HOME "C:\Program Files (x86)\Android\android-sdk"
    pause
    exit /b 1
)

REM Check if SDK exists
if defined ANDROID_SDK_ROOT (
    set SDK_PATH=%ANDROID_SDK_ROOT%
) else if defined ANDROID_HOME (
    set SDK_PATH=%ANDROID_HOME%
) else (
    REM Try common locations
    if exist "C:\Program Files (x86)\Android\android-sdk" (
        set SDK_PATH="C:\Program Files (x86)\Android\android-sdk"
    ) else (
        echo ❌ Cannot find Android SDK
        echo Please install Android Studio or specify SDK path
        pause
        exit /b 1
    )
)

echo Found Android SDK at: %SDK_PATH%
echo.

echo Step 1: List existing emu lators...
%SDK_PATH%\cmdline-tools\latest\bin\avdmanager list avd 2>nul || %SDK_PATH%\tools\bin\avdmanager list avd 2>nul
echo.

echo Step 2: Delete existing emulator '%EMU_NAME%' if exists...
%SDK_PATH%\cmdline-tools\latest\bin\avdmanager delete avd -n %EMU_NAME% 2>nul || %SDK_PATH%\tools\bin\avdmanager delete avd -n %EMU_NAME% 2>nul
if %ERRORLEVEL% equ 0 (
    echo ✅ Deleted old emulator
) else (
    echo ℹ️  No existing emulator to remove
)
echo.

echo Step 3: Create new lightweight emulator...
echo Selecting configurations that minimize resource usage:
echo   • Pixel 4a device (small screen, ~5.8 inches)
echo   • Android 14 (API 34) latest system image
echo   • x86_64 architecture (fast CPU emulation)
echo   • No Google Play services (saves space)
echo   • Custom memory settings (optimized)
echo.

REM Create AVD with lite config
yes | %SDK_PATH%\cmdline-tools\latest\bin\avdmanager create avd ^
    --name %EMU_NAME% ^
    --package %TARGET% ^
    --device "pixel_4a" ^
    --abi x86_64

if %ERRORLEVEL% neq 0 (
    echo ⚠️  Trying alternative command...
    yes | %SDK_PATH%\tools\bin\avdmanager create avd ^
        --name %EMU_NAME% ^
        --package %TARGET% ^
        --device "pixel_4a" ^
        --abi x86_64
)

if %ERRORLEVEL% neq 0 (
    echo ❌ Failed to create emulator
    pause
    exit /b 1
)

echo ✅ Emulator created successfully!
echo.

echo Step 4: Optimizing emu lator for speed and low RAM usage...

REM Modify emulator.ini to add performance optimizations
set AVD_PATH="%APPDATA%\Local\Android\Avd\%EMU_NAME%.avd"
if not exist %AVD_PATH% (
    REM Try alternative location
    set AVD_PATH="%USERPROFILE%\AppData\Local\Android\Avd\%EMU_NAME%.avd"
)

if exist %AVD_PATH% (
    echo Modifying %EMU_NAME%.avd\config.ini with optimizations...
    
    REM Add performance optimizations to config.ini
    (type %AVD_PATH%\config.ini & echo hw.cpu.ncore=4 & echo ram.size=2048 & echo vm.heapSize=512 & echo disk.dataPartition.size=4G & echo gpu.status=no & echo hw.gpu.enabled=yes & echo hw.sensors.orientation=yes) > %AVD_PATH%\config.ini.tmp
    
    move /Y %AVD_PATH%\config.ini.tmp %AVD_PATH%\config.ini
    echo ✅ Optimizations applied
) else (
    echo ⚠️  Could not find config.ini to optimize
)

echo.
echo ====================================================================
echo ✅ DONE! Emulator ready for testing
echo ====================================================================
echo.
echo Next steps:
echo 1. Start emulator: %SDK_PATH%\emulator\emulator -avd %EMU_NAME% -no-audio
echo 2. Wait ~30 seconds for boot
echo 3. Install APK: adb install app-debug.apk
echo 4. Run tests: adb shell monkey -p com.hermes.bridge -c android.intent.category.LAUNCHER 1
echo.
echo To start automatically after creation:
echo.
echo @echo off^
echo start_server.bat^
echo echo Starting emulator...^
echo %SDK_PATH%\emulator\emulator -avd %EMU_NAME% -no-audio^
echo echo Waiting for boot...^
echo timeout /t 45^
echo adb install app\build\outputs\apk\debug\app-debug.apk^
echo adb shell monkey -p com.hermes.bridge -c android.intent.category.LAUNCHER 1
echo.
pause
