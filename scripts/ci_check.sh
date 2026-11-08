#!/usr/bin/env bash
set -euo pipefail

echo "=========================================================="
echo "PocketAI CI Verification Check"
echo "=========================================================="

GRADLE_CMD="gradle"
if [ -f "./gradlew" ]; then
    GRADLE_CMD="./gradlew"
fi

echo "--> 1. Running Unit Tests Suite..."
$GRADLE_CMD testDebugUnitTest --continue

echo "--> 2. Validating Debug Build Assembly..."
$GRADLE_CMD assembleDebug

echo "=========================================================="
echo "✓ All CI Checks Passed!"
echo "=========================================================="
