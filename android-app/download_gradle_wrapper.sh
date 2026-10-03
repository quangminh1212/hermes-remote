#!/bin/bash
# Script to download Gradle wrapper jar file
# Run this after creating the gradle/wrapper directory

WRAPPER_DIR="gradle/wrapper"
JAR_FILE="$WRAPPER_DIR/gradle-wrapper.jar"
GRADLE_VERSION="8.14.5"

echo "Creating gradle wrapper directory..."
mkdir -p "$WRAPPER_DIR"

echo "Downloading Gradle $GRADLE_VERSION wrapper..."
WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v$GRADLE_VERSION.2-subprojects/performance/src/main/resources/org/gradle/wrapper/gradle-wrapper.jar"

if command -v curl &> /dev/null; then
    curl -L -o "$JAR_FILE" "$WRAPPER_URL"
elif command -v wget &> /dev/null; then
    wget -O "$JAR_FILE" "$WRAPPER_URL"
else
    echo "Error: Please install curl or wget"
    exit 1
fi

if [ -f "$JAR_FILE" ]; then
    echo "✅ Gradle wrapper downloaded successfully!"
    echo "You can now run: ./gradlew build"
else
    echo "❌ Failed to download Gradle wrapper"
    exit 1
fi
