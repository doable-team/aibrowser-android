FROM --platform=linux/arm64 debian:trixie-slim

ENV DEBIAN_FRONTEND=noninteractive
ENV LANG=C.UTF-8 LC_ALL=C.UTF-8

# Base packages: Chromium, the VNC/desktop stack, noVNC, Node/Python for the
# gate and status services, plus fonts, locales and download/health tooling.
RUN apt-get update && apt-get install -y --no-install-recommends \
    chromium \
    tigervnc-standalone-server \
    openbox \
    novnc \
    websockify \
    python3 \
    nodejs \
    npm \
    curl \
    ca-certificates \
    procps \
    xz-utils \
    fonts-dejavu-core \
    fonts-noto-core \
    locales \
    && rm -rf /var/lib/apt/lists/*

# C.UTF-8 as the default locale.
RUN sed -i 's/^# *C\.UTF-8 UTF-8/C.UTF-8 UTF-8/' /etc/locale.gen && locale-gen

# cloudflared from Cloudflare's apt repository (arm64).
RUN mkdir -p --mode=0755 /usr/share/keyrings \
    && curl -fsSL https://pkg.cloudflare.com/cloudflare-main.gpg -o /usr/share/keyrings/cloudflare-main.gpg \
    && echo 'deb [signed-by=/usr/share/keyrings/cloudflare-main.gpg] https://pkg.cloudflare.com/cloudflared any main' \
        > /etc/apt/sources.list.d/cloudflared.list \
    && apt-get update && apt-get install -y --no-install-recommends cloudflared \
    && rm -rf /var/lib/apt/lists/*

# Playwright MCP server, pinned. Attaches to Chromium over CDP, so no browser
# is downloaded.
RUN npm install -g @playwright/mcp@0.0.80 \
    && npm cache clean --force

# The app's files land under /opt/aibrowser/.
COPY rootfs-overlay/ /

# Final stripping: shrink the exported filesystem (directories are kept).
RUN mkdir -p /root/profile /root/logs \
    && rm -rf /var/cache/apt/* /var/lib/apt/lists/* /usr/share/doc/* /usr/share/man/* \
    && find /var/log /tmp -mindepth 1 -delete \
    && find /usr/share/locale -mindepth 1 -maxdepth 1 -type d \
       ! -name C ! -name en -exec rm -rf {} + \
    && rm -rf /root/.npm