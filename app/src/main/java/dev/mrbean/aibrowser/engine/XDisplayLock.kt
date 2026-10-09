package dev.mrbean.aibrowser.engine

import java.io.File

/** Recover display :1 after Android kills X without letting it unlink its lock. */
object XDisplayLock {
    fun recover(rootfs: File, processExists: (Int) -> Boolean = { File("/proc/$it").exists() }): Boolean {
        val lock = File(rootfs, "tmp/.X1-lock")
        if (!lock.isFile) return false
        val contents = lock.readText()
        val pid = contents.trim().toIntOrNull() ?: return false
        // Unknown or live owners must never be disturbed.
        if (pid <= 0 || processExists(pid)) return false
        if (lock.readText() != contents) return false
        val socket = File(rootfs, "tmp/.X11-unix/X1")
        if (socket.exists() && !socket.delete()) return false
        return lock.delete()
    }
}
