#!/bin/sh
export DISPLAY=:1

i=0
until [ -S /tmp/.X11-unix/X1 ] || [ "$i" -ge 60 ]; do
  sleep 1
  i=$((i + 1))
done
[ -S /tmp/.X11-unix/X1 ] || exit 1

flags="--no-sandbox --disable-gpu --disable-dev-shm-usage \
  --remote-debugging-port=9222 --remote-debugging-address=127.0.0.1 \
  --user-data-dir=/root/profile --window-size=1280,720 \
  --no-first-run --no-default-browser-check"

if [ -s /opt/aibrowser/data/chromium.flags ]; then
  while IFS= read -r flag; do
    [ -n "$flag" ] && flags="$flags $flag"
  done < /opt/aibrowser/data/chromium.flags
fi

if [ -d /opt/aibrowser/data/extensions ]; then
  exts=$(find /opt/aibrowser/data/extensions -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort | paste -sd, -)
  [ -n "$exts" ] && flags="$flags --load-extension=$exts"
fi

exec chromium $flags about:blank