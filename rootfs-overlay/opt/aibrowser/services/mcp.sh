#!/bin/sh
i=0
until curl -fsS http://127.0.0.1:9222/json/version >/dev/null 2>&1; do
  [ "$i" -ge 120 ] && exit 1
  sleep 1
  i=$((i + 1))
done

hosts="localhost:18931,127.0.0.1:18931"
if [ -s /opt/aibrowser/data/mcp.host ]; then
  hosts="$hosts,$(tr -d ' \t\r\n' < /opt/aibrowser/data/mcp.host)"
fi

export PLAYWRIGHT_MCP_PING_TIMEOUT_MS=0

exec playwright-mcp --cdp-endpoint http://127.0.0.1:9222 \
  --port 18931 --host 127.0.0.1 --allowed-hosts "$hosts"