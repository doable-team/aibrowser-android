package dev.mrbean.aibrowser.engine

import java.io.File

/**
 * The `index.html` for noVNC at `<rootfs>/usr/share/novnc`, served by
 * websockify when the viewer is opened at the bare hostname. Without it the
 * server shows a directory listing instead of the desktop; the redirect keeps
 * any query string the visitor typed. The file is written on install and again
 * at supervisor start so an already-installed rootfs gets it on the next app
 * start.
 */
object NoVncIndex {

    const val DEFAULT_INDEX = """<!doctype html>
<meta charset="utf-8">
<title>AiBrowser viewer</title>
<script>location.replace("vnc.html?autoconnect=true&resize=scale" + (location.search ? "&" + location.search.slice(1) : ""));</script>
<noscript><a href="vnc.html?autoconnect=true&amp;resize=scale">Open the viewer</a></noscript>
"""

    /** Creates `usr/share/novnc/index.html` when the directory exists and the file is missing. */
    fun ensure(rootfs: File) {
        val dir = File(rootfs, "usr/share/novnc")
        if (!dir.isDirectory) return
        val file = File(dir, "index.html")
        if (!file.isFile) {
            file.writeText(DEFAULT_INDEX)
        }
    }
}