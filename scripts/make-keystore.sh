#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [ -f release.jks ]; then
    echo "release.jks already exists; refusing to overwrite it." >&2
    exit 1
fi

if ! command -v keytool >/dev/null 2>&1; then
    echo "keytool not found; set JAVA_HOME to a JDK 17 install." >&2
    exit 1
fi

generated=""
if [ -z "${KEYSTORE_PASSWORD:-}" ]; then
    generated="$(openssl rand -hex 16)"
    KEYSTORE_PASSWORD="$generated"
fi
if [ -z "${KEY_PASSWORD:-}" ]; then
    KEY_PASSWORD="${generated:-$KEYSTORE_PASSWORD}"
fi

keytool -genkeypair -v \
    -keystore release.jks \
    -alias aibrowser \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "CN=AiBrowser" >/dev/null

umask 077
cat > keystore.properties <<EOF
storeFile=release.jks
storePassword=$KEYSTORE_PASSWORD
keyAlias=aibrowser
keyPassword=$KEY_PASSWORD
EOF
chmod 600 keystore.properties

if [ -n "$generated" ]; then
    echo "Generated password (store and key are the same); print once, keep it safe:"
    echo "$generated"
fi

echo "Created release.jks and keystore.properties at the repository root."