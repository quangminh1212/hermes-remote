# Build and Install Script for Hermes Android Bridge
# Requires: Android SDK, ADB, Java JDK

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "🔨 Building Hermes Android Bridge" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

$ErrorActionPreference = "Stop"

# Check if running in correct directory
if (-not (Test-Path "build.gradle.kts")) {
    Write-Error "Please run this script from the android-app directory"
    exit 1
}

# Step 1: Clean previous builds
Write-Host "`n[INFO] Step 1/5: Cleaning previous builds..." -ForegroundColor Blue
.\gradlew.bat clean --no-daemon

# Step 2: Build debug APK
Write-Host "[INFO] Step 2/5: Building Debug APK..." -ForegroundColor Blue
.\gradlew.bat assembleDebug --no-daemon

# Step 3: Find the built APK
Write-Host "[INFO] Step 3/5: Finding built APK..." -ForegroundColor Blue
$apkPath = Get-ChildItem -Path "app\build\outputs\apk\debug" -Filter "*.apk" | Select-Object -First 1 -ExpandProperty FullName

if (-not $apkPath) {
    Write-Error "Failed to find built APK"
    exit 1
}

Write-Host "`n[SUCCESS] APK found at: $apkPath" -ForegroundColor Green

# Step 4: Check if ADB is available
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Error "ADB not found. Please ensure Android SDK tools are installed."
    exit 1
}

# Step 5: Check if device is connected
$devices = adb devices | Where-Object { $_ -match 'device$' }
if (-not $devices) {
    Write-Error "No device/emulator connected"
    exit 1
}

Write-Host "[INFO] Step 4/5: Installing on device..." -ForegroundColor Blue

# Install APK
adb install -r $apkPath

Write-Host "`n[SUCCESS] App installed successfully!" -ForegroundColor Green
Write-Host "[INFO] Run on emulator/device: com.hermes.bridge.ui/.MainActivity" -ForegroundColor Cyan

Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host "✅ Build complete!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Cyan

# Show build information
$versionName = (Select-String -Pattern 'versionName "(.*)"' -Path "app\build.gradle.kts" | Select-Object -First 1).Matches.Groups[1].Value
$apksize = (Get-Item $apkPath).Length / 1MB

Write-Host "`n[INFO] Build information:" -ForegroundColor Blue
Write-Host "  - App ID:        com.hermes.bridge"
Write-Host "  - Version:       $versionName"
Write-Host "  - APK Size:      [Math]::Round($apksize, 2) MB"
Write-Host "  - Target API:    34"
Write-Host "  - Min API:       26"

Write-Host "`n[INFO] Next steps:" -ForegroundColor Cyan
Write-Host "1. Connect Android device or start emulator"
Write-Host "2. Enable Accessibility Service in Settings"
Write-Host "3. Launch app and configure WebSocket connection"
Write-Host "4. Start connection and test commands"
