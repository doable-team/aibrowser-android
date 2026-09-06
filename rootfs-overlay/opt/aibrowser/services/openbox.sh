#!/bin/sh
export DISPLAY=:1

i=0
until [ -S /tmp/.X11-unix/X1 ] || [ "$i" -ge 60 ]; do
  sleep 1
  i=$((i + 1))
done
[ -S /tmp/.X11-unix/X1 ] || exit 1

exec openbox