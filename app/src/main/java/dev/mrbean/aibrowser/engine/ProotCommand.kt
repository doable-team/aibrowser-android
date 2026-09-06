package dev.mrbean.aibrowser.engine

import java.io.File

data class ProcessSpec(
    val argv: List<String>,
    val env: Map<String, String>,
)

/**
 * Environment every proot process runs with: its libraries come from
 * [Paths.lib], the loader from the native library dir, scratch space from
 * [Paths.prootTmp], and the shell helpers from [Paths.bin].
 */
fun prootEnv(paths: Paths): Map<String, String> = mapOf(
    "LD_LIBRARY_PATH" to paths.lib.absolutePath,
    "PROOT_TMP_DIR" to paths.prootTmp.absolutePath,
    "PROOT_LOADER" to "${paths.nativeLibraryDir}/libproot-loader.so",
    "PATH" to "${paths.bin.absolutePath}:/system/bin",
)

/**
 * Builds the argv/env that run `command` inside a rootfs with proot.
 * Pure Kotlin so it is unit-testable.
 */
object ProotCommand {

    fun build(
        paths: Paths,
        rootfsDir: File,
        workingDir: String,
        extraMounts: List<Pair<String, String>> = emptyList(),
        env: Map<String, String> = emptyMap(),
        command: List<String>,
    ): ProcessSpec {
        val argv = buildList {
            add(File(paths.bin, "proot").absolutePath)
            add("--kill-on-exit")
            add("--link2symlink")
            add("--sysvipc")
            add("-L")
            add("-0")
            add("-r")
            add(rootfsDir.absolutePath)
            add("-w")
            add(workingDir)
            add("--mount=/dev")
            add("--mount=/proc")
            add("--mount=/sys")
            add("--mount=/system")
            add("--mount=/apex")
            add("--mount=/data")
            add("--mount=${paths.tmp.absolutePath}:/dev/shm")
            add("--mount=${paths.data.absolutePath}:/opt/aibrowser/data")
            for ((host, guest) in extraMounts) {
                add("--mount=$host:$guest")
            }
            add("/usr/bin/env")
            add("-i")
            add("HOME=/root")
            add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
            add("TERM=xterm-256color")
            add("LANG=C.UTF-8")
            for ((key, value) in env.toSortedMap()) {
                add("$key=$value")
            }
            addAll(command)
        }
        return ProcessSpec(argv, prootEnv(paths))
    }
}