@echo off
REM Script to download Gradle wrapper jar file for Windows
REM Run this after creating the gradle/wrapper directory

set WRAPPER_DIR=gradle\wrapper
set JAR_FILE=%WRAPPER_DIR%\gradle-wrapper.jar
set GRADLE_VERSION=8.14.5

echo Creating gradle wrapper directory...
if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"

echo Downloading Gradle %GRADLE_VERSION% wrapper...

REM Try PowerShell first
powershell -Command "Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/gradle/gradle/v%GRADLE_VERSION%.2-subprojects/performance/src/main/resources/org/gradle/wrapper/gradle-wrapper.jar' -OutFile '%JAR_FILE%'"

if exist "%JAR_FILE%" (
    echo.
    echo ✅ Gradle wrapper downloaded successfully!
    echo You can now run: gradlew.bat build
) else (
    echo.
    echo ❌ Failed to download Gradle wrapper
    echo.
    echo Manual download option:
    echo 1. Visit: https://github.com/gradle/gradle/raw/v%GRADLE_VERSION%.2-subprojects/performance/src/main/resources/org/gradle/wrapper/gradle-wrapper.jar
    echo 2. Save as: %CD%\%JAR_FILE%
    exit /b 1
)
