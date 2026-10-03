#!/bin/bash
# ===================================================================
# Create Android emulator with lightest configuration for fast testing
# Uses Pixel 4a without Google Play + x86_64 architecture
# Optimized for minimal RAM usage and fastest startup time
# ===================================================================

EMU_NAME="HermesTestPixel4aLite"
TARGET="system-images;android-34;google_apis;x86_64"

echo "===================================================================="
echo "📱 Creating Lightest Android Emulator for Hermes Bridge Testing"
echo "===================================================================="
echo ""

# Check if avdmanager exists
if ! command -v avdmanager &> /dev/null; then
    echo "❌ avdmanager not found!"
    echo "Please set ANDROID_HOME or install Android SDK"
    exit 1
fi

# Find Android SDK
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
        echo "❌ Cannot find Android SDK"
        echo "Please install Android Studio or specify SDK path"
        exit 1
    fi
fi

echo "Found Android SDK at: $SDK_PATH"
echo ""

echo "Step 1: List existing emulators..."
$SDK_PATH/cmdline-tools/latest/bin/avdmanager list avd 2>/dev/null || $SDK_PATH/tools/bin/avdmanager list avd 2>/dev/null
echo ""

echo "Step 2: Delete existing emulator '$EMU_NAME' if exists..."
$SDK_PATH/cmdline-tools/latest/bin/avdmanager delete avd -n $EMU_NAME 2>/dev/null || \
$SDK_PATH/tools/bin/avdmanager delete avd -n $EMU_NAME 2>/dev/null
if [ $? -eq 0 ]; then
    echo "✅ Deleted old emulator"
else
    echo "ℹ️  No existing emulator to remove"
fi
echo ""

echo "Step 3: Create new lightweight emulator..."
echo "Selecting configurations that minimize resource usage:"
echo "  • Pixel 4a device (small screen, ~5.8 inches)"
echo "  • Android 14 (API 34) latest system image"
echo "  • x86_64 architecture (fast CPU emulation)"
echo "  • No Google Play services (saves space)"
echo "  • Custom memory settings (optimized)"
echo ""

# Create AVD with lite config
yes | $SDK_PATH/cmdline-tools/latest/bin/avdmanager create avd \
    --name $EMU_NAME \
    --package $TARGET \
    --device "pixel_4a" \
    --abi x86_64

if [ $? -ne 0 ]; then
    echo "⚠️  Trying alternative command..."
    yes | $SDK_PATH/tools/bin/avdmanager create avd \
        --name $EMU_NAME \
        --package $TARGET \
        --device "pixel_4a" \
        --abi x86_64
fi

if [ $? -ne 0 ]; then
    echo "❌ Failed to create emulator"
    exit 1
fi

echo "✅ Emulator created successfully!"
echo ""

echo "Step 4: Optimizing emulator for speed and low RAM usage..."

# Modify emulator.ini to add performance optimizations
AVD_PATH="$HOME/.android/avd/$EMU_NAME.avd"
if [ ! -d "$AVD_PATH" ]; then
    # Try alternative location
    AVD_PATH="$HOME/.local/share/android-emulator/avd/$EMU_NAME.avd"
fi

if [ -d "$AVD_PATH" ]; then
    echo "Modifying $EMU_NAME.avd/config.ini with optimizations..."
    
    # Add performance optimizations to config.ini
    {
        cat "$AVD_PATH/config.ini"
        echo "hw.cpu.ncore=4"
        echo "ram.size=2048"
        echo "vm.heapSize=512"
        echo "disk.dataPartition.size=4G"
        echo "gpu.status=no"
        echo "hw.gpu.enabled=yes"
        echo "hw.sensors.orientation=yes"
    } > "$AVD_PATH/config.ini.tmp"
    
    mv "$AVD_PATH/config.ini.tmp" "$AVD_PATH/config.ini"
    echo "✅ Optimizations applied"
else
    echo "⚠️  Could not find config.ini to optimize"
fi

echo ""
echo "===================================================================="
echo "✅ DONE! Emulator ready for testing"
echo "===================================================================="
echo ""
echo "Next steps:"
echo "1. Start emulator: $SDK_PATH/emulator/emulator -avd $EMU_NAME -no-audio"
echo "2. Wait ~30 seconds for boot"
echo "3. Install APK: adb install app-debug.apk"
echo "4. Run tests: adb shell monkey -p com.hermes.bridge -c android.intent.category.LAUNCHER 1"
echo ""
echo "To start automatically after creation:"
echo ""
cat << 'EOFSTARTUP'
#!/bin/bash
# Automatic build and test script
cd android-app

# Download Gradle wrapper if needed
if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
    curl -o gradle/wrapper/gradle-wrapper.jar \
        https://github.com/gradle/gradle/raw/v8.14.5-subprojects/performance/src/main/resources/org/gradle/wrapper/gradle-wrapper.jar
fi

# Build debug APK
./gradlew.bat assembleDebug

# Start Python server in background
../start_server.bat &

# Start emulator
$SDK_PATH/emulator/emulator -avd $EMU_NAME -no-audio

# Wait for boot
echo "Waiting for emulator to boot..."
while ! adb devices | grep -q "device"; do
    sleep 5
done
echo "✅ Emulator ready!"

# Install app
adb install app/build/outputs/apk/debug/app-debug.apk

# Launch app
adb shell monkey -p com.hermes.bridge -c android.intent.category.LAUNCHER 1

echo "🚀 App launched! Monitor logs with: adb logcat"
EOFSTARTUP
echo ""
