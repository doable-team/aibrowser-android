package dev.mrbean.aibrowser.engine

import java.io.File

/**
 * The Chromium command-line flags file at `data/chromium.flags`, read by the
 * in-rootfs `chromium.sh`. The defaults stop Chromium from offering its
 * "Restore pages?" bubble after every unclean stop and route the browser
 * through the netguard filtering proxy so it cannot reach private, loopback or
 * link-local addresses (loopback is forced through the proxy too). The file is
 * created on install and repaired at supervisor start so the Settings screen
 * always shows them.
 */
object ChromiumFlags {

    // The proxy flags point Chromium at the network guard and force loopback
    // through it too; QUIC and non-proxied WebRTC are disabled because UDP
    // would leave the browser without passing the guard at all.
    const val DEFAULT_FLAGS =
        "--disable-session-crashed-bubble\n" +
            "--hide-crash-restore-bubble\n" +
            "--proxy-server=http://127.0.0.1:8933\n" +
            "--proxy-bypass-list=<-loopback>\n" +
            "--disable-quic\n" +
            "--force-webrtc-ip-handling-policy=disable_non_proxied_udp\n"

    private val defaultLines: List<String> = DEFAULT_FLAGS.trimEnd('\n').split("\n")

    /**
     * Creates `chromium.flags` with the defaults when it does not exist; an
     * existing file keeps every operator-added line and gets any missing
     * default line appended. The file is written back only when something
     * changed.
     */
    fun ensure(dataDir: File) {
        val file = File(dataDir, "chromium.flags")
        if (!file.isFile) {
            dataDir.mkdirs()
            file.writeText(DEFAULT_FLAGS)
            return
        }
        val existing = file.readText().split("\n").filter { it.isNotBlank() }
        val missing = defaultLines.filter { d -> existing.none { it.trim() == d.trim() } }
        if (missing.isEmpty()) return
        file.writeText((existing + missing).joinToString("\n") + "\n")
    }
}