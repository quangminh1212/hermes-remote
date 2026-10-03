## System Requirements
- Android 8.0+ (API 26+)
- Internet permission
- Accessibility service access

## Installation

1. Build APK in Android Studio or via Gradle
2. Install to device: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. Enable Accessibility Service in device settings
4. Configure connection URL in app
5. Start connection and enjoy!

## Testing
Run unit tests:
```bash
./gradlew test
```

Run instrumentation tests:
```bash
./gradlew connectedAndroidTest
```
