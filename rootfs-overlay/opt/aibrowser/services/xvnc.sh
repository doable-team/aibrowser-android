#!/bin/sh
exec Xtigervnc :1 -geometry 1280x720 -depth 24 -rfbport 5901 \
  -SecurityTypes None -localhost yes -AlwaysShared