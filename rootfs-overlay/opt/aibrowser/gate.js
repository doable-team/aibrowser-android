#!/usr/bin/env node
'use strict';

const http = require('http');
const crypto = require('crypto');
const fs = require('fs');

const TOKEN_FILE = '/opt/aibrowser/data/mcp.tokens';
const ROUTES = [
  { listen: 8931, target: 18931 },
  { listen: 8932, target: 18932 },
];

let tokens = [];
let tokenMtime = null;

function loadTokens() {
  let mtime;
  let list = [];
  try {
    const st = fs.statSync(TOKEN_FILE);
    mtime = st.mtimeMs;
    if (mtime === tokenMtime) return;
    const found = [];
    for (const line of fs.readFileSync(TOKEN_FILE, 'utf8').split('\n')) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      const token = trimmed.split(/\s+/).pop();
      if (token) found.push(token);
    }
    list = found;
  } catch {
    mtime = null;
    list = [];
  }
  if (mtime !== tokenMtime) {
    tokenMtime = mtime;
    tokens = list;
  }
}

function matches(token) {
  const buf = Buffer.from(token);
  for (const t of tokens) {
    const tb = Buffer.from(t);
    if (tb.length === buf.length && crypto.timingSafeEqual(tb, buf)) return true;
  }
  return false;
}

function extractToken(req, url) {
  const q = url.searchParams.get('token');
  if (q) return { token: q, query: true };
  const auth = req.headers['authorization'] || '';
  const m = /^Bearer\s+(.+)$/i.exec(auth);
  if (m) return { token: m[1].trim(), bearer: true };
  const x = req.headers['x-mcp-token'];
  if (x) return { token: x, header: true };
  return { token: null };
}

function refusalInfo(req, status) {
  const ip = req.headers['cf-connecting-ip'] || req.socket.remoteAddress;
  if (!req.headers['cf-connecting-ip'] && (ip === '::1' || ip === '::ffff:127.0.0.1' || ip === '127.0.0.1')) {
    return null;
  }
  const path = new URL(req.url, 'http://127.0.0.1').pathname;
  return `${new Date().toISOString()} ${status} ${req.method} ${path} ip=${ip}`;
}

function makeHandler(targetPort) {
  return (req, res) => {
    loadTokens();
    const url = new URL(req.url, 'http://127.0.0.1');
    const provided = extractToken(req, url);
    const ok = provided.token !== null && matches(provided.token);

    if (!ok) {
      const noTokens = tokens.length === 0;
      const status = noTokens ? 503 : 401;
      const body = JSON.stringify({
        ok: false,
        error: noTokens ? 'gate has no tokens' : 'unauthorized',
      });
      res.writeHead(status, {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(body),
      });
      res.end(body);
      const log = refusalInfo(req, status);
      if (log) console.log(log);
      return;
    }

    if (provided.query) url.searchParams.delete('token');
    delete req.headers['authorization'];
    delete req.headers['x-mcp-token'];

    let responded = false;
    const upstream = http.request(
      {
        hostname: '127.0.0.1',
        port: targetPort,
        method: req.method,
        path: url.pathname + url.search,
        headers: req.headers,
      },
      (ures) => {
        responded = true;
        res.writeHead(ures.statusCode, ures.headers);
        ures.pipe(res);
        ures.on('error', () => res.destroy());
      },
    );

    upstream.on('error', () => {
      if (responded) {
        upstream.destroy();
        res.destroy();
        return;
      }
      responded = true;
      const body = JSON.stringify({ ok: false, error: 'bad gateway' });
      res.writeHead(502, {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(body),
      });
      res.end(body);
    });

    req.pipe(upstream);
    req.on('error', () => upstream.destroy());
    req.on('aborted', () => upstream.destroy());
    res.on('close', () => {
      if (!res.writableEnded) upstream.destroy();
    });
  };
}

loadTokens();

for (const route of ROUTES) {
  const server = http.createServer(makeHandler(route.target));
  server.requestTimeout = 0;
  server.headersTimeout = 60000;
  server.keepAliveTimeout = 75000;
  server.listen(route.listen, '127.0.0.1', () => {
    console.log(`${new Date().toISOString()} gate listening on 127.0.0.1:${route.listen} -> ${route.target}`);
  });
}