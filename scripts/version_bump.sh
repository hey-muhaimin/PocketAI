#!/usr/bin/env bash
set -euo pipefail

echo "=========================================================="
echo "PocketAI Semantic Version Bumper"
echo "=========================================================="

BUILD_FILE="app/build.gradle.kts"
if [ ! -f "$BUILD_FILE" ]; then
    echo "✗ Error: Cannot locate $BUILD_FILE"
    exit 1
fi

CURRENT_CODE=$(grep -oE "versionCode = [0-9]+" "$BUILD_FILE" | awk '{print $3}')
CURRENT_NAME=$(grep -oE 'versionName = "[^"]+"' "$BUILD_FILE" | cut -d'"' -f2)

NEW_CODE=$((CURRENT_CODE + 1))
echo "Current Version: $CURRENT_NAME (code: $CURRENT_CODE)"

NEW_NAME="${1:-}"
if [ -z "$NEW_NAME" ]; then
    # Auto-increment patch: e.g. 1.0 -> 1.1 or 1.0.0 -> 1.0.1
    MAJOR_MINOR=$(echo "$CURRENT_NAME" | cut -d'.' -f1-2)
    PATCH=$(echo "$CURRENT_NAME" | cut -d'.' -f3)
    if [ -z "$PATCH" ]; then
        NEW_NAME="${MAJOR_MINOR}.1"
    else
        NEW_NAME="${MAJOR_MINOR}.$((PATCH + 1))"
    fi
fi

echo "Updating to Version: $NEW_NAME (code: $NEW_CODE)"

# Replace in app/build.gradle.kts
sed -i "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" "$BUILD_FILE"
sed -i "s/versionName = \"$CURRENT_NAME\"/versionName = \"$NEW_NAME\"/" "$BUILD_FILE"

echo "✓ Successfully bumped $BUILD_FILE"
