#!/usr/bin/env bash
set -euo pipefail

echo "=========================================================="
echo "PocketAI Production Release Keystore Generator"
echo "=========================================================="

KEYSTORE_FILE="${1:-my-upload-key.jks}"
KEY_ALIAS="${2:-upload}"
VALIDITY_DAYS=10000

if [ -f "$KEYSTORE_FILE" ]; then
    echo "! Warning: Keystore '$KEYSTORE_FILE' already exists."
    read -rp "Overwrite existing keystore? (y/N): " CONFIRM
    if [[ "$CONFIRM" != "y" && "$CONFIRM" != "Y" ]]; then
        echo "Aborted."
        exit 0
    fi
fi

# Prompt or generate secure password
STORE_PASS="${STORE_PASSWORD:-}"
if [ -z "$STORE_PASS" ]; then
    read -rsp "Enter Keystore & Key Password (min 6 chars): " STORE_PASS
    echo ""
fi

keytool -genkeypair \
    -v \
    -keystore "$KEYSTORE_FILE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity "$VALIDITY_DAYS" \
    -storepass "$STORE_PASS" \
    -keypass "$STORE_PASS" \
    -dname "CN=PocketAI Release, OU=Mobile Security, O=PocketAI, L=San Francisco, ST=California, C=US"

echo "✓ Keystore generated at: $KEYSTORE_FILE"

# Generate or update keystore.properties
cat <<EOF > keystore.properties
KEYSTORE_PATH=$KEYSTORE_FILE
KEY_ALIAS=$KEY_ALIAS
STORE_PASSWORD=$STORE_PASS
KEY_PASSWORD=$STORE_PASS
EOF

echo "✓ Created keystore.properties configuration file."
echo "CRITICAL: Do NOT commit keystore.properties or .jks files to public version control."
