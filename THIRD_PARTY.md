# Third-party components

## Native binaries shipped as jniLibs (arm64-v8a)

These are separate programs the app executes; they are not linked into it.
They are builds from the termux-packages tree (Android-patched proot with
`--kill-on-exit` and `--link2symlink`), redistributed unmodified. For the
initial bootstrap they are taken from the Pocket Trilium v1.5.0 release APK
(https://github.com/nriver/pocket-trilium/releases/tag/v1.5.0, file
`pocket-trilium_1.5.0_20260828_01-arm64-v8a-release.apk`), whose build
recipe is documented in that repository's `docs/jniLibs.md`.
`scripts/fetch-native.sh` fetches them and checks these SHA-256 prefixes.

| file | program / library | licence | sha256 (first 16) |
|---|---|---|---|
| libexec_proot.so | proot | GPL-2.0 | 8a03d1da568e2117 |
| libproot-loader.so | proot loader | GPL-2.0 | b5b856b8b0d00d88 |
| libexec_busybox.so | busybox launcher | GPL-2.0 | 527f2ede5721d062 |
| libbusybox.so | busybox | GPL-2.0 | cae8a362b0e63ce2 |
| libexec_tar.so | GNU tar | GPL-3.0 | a755d420143dafb8 |
| libtalloc.so | talloc | LGPL-3.0 | ea1092e4a755589b |
| libacl.so | acl | LGPL-2.1 | 61bac9778cd70979 |
| libattr.so | attr | LGPL-2.1 | c83e3a340c931625 |
| libandroid-selinux.so | libselinux (Android) | public domain / BSD-like | e5918c3606edc64d |
| libiconv.so | GNU libiconv | LGPL-2.1 | bbd84d42f56c709d |
| libcharset.so | GNU libiconv (charset) | LGPL-2.1 | 03dc14680e9cfdef |
| libpcre2-8.so | PCRE2 | BSD-3-Clause | 2338a513735e3b58 |

Source for all of the above: https://github.com/termux/termux-packages (and
the fork used by Pocket Trilium, https://github.com/Nriver/termux-packages).
A `scripts/build-native.sh` that reproduces them in Docker is planned.

## Inside the rootfs

Debian trixie (arm64) and its packages under their own licences: Chromium,
TigerVNC, Openbox, noVNC, websockify, Python, Node.js; cloudflared
(Apache-2.0) from Cloudflare's package repository; @playwright/mcp
(Apache-2.0) from npm. The rootfs is downloaded by the app; nothing from it
is inside the APK.

## Written offer of source (GPL binaries)

The released APK carries the GPL and LGPL programs listed above as separate
executables. Their source is the termux-packages tree, which builds them:
https://github.com/termux/termux-packages — proot under `packages/proot`,
busybox under `packages/busybox`, GNU tar under `packages/tar`, and the
libraries under their own package directories.

If you would rather have the corresponding source directly, open an issue on
this repository and it will be provided for any released binary, on a physical
medium or by download, for as long as that release is distributed and for at
least three years after, at no more than the cost of distribution. This offer
is valid for anyone who has a copy of the APK.

## Prior art

Mechanism and packaging technique from Cateners/tiny_computer (GPL-3.0) and
nriver/pocket-trilium (AGPL-3.0). No source code from either is used here.
