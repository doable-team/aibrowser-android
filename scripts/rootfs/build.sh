#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OVERLAY="$ROOT/rootfs-overlay"
OUT="$ROOT/scripts/rootfs/out"
DOCKERFILE="$ROOT/scripts/rootfs/Dockerfile"

VERSION="$(cat "$OVERLAY/opt/aibrowser/VERSION")"
IMG="aibrowser-rootfs:$VERSION"
CTN="aibrowser-rootfs-build-$VERSION"
FILE="aibrowser-rootfs-$VERSION-arm64.tar.xz"
OUTFILE="$OUT/$FILE"

mkdir -p "$OUT"

echo "==> Building arm64 image (emulated; expect 20-40 min)..."

if docker buildx version >/dev/null 2>&1; then
  docker buildx build --platform linux/arm64 --load -t "$IMG" -f "$DOCKERFILE" "$ROOT"
else
  docker build --platform linux/arm64 -t "$IMG" -f "$DOCKERFILE" "$ROOT"
fi

# Package versions from the image. dpkg-query gets its format string without a
# shell in between, so ${Version} reaches it literally; npm's tree is parsed here.
readout() { docker run --rm --platform linux/arm64 "$IMG" "$@" 2>/dev/null; }
CHROMIUM="$(readout dpkg-query -W -f='${Version}' chromium)"
CLOUDFLARED="$(readout dpkg-query -W -f='${Version}' cloudflared)"
PMCP="$(readout npm list -g --depth=0 @playwright/mcp | sed -n 's/.*@playwright\/mcp@\([0-9][0-9A-Za-z.-]*\).*/\1/p' | head -n 1)"

if [ -z "$CHROMIUM" ] || [ -z "$CLOUDFLARED" ] || [ -z "$PMCP" ]; then
  echo "ERROR: could not read package versions from the image" >&2
  exit 1
fi
echo "==> chromium=$CHROMIUM cloudflared=$CLOUDFLARED playwright-mcp=$PMCP"

echo "==> Exporting container filesystem..."
docker rm -f "$CTN" >/dev/null 2>&1 || true
docker create --name "$CTN" "$IMG" >/dev/null
docker export "$CTN" | xz -T0 -6 > "$OUTFILE"
docker rm -f "$CTN" >/dev/null

echo "==> Hashing..."
SHA256="$(sha256sum "$OUTFILE" | cut -d' ' -f1)"
SIZE="$(stat -c %s "$OUTFILE")"
printf '%s  %s\n' "$SHA256" "$FILE" > "$OUTFILE.sha256"

echo "==> Writing manifest..."
python3 - "$OUT" "$VERSION" "$FILE" "$SHA256" "$SIZE" "$CHROMIUM" "$CLOUDFLARED" "$PMCP" <<'EOF'
import json, sys
out, version, file, sha256, size, chrom, cfd, pmcp = sys.argv[1:]
manifest = {
    "version": version,
    "file": file,
    "size": int(size),
    "sha256": sha256,
    "url": f"https://github.com/mrbeandev/aibrowser-android/releases/download/rootfs-{version}/{file}",
    "minApp": "0.1.0",
    "packages": {"chromium": chrom, "cloudflared": cfd, "playwright-mcp": pmcp},
}
with open(out + "/manifest.json", "w") as f:
    json.dump(manifest, f, indent=2)
    f.write("\n")
print(json.dumps(manifest, indent=2))
EOF

echo "==> Done"
echo "file: $OUTFILE"
echo "size: $SIZE bytes ($(du -h "$OUTFILE" | cut -f1))"
echo "sha256: $SHA256"
echo "manifest: $OUT/manifest.json"