# Building the rootfs

`build.sh` builds a Debian trixie arm64 userland for the app, exports it as a
tar, strips what the app never needs, and repacks it with xz.

Requirements: Docker with arm64 emulation (binfmt), `xz`, `sha256sum`,
`python3`. No root on the host beyond Docker access.

## Build

```
scripts/rootfs/build.sh
```

The build is a single arm64 container built through emulation; it is slow:
**expect 20 to 40 minutes**. `docker export` of the emulated image does not
need emulation, so only the build step is slow.

Outputs, in `scripts/rootfs/out/`:

- `aibrowser-rootfs-<version>-arm64.tar.xz` — the rootfs tarball (~250-350 MB)
- `aibrowser-rootfs-<version>-arm64.tar.xz.sha256` — its sha256
- `manifest.json` — `{version, file, size, sha256, url, minApp, packages}`,
  where `url` points at the GitHub release for this version

Version and package versions come from `rootfs-overlay/opt/aibrowser/VERSION`
and from the built image (`dpkg-query` for chromium and cloudflared,
`npm list -g` for `@playwright/mcp`).

The build is idempotent: rerunning it overwrites the outputs.

## Publish

1. Create a tag `rootfs-<version>` (e.g. `rootfs-0.1.0`) and push it.
2. Create a GitHub release named `rootfs-<version>`.
3. Attach the tarball and the `.sha256` file.
4. Attach `manifest.json`.

The app fetches the repo's "latest" release and reads the manifest, so the
release must be the latest release of this repository.

## Test the tarball on a Linux host (optional)

Requires `proot` (not root). Extract and boot the stack in a scratch dir:

```
mkdir -p /tmp/rootfs-test && tar -xJf scripts/rootfs/out/aibrowser-rootfs-0.1.0-arm64.tar.xz -C /tmp/rootfs-test
mkdir -p /tmp/rootfs-test/opt/aibrowser/data
proot -R /tmp/rootfs-test -b /proc -w /root sh -c '
  Xtigervnc :1 ... &
  DISPLAY=:1 openbox &
  DISPLAY=:1 chromium ... &
  websockify ... &
  node /opt/aibrowser/gate.js &
  python3 /opt/aibrowser/status.py &'
```

Then check `http://127.0.0.1:18932/status` with curl. The services also run
standalone: `proot -R /tmp/rootfs-test /opt/aibrowser/services/xvnc.sh` etc.,
each blocking in the foreground (exit kills the process, as the app relies on
with `proot --kill-on-exit`).