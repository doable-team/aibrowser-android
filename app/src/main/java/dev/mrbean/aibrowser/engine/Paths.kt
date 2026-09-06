package dev.mrbean.aibrowser.engine

import android.content.Context
import java.io.File

/**
 * Filesystem layout under the app's internal storage.
 *
 * [root] is [Context.getFilesDir]; the directories are created by [ensureDirs].
 */
data class Paths(
    val root: File,
    val bin: File,
    val lib: File,
    val tmp: File,
    val prootTmp: File,
    val rootfs: File,
    val data: File,
    val logs: File,
    val nativeLibraryDir: String,
) {
    fun ensureDirs() {
        listOf(bin, lib, tmp, prootTmp, rootfs, data, logs).forEach(File::mkdirs)
    }

    companion object {
        fun from(context: Context): Paths {
            val root = context.filesDir
            return Paths(
                root = root,
                bin = File(root, "bin"),
                lib = File(root, "lib"),
                tmp = File(root, "tmp"),
                prootTmp = File(root, "proot_tmp"),
                rootfs = File(root, "rootfs"),
                data = File(root, "data"),
                logs = File(root, "logs"),
                nativeLibraryDir = context.applicationInfo.nativeLibraryDir,
            )
        }
    }
}