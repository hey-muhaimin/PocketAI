#!/usr/bin/env bash
set -euo pipefail

echo "=========================================================="
echo "PocketAI Production Release Packaging (Signed AAB & APK)"
echo "=========================================================="

GRADLE_CMD="gradle"
if [ -f "./gradlew" ]; then
    GRADLE_CMD="./gradlew"
fi

# 1. Run unit test suite before release build
echo "--> Executing unit tests..."
$GRADLE_CMD :app:testDebugUnitTest

# 2. Build signed App Bundle (AAB for Google Play Store)
echo "--> Building production Release AAB (Android App Bundle)..."
$GRADLE_CMD :app:bundleRelease

# 3. Build signed Release APK
echo "--> Building production Release APK with R8 shrinking..."
$GRADLE_CMD :app:assembleRelease

echo "=========================================================="
echo "✓ Release Build Finished Successfully!"
echo "Outputs:"
find app/build/outputs/ -type f \( -name "*.apk" -o -name "*.aab" \) 2>/dev/null || true
echo "=========================================================="
