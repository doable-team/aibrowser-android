# AiBrowser for Android

Turn a spare Android phone into a headed Chromium that AI agents drive over
MCP from anywhere, with a live view of the screen for you. One APK, no root,
no Termux.

Inside the app a Debian userland runs under proot and holds Chromium, a VNC
server, noVNC, the Playwright MCP server, a token gate, a health service and a
Cloudflare tunnel. The app supervises those processes and gives you a control
panel: first-run setup, service switches and status, a live preview, tunnel
token and API tokens.

Status: in development. See `docs/SPEC.md` for the design and milestones.

## Why a phone

A phone on a residential connection with a real, headed browser passes the
bot checks that datacenter browsers fail, costs nothing to run, and survives
reboots. Measured on a Realme RMX3998: Chromium under proot loads pages as
fast as a native build.

## How it fits together

```
agent (any MCP client)
   |  https://<mcp host>/mcp?token=...
   v
Cloudflare tunnel  ->  phone: gate (8931)  ->  Playwright MCP (18931)  ->  Chromium
you                ->  phone: noVNC (6080)  ->  VNC server  ->  the same screen
```

## Building

Requirements: JDK 17, the Android SDK (platform 35, build-tools 35), Docker
for the rootfs image. Then:

```
scripts/fetch-native.sh          # proot, busybox, tar as jniLibs
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
scripts/rootfs/build.sh          # scripts/rootfs/out/aibrowser-rootfs-<v>-arm64.tar.xz
```

The app downloads the rootfs on first run from this repository's releases.

## Licence

MIT for the app. The bundled proot, busybox and tar binaries are GPL programs
built from termux-packages, executed as separate processes; see
`THIRD_PARTY.md`. The rootfs is Debian.
