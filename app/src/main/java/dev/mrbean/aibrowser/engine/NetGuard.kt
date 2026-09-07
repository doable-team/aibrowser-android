package dev.mrbean.aibrowser.engine

import java.io.File

/**
 * The in-rootfs network guard at `<rootfs>/opt/aibrowser/netguard.js`, run by
 * the `netguard` service. Chromium is told (via `chromium.flags`) to use it as
 * a local filtering proxy, so every target the browser reaches is checked
 * against the private/loopback/link-local ranges before the proxy connects to
 * the exact address it resolved. The file is written on install and again at
 * supervisor start so an already-installed rootfs gets it on the next app
 * start; when the file exists but carries an older version marker it is
 * overwritten so an app update refreshes the guard.
 */
object NetGuard {

    /** Version marker line that must appear in [NETGUARD_JS]. */
    const val MARKER = "// aibrowser-netguard v3"

    const val NETGUARD_JS = """#!/usr/bin/env node
'use strict';

// aibrowser-netguard v3

const http = require('http');
const net = require('net');
const dns = require('dns');
const fs = require('fs');

const LISTEN_ADDR = '127.0.0.1';
const LISTEN_PORT = 8933;
const ALLOW_FILE = '/opt/aibrowser/data/netguard.allow';

const BLOCKED_V4 = [
  { network: 0x00000000, mask: 0xff000000 }, // 0.0.0.0/8
  { network: 0x0a000000, mask: 0xff000000 }, // 10.0.0.0/8
  { network: 0x64400000, mask: 0xffc00000 }, // 100.64.0.0/10
  { network: 0x7f000000, mask: 0xff000000 }, // 127.0.0.0/8
  { network: 0xa9fe0000, mask: 0xffff0000 }, // 169.254.0.0/16
  { network: 0xac100000, mask: 0xfff00000 }, // 172.16.0.0/12
  { network: 0xc0000000, mask: 0xffffff00 }, // 192.0.0.0/24
  { network: 0xc0000200, mask: 0xffffff00 }, // 192.0.2.0/24
  { network: 0xc0586300, mask: 0xffffff00 }, // 192.88.99.0/24
  { network: 0xc0a80000, mask: 0xffff0000 }, // 192.168.0.0/16
  { network: 0xc6120000, mask: 0xfffe0000 }, // 198.18.0.0/15
  { network: 0xc6336400, mask: 0xffffff00 }, // 198.51.100.0/24
  { network: 0xcb007100, mask: 0xffffff00 }, // 203.0.113.0/24
  { network: 0xe0000000, mask: 0xf0000000 }, // 224.0.0.0/4
  { network: 0xf0000000, mask: 0xf0000000 }, // 240.0.0.0/4
  { network: 0xffffffff, mask: 0xffffffff }, // 255.255.255.255
];

let allowHosts = [];
let allowMtime = null;

function loadAllow() {
  let mtime;
  let list = [];
  try {
    const st = fs.statSync(ALLOW_FILE);
    mtime = st.mtimeMs;
    if (mtime === allowMtime) return;
    const found = [];
    for (const line of fs.readFileSync(ALLOW_FILE, 'utf8').split('\n')) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      found.push(trimmed.toLowerCase());
    }
    list = found;
  } catch {
    mtime = null;
    list = [];
  }
  if (mtime !== allowMtime) {
    allowMtime = mtime;
    allowHosts = list;
  }
}

function ipv4ToInt(addr) {
  // Number() per octet: without it "192" + "168" concatenates instead of
  // adding, every literal address parses to garbage, and nothing is blocked.
  const parts = addr.split('.').map((octet) => Number(octet));
  if (parts.length !== 4 || parts.some((n) => !Number.isInteger(n) || n < 0 || n > 255)) {
    return -1; // not a dotted quad: treated as unknown, never as allowed
  }
  return (((parts[0] * 256 + parts[1]) * 256 + parts[2]) * 256 + parts[3]) >>> 0;
}

function isBlockedV4(ip) {
  if (ip < 0) return true; // unparseable: refuse rather than guess
  for (const range of BLOCKED_V4) {
    // >>> 0 after the AND: JavaScript's bitwise operators work on signed
    // 32-bit values, so without it every address from 128.0.0.0 up compares
    // negative and no range above that ever matches (192.168/16 included).
    if (((ip & range.mask) >>> 0) === range.network) return true;
  }
  return false;
}

function writeV4(bytes, part, offset) {
  const octets = part.split('.').map((o) => parseInt(o, 10));
  bytes[offset] = octets[0];
  bytes[offset + 1] = octets[1];
  bytes[offset + 2] = octets[2];
  bytes[offset + 3] = octets[3];
}

function ipv6ToBytes(addr) {
  let s = addr.toLowerCase();
  if (s.startsWith('[') && s.endsWith(']')) s = s.slice(1, -1);
  const bytes = Buffer.alloc(16, 0);
  let head = s;
  let tail = '';
  const z = s.indexOf('::');
  if (z !== -1) {
    head = s.slice(0, z);
    tail = s.slice(z + 2);
  }
  const headParts = head ? head.split(':') : [];
  const tailParts = tail ? tail.split(':') : [];
  let idx = 0;
  for (const part of headParts) {
    if (part.indexOf('.') !== -1) {
      writeV4(bytes, part, idx);
      idx += 4;
    } else {
      bytes.writeUInt16BE(parseInt(part, 16), idx);
      idx += 2;
    }
  }
  let endIdx = 16;
  for (let k = tailParts.length - 1; k >= 0; k--) {
    const part = tailParts[k];
    if (part.indexOf('.') !== -1) {
      writeV4(bytes, part, endIdx - 4);
      endIdx -= 4;
    } else {
      endIdx -= 2;
      bytes.writeUInt16BE(parseInt(part, 16), endIdx);
    }
  }
  return bytes;
}

function embeddedV4(bytes) {
  const mapped =
    bytes[0] === 0 && bytes[1] === 0 && bytes[2] === 0 && bytes[3] === 0 &&
    bytes[4] === 0 && bytes[5] === 0 && bytes[6] === 0 && bytes[7] === 0 &&
    bytes[8] === 0 && bytes[9] === 0 && bytes[10] === 0xff && bytes[11] === 0xff;
  const nat64 =
    bytes[0] === 0x00 && bytes[1] === 0x64 && bytes[2] === 0xff && bytes[3] === 0x9b &&
    bytes[4] === 0 && bytes[5] === 0 && bytes[6] === 0 && bytes[7] === 0 &&
    bytes[8] === 0 && bytes[9] === 0 && bytes[10] === 0 && bytes[11] === 0;
  if (mapped || nat64) return bytes.readUInt32BE(12);
  return null;
}

function isZero(bytes, end) {
  for (let i = 0; i < end; i++) {
    if (bytes[i] !== 0) return false;
  }
  return true;
}

function isBlockedV6(bytes) {
  const v4 = embeddedV4(bytes);
  if (v4 !== null) return isBlockedV4(v4);
  if (isZero(bytes, 16)) return true; // ::
  if (bytes[15] === 1 && isZero(bytes, 15)) return true; // ::1
  if ((bytes[0] & 0xfe) === 0xfc) return true; // fc00::/7
  if (bytes[0] === 0xfe && (bytes[1] & 0xc0) === 0x80) return true; // fe80::/10
  if (bytes[0] === 0xff) return true; // ff00::/8
  if (bytes[0] === 0x20 && bytes[1] === 0x01 && bytes[2] === 0x0d && bytes[3] === 0xb8) {
    return true; // 2001:db8::/32
  }
  return false;
}

function isBlockedAddress(address) {
  const family = net.isIP(address);
  if (family === 4) return isBlockedV4(ipv4ToInt(address));
  if (family === 6) return isBlockedV6(ipv6ToBytes(address));
  return false;
}

function parseHostPort(str, defaultPort) {
  const s = str.trim();
  if (s.startsWith('[')) {
    const close = s.indexOf(']');
    const host = s.slice(1, close);
    const rest = s.slice(close + 1);
    const port = rest.startsWith(':') ? parseInt(rest.slice(1), 10) : defaultPort;
    return { host, port };
  }
  const lastColon = s.lastIndexOf(':');
  if (lastColon === -1) return { host: s, port: defaultPort };
  return { host: s.slice(0, lastColon), port: parseInt(s.slice(lastColon + 1), 10) };
}

async function checkHost(host) {
  loadAllow();
  if (allowHosts.includes(host.toLowerCase())) {
    return { ok: true, address: null };
  }
  let addresses;
  if (net.isIP(host)) {
    addresses = [host];
  } else {
    const records = await dns.promises.lookup(host, { all: true });
    addresses = records.map((r) => r.address);
  }
  for (const address of addresses) {
    if (isBlockedAddress(address)) {
      return { ok: false, address };
    }
  }
  return { ok: true, address: addresses[0] || null };
}

function logRefused(host, address) {
  console.log(new Date().toISOString() + ' refused host=' + host + ' address=' + address);
}

function onRequest(req, res) {
  let url;
  try {
    url = new URL(req.url, 'http://127.0.0.1');
  } catch (e) {
    res.writeHead(400);
    res.end('bad request url');
    return;
  }
  let host = url.hostname;
  if (host.startsWith('[') && host.endsWith(']')) host = host.slice(1, -1);
  const port = url.port ? parseInt(url.port, 10) : 80;
  checkHost(host).then(
    (verdict) => {
      if (!verdict.ok) {
        const body = JSON.stringify({ ok: false, error: 'blocked', host: host, address: verdict.address });
        res.writeHead(403, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) });
        res.end(body);
        logRefused(host, verdict.address);
        return;
      }
      const upstream = http.request(
        {
          host: verdict.address || host,
          port: port,
          method: req.method,
          path: url.pathname + url.search,
          headers: req.headers,
        },
        (ures) => {
          res.writeHead(ures.statusCode, ures.headers);
          ures.pipe(res);
          ures.on('error', () => res.destroy());
        },
      );
      upstream.on('error', () => {
        res.writeHead(502, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ ok: false, error: 'bad gateway' }));
      });
      req.pipe(upstream);
      req.on('error', () => upstream.destroy());
      req.on('aborted', () => upstream.destroy());
      res.on('close', () => {
        if (!res.writableEnded) upstream.destroy();
      });
    },
    () => {
      res.writeHead(502, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ ok: false, error: 'bad gateway' }));
    },
  );
}

function onConnect(req, clientSocket, head) {
  const target = parseHostPort(req.url, 443);
  checkHost(target.host).then(
    (verdict) => {
      if (!verdict.ok) {
        logRefused(target.host, verdict.address);
        clientSocket.end('HTTP/1.1 403 Forbidden\r\n\r\n');
        return;
      }
      const upstream = net.connect({ host: verdict.address || target.host, port: target.port });
      upstream.once('connect', () => {
        clientSocket.write('HTTP/1.1 200 Connection Established\r\n\r\n');
        if (head && head.length) upstream.write(head);
        clientSocket.pipe(upstream);
        upstream.pipe(clientSocket);
      });
      upstream.once('error', () => {
        clientSocket.end('HTTP/1.1 502 Bad Gateway\r\n\r\n');
      });
      clientSocket.on('error', () => upstream.destroy());
      clientSocket.on('close', () => upstream.destroy());
      upstream.on('error', () => clientSocket.destroy());
    },
    () => {
      clientSocket.end('HTTP/1.1 502 Bad Gateway\r\n\r\n');
    },
  );
}

// `node netguard.js --self-test` checks the address classifier and exits
// non-zero on any wrong verdict, without opening a socket. Two real bugs hid
// here: string octets that concatenated, and a signed AND that let every
// address from 128.0.0.0 up through the filter.
if (process.argv.includes('--self-test')) {
  const blocked = [
    '0.0.0.0', '10.0.0.5', '100.64.0.1', '127.0.0.1', '169.254.169.254',
    '172.16.0.1', '172.20.1.5', '172.31.255.255', '192.0.0.1', '192.168.1.1',
    '198.18.0.1', '224.0.0.1', '240.0.0.1', '255.255.255.255',
    '::', '::1', 'fc00::1', 'fd12:3456::1', 'fe80::1', 'ff02::1',
    '::ffff:192.168.1.1', '64:ff9b::10.0.0.1', '2001:db8::1',
  ];
  const allowed = [
    '1.1.1.1', '8.8.8.8', '93.184.216.34', '172.32.0.1', '100.128.0.1',
    '192.167.255.255', '2606:2800:220:1:248:1893:25c8:1946', '2a00:1450:4001::1',
  ];
  let failures = 0;
  for (const address of blocked) {
    if (!isBlockedAddress(address)) { console.error('NOT blocked but should be: ' + address); failures++; }
  }
  for (const address of allowed) {
    if (isBlockedAddress(address)) { console.error('blocked but should not be: ' + address); failures++; }
  }
  console.log(failures === 0
    ? 'netguard self-test: ' + (blocked.length + allowed.length) + ' addresses classified correctly'
    : 'netguard self-test: ' + failures + ' wrong');
  process.exit(failures === 0 ? 0 : 1);
}

const server = http.createServer(onRequest);
server.on('connect', onConnect);
server.requestTimeout = 0;
server.headersTimeout = 60000;
server.keepAliveTimeout = 75000;
server.listen(LISTEN_PORT, LISTEN_ADDR, () => {
  console.log(new Date().toISOString() + ' netguard listening on ' + LISTEN_ADDR + ':' + LISTEN_PORT);
});"""

    const val NETGUARD_SH = """#!/bin/sh
exec node /opt/aibrowser/netguard.js
"""

    /**
     * Writes `opt/aibrowser/netguard.js` and `opt/aibrowser/services/netguard.sh`
     * into an installed rootfs, creating the directories and marking the
     * scripts executable where the filesystem allows. A rootfs without
     * `/opt/aibrowser` is left alone (the overlay files are not there yet).
     * When `netguard.js` exists but its version marker differs it is
     * overwritten, so an app update refreshes an existing install.
     */
    fun ensure(rootfs: File) {
        val dir = File(rootfs, "opt/aibrowser")
        if (!dir.isDirectory) return
        val js = File(dir, "netguard.js")
        if (!js.isFile || !js.readText().contains(MARKER)) {
            js.writeText(NETGUARD_JS)
            js.setExecutable(true)
        }
        val services = File(dir, "services")
        services.mkdirs()
        val sh = File(services, "netguard.sh")
        if (!sh.isFile || sh.readText() != NETGUARD_SH) {
            sh.writeText(NETGUARD_SH)
            sh.setExecutable(true)
        }
    }
}