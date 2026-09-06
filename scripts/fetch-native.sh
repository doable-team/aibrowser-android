#!/usr/bin/env bash
#
# Fetch the native binaries the app runs (proot, busybox, tar and their
# runtime libraries) and place them under app/src/main/jniLibs/arm64-v8a/.
#
# The binaries are taken from the Pocket Trilium v1.5.0 release APK, which
# ships builds from termux-packages (see THIRD_PARTY.md for provenance and
# licences). Every file is verified against the SHA-256 prefix listed in
# THIRD_PARTY.md; the script fails rather than extracting something wrong.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

APK_URL="https://github.com/nriver/pocket-trilium/releases/download/v1.5.0/pocket-trilium_1.5.0_20260828_01-arm64-v8a-release.apk"
CACHE_DIR="$SCRIPT_DIR/cache"
APK="$CACHE_DIR/pocket-trilium_1.5.0_20260828_01-arm64-v8a-release.apk"
JNI_DIR="$REPO_ROOT/app/src/main/jniLibs/arm64-v8a"

# name sha256-prefix (first 16 hex chars), as listed in THIRD_PARTY.md
FILES=(
  "libexec_proot.so 8a03d1da568e2117"
  "libproot-loader.so b5b856b8b0d00d88"
  "libexec_busybox.so 527f2ede5721d062"
  "libbusybox.so cae8a362b0e63ce2"
  "libexec_tar.so a755d420143dafb8"
  "libtalloc.so ea1092e4a755589b"
  "libacl.so 61bac9778cd70979"
  "libattr.so c83e3a340c931625"
  "libandroid-selinux.so e5918c3606edc64d"
  "libiconv.so bbd84d42f56c709d"
  "libcharset.so 03dc14680e9cfdef"
  "libpcre2-8.so 2338a513735e3b58"
)

mkdir -p "$CACHE_DIR" "$JNI_DIR"

if [[ -f "$APK" ]]; then
  echo "Using cached APK: $APK"
else
  echo "Downloading $APK_URL"
  curl -fL --retry 3 -o "$APK" "$APK_URL"
fi

echo "Extracting ${#FILES[@]} files from lib/arm64-v8a/ ..."
extracted=0
for entry in "${FILES[@]}"; do
  read -r name prefix <<<"$entry"
  dest="$JNI_DIR/$name"
  unzip -o -q "$APK" "lib/arm64-v8a/$name" -d "$CACHE_DIR/stage" || {
    echo "FAILED: $name not found inside $APK (lib/arm64-v8a/$name)" >&2
    exit 1
  }
  staged="$CACHE_DIR/stage/lib/arm64-v8a/$name"
  actual="$(sha256sum "$staged" | awk '{print $1}')"
  if [[ "${actual:0:16}" != "$prefix" ]]; then
    echo "FAILED: $name sha256 is ${actual:0:16}..., expected $prefix" >&2
    exit 1
  fi
  mv -f "$staged" "$dest"
  chmod 755 "$dest"
  extracted=$((extracted + 1))
done
rm -rf "$CACHE_DIR/stage"

echo "----"
echo "fetch-native.sh summary:"
echo "  APK:        $APK"
echo "  jniLibs:    $JNI_DIR"
echo "  files:      $extracted of ${#FILES[@]} extracted and verified"