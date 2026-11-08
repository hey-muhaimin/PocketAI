#!/usr/bin/env bash
set -euo pipefail

echo "=========================================================="
echo "PocketAI Environment Bootstrap"
echo "=========================================================="

# 1. Verify Java Installation
if command -v java >/dev/null 2>&1; then
    JAVA_VER=$(java -version 2>&1 | head -n 1)
    echo "✓ Java detected: $JAVA_VER"
else
    echo "✗ Error: Java is not installed or not in PATH."
    exit 1
fi

# 2. Check Android SDK Root
if [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "$ANDROID_SDK_ROOT" ]; then
    echo "✓ ANDROID_SDK_ROOT configured: $ANDROID_SDK_ROOT"
elif [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ]; then
    echo "✓ ANDROID_HOME configured: $ANDROID_HOME"
else
    echo "! Warning: Neither ANDROID_SDK_ROOT nor ANDROID_HOME is set in your shell."
    echo "  Ensure Android SDK command-line tools and platform 36 are installed."
fi

# 3. Create necessary local directories
mkdir -p build/outputs
mkdir -p app/build/outputs/apk/debug
mkdir -p app/build/outputs/apk/release
mkdir -p app/build/outputs/bundle/release

# 4. Set execution permissions for all scripts
chmod +x scripts/*.sh 2>/dev/null || true
if [ -f "gradlew" ]; then
    chmod +x gradlew
fi

echo "✓ PocketAI bootstrap completed successfully."
