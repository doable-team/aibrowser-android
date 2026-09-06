package dev.mrbean.aibrowser.engine

import java.io.File

/**
 * The Chromium command-line flags file at `data/chromium.flags`, read by the
 * in-rootfs `chromium.sh`. The defaults stop Chromium from offering its
 * "Restore pages?" bubble after every unclean stop; the file is created on
 * install and again at supervisor start so the Settings screen always shows
 * them.
 */
object ChromiumFlags {

    const val DEFAULT_FLAGS = "--disable-session-crashed-bubble\n--hide-crash-restore-bubble\n"

    /** Creates `chromium.flags` with the defaults when it does not exist. */
    fun ensure(dataDir: File) {
        val file = File(dataDir, "chromium.flags")
        if (!file.isFile) {
            dataDir.mkdirs()
            file.writeText(DEFAULT_FLAGS)
        }
    }
}