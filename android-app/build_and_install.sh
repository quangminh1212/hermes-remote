#!/bin/bash

# Hermes Android Bridge - Build and Test Script

set -e

echo "========================================="
echo "🔨 Building Hermes Android Bridge"
echo "========================================="

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
print_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }
print_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }

# Check if running in the correct directory
if [ ! -f "build.gradle.kts" ]; then
    print_error "Please run this script from the android-app directory"
    exit 1
fi

# Step 1: Clean previous builds
print_info "Step 1/5: Cleaning previous builds..."
./gradlew clean --no-daemon

# Step 2: Run lint checks (optional, can be skipped)
print_info "Step 2/5: Running lint checks..."
if ./gradlew lint --no-daemon 2>&1 | tee /dev/null; then
    print_success "Lint checks passed"
else
    print_warn "Lint checks failed or skipped"
fi

# Step 3: Build debug APK
print_info "Step 3/5: Building Debug APK..."
./gradlew assembleDebug --no-daemon

# Step 4: Find the built APK
print_info "Step 4/5: Finding built APK..."
APK_PATH=$(find app/build/outputs/apk/debug -name "*.apk" -type f | head -n 1)

if [ -z "$APK_PATH" ]; then
    print_error "Failed to find built APK"
    exit 1
fi

print_success "APK found at: $APK_PATH"

# Step 5: Install on connected device/emulator
print_info "Step 5/5: Installing on device..."

# Check if ADB is available
if ! command -v adb &> /dev/null; then
    print_error "ADB not found. Please ensure Android SDK tools are installed."
    exit 1
fi

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    print_error "No device/emulator connected"
    exit 1
fi

# Install APK
adb install -r "$APK_PATH"

print_success "App installed successfully!"
print_info "Run on emulator/device: com.hermes.bridge.ui/.MainActivity"

echo ""
echo "========================================="
echo "✅ Build complete!"
echo "========================================="

# Show build info
print_info "Build information:"
echo "  - App ID: com.hermes.bridge"
echo "  - Version: $(grep versionName app/build.gradle.kts | head -1 | sed 's/.*versionName "\(.*\)"/\1/')"
echo "  - APK Size: $(du -h "$APK_PATH" | cut -f1)"
echo "  - Target API: 34"
echo "  - Min API: 26"

echo ""
print_success "Next steps:"
echo "1. Connect Android device or start emulator"
echo "2. Enable Accessibility Service in Settings"
echo "3. Launch app and configure WebSocket connection"
echo "4. Start connection and test commands"
