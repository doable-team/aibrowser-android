#!/usr/bin/env python3
import json
import os
import time
import urllib.request
import urllib.error
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DATA = '/opt/aibrowser/data'
STATE = os.path.join(DATA, 'state.json')
EXT = os.path.join(DATA, 'extensions')
TLOG = os.path.join(DATA, 'logs', 'tunnel.log')
SERVICES = ['xvnc', 'openbox', 'chromium', 'novnc', 'mcp', 'gate', 'status', 'tunnel']
START = time.time()


def probe(method, url, data=None):
    try:
        req = urllib.request.Request(url, method=method, data=data)
        with urllib.request.urlopen(req, timeout=3) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code
    except Exception:
        return None


def services_payload():
    loaded = {}
    try:
        with open(STATE) as f:
            loaded = json.load(f).get('services', {})
    except Exception:
        pass
    now = time.time()
    out = {}
    for name in SERVICES:
        svc = loaded.get(name, {})
        state = svc.get('state', '')
        state = state if state in ('run', 'down', 'disabled') else 'unknown'
        uptime = 0
        if state == 'run':
            since = svc.get('since')
            if isinstance(since, (int, float)):
                uptime = max(0, int(now - since))
        out[name] = {'state': state, 'uptime_sec': uptime}
    return out


def chromium_payload():
    try:
        with urllib.request.urlopen('http://127.0.0.1:9222/json/version', timeout=3) as r:
            ver = json.loads(r.read().decode('utf-8', 'replace'))
        with urllib.request.urlopen('http://127.0.0.1:9222/json/list', timeout=3) as r:
            lst = json.loads(r.read().decode('utf-8', 'replace'))
        pages = [p.get('url') for p in lst if p.get('type') == 'page']
        return {'ok': True, 'browser': ver.get('Browser'), 'pages': pages}
    except Exception:
        return {'ok': False, 'browser': None, 'pages': []}


def extensions_payload():
    out = []
    if os.path.isdir(EXT):
        for name in sorted(os.listdir(EXT)):
            path = os.path.join(EXT, name)
            if not os.path.isdir(path):
                continue
            try:
                with open(os.path.join(path, 'manifest.json')) as f:
                    manifest = json.load(f)
                if manifest.get('name'):
                    out.append({'name': manifest['name'], 'version': manifest.get('version', '')})
            except Exception:
                continue
    return out


def mcp_payload():
    code = probe('POST', 'http://127.0.0.1:18931/mcp', data=b'')
    return {'ok': code in (400, 406), 'http': code}


def novnc_payload():
    code = probe('GET', 'http://127.0.0.1:6080/')
    return {'ok': code == 200, 'http': code}


def gate_payload():
    codes = {}
    for port in ('8931', '8932'):
        codes[port] = probe('GET', 'http://127.0.0.1:%s/health' % port)
    return {'ok': all(c == 401 for c in codes.values()), 'http': codes}


def tunnel_payload():
    try:
        with open(TLOG) as f:
            lines = f.read().splitlines()
    except Exception:
        return {'connections': 0}
    last = None
    for i, line in enumerate(lines):
        if 'Starting tunnel' in line:
            last = i
    if last is None:
        return {'connections': 0}
    n = sum(1 for line in lines[last:] if 'Registered tunnel connection' in line)
    return {'connections': n}


def build_payload():
    services = services_payload()
    chromium = chromium_payload()
    mcp = mcp_payload()
    novnc = novnc_payload()
    gate = gate_payload()
    all_ok = all(s['state'] in ('run', 'disabled') for s in services.values())
    chromium_ok = True if services['chromium']['state'] == 'disabled' else chromium['ok']
    mcp_ok = True if services['mcp']['state'] == 'disabled' else mcp['ok']
    novnc_ok = True if services['novnc']['state'] == 'disabled' else novnc['ok']
    ok = all_ok and chromium_ok and mcp_ok and novnc_ok and gate['ok']
    return {
        'ok': ok,
        'time': datetime.now().isoformat(),
        'status_uptime_sec': int(time.time() - START),
        'services': services,
        'chromium': chromium,
        'extensions_loaded': extensions_payload(),
        'mcp': mcp,
        'novnc': novnc,
        'gate': gate,
        'tunnel': tunnel_payload(),
    }


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path not in ('/', '/status', '/status/', '/health'):
            self.send_error(404)
            return
        body = json.dumps(build_payload()).encode('utf-8')
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


if __name__ == '__main__':
    ThreadingHTTPServer(('127.0.0.1', 18932), Handler).serve_forever()