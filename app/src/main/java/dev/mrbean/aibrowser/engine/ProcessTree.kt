package dev.mrbean.aibrowser.engine

import java.io.File
import java.io.IOException

/**
 * Inspects and signals host processes by pid.
 *
 * Kept free of compile-time Android imports (the Android signalling path is
 * reached via reflection) so the unit tests can compile and run on the JVM.
 */
interface ProcessTree {

    /** Returns the pids of the direct children of [pid], if any. */
    fun childrenOf(pid: Int): List<Int>

    /** Sends [signal] (raw signal number) to [pid]. */
    fun signal(pid: Int, signal: Int)
}

/**
 * Default [ProcessTree] scanning `/proc/<pid>/stat` for the parent pid (the
 * 4th field of the stat line) and signalling with
 * [android.os.Process.sendSignal] when running on Android, falling back to
 * `kill -<signal> <pid>` through [ProcessBuilder] (used by the JVM tests).
 */
class ProcProcessTree : ProcessTree {

    override fun childrenOf(pid: Int): List<Int> {
        val procDir = File("/proc")
        val entries = procDir.listFiles { f -> f.name.all { it.isDigit() } } ?: return emptyList()
        return entries.mapNotNull { entry ->
            val stat = try {
                File(entry, "stat").readText()
            } catch (_: IOException) {
                return@mapNotNull null
            } catch (_: SecurityException) {
                return@mapNotNull null
            }
            // The comm field may contain spaces and parens, so split after the
            // last ") " and take field 2 (the parent pid) of the remainder.
            val fields = stat.substringAfterLast(") ").split(' ').filter { it.isNotEmpty() }
            val ppid = fields.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            if (ppid == pid) entry.name.toIntOrNull() else null
        }
    }

    override fun signal(pid: Int, signal: Int) {
        val sendSignal = try {
            val androidProcess = Class.forName("android.os.Process")
            androidProcess.getMethod(
                "sendSignal",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
        } catch (_: Throwable) {
            null
        }
        if (sendSignal != null) {
            try {
                sendSignal.invoke(null, pid, signal)
                return
            } catch (_: Throwable) {
                // Fall through to the kill binary if the reflection call fails
                // (e.g. "not mocked" in the unit-test environment).
            }
        }
        runCatching {
            ProcessBuilder("kill", "-$signal", pid.toString()).start().waitFor()
        }
    }
}