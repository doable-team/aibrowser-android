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

## Install (personal use)

Sideload the release APK from `app/build/outputs/apk/release/app-release.apk`
(or the debug one from `app/build/outputs/apk/debug/app-debug.apk`), then open
the app.

- Setup: "Prepare" runs the proot self-test; "Install" downloads the rootfs
  (default manifest URL `https://mrbean.dev/aibrowser/manifest.json`, about
  300 MB, then a few minutes of extraction); then do the Android checks
  (battery optimisation exemption, disable child process restrictions).
- Settings: paste the Cloudflare tunnel token, add an API token (shown once),
  set the MCP hostname.
- Dashboard: "Start all".

The three public hostnames you create in Zero Trust map to these local ports:

| hostname | local port |
|---|---|
| MCP | 8931 |
| status | 8932 |
| viewer | 6080 |

The MCP URL for your agent is `https://<mcp host>/mcp?token=<token>`. Put the
viewer hostname behind a Cloudflare Access login so the screen stays private.

## Build from source

Requirements: JDK 17, the Android SDK (platform 36, build-tools 35), Docker
for the rootfs image. Then:

```
scripts/fetch-native.sh          # proot, busybox, tar as jniLibs
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
scripts/make-keystore.sh         # release.jks + keystore.properties (signing)
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
scripts/rootfs/build.sh          # scripts/rootfs/out/aibrowser-rootfs-<v>-arm64.tar.xz
```

To host the rootfs yourself, put `manifest.json` plus the tarball and its
`.sha256` in one folder on any HTTPS host and point the app's mirror URL at
`https://<host>/<folder>/manifest.json`.

## Licence

MIT for the app. The bundled proot, busybox and tar binaries are GPL programs
built from termux-packages, executed as separate processes; see
`THIRD_PARTY.md`. The rootfs is Debian.
