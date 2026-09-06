#!/bin/sh
if [ ! -s /opt/aibrowser/data/tunnel.token ]; then
  sleep 30
  exit 3
fi

exec cloudflared tunnel --no-autoupdate run --token "$(cat /opt/aibrowser/data/tunnel.token)"