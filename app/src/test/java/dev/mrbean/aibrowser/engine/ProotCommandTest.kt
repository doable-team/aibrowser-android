package dev.mrbean.aibrowser.engine

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProotCommandTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `builds the exact argv and env`() {
        val root = tmp.newFolder("files")
        val paths = Paths(
            root = root,
            bin = File(root, "bin"),
            lib = File(root, "lib"),
            tmp = File(root, "tmp"),
            prootTmp = File(root, "proot_tmp"),
            rootfs = File(root, "rootfs"),
            data = File(root, "data"),
            logs = File(root, "logs"),
            nativeLibraryDir = "/data/app/package/lib/arm64-v8a",
        )
        val rootfsDir = File(root, "rootfs")

        val spec = ProotCommand.build(
            paths = paths,
            rootfsDir = rootfsDir,
            workingDir = "/root",
            extraMounts = listOf("host-a" to "guest-a", "host-b" to "guest-b"),
            env = mapOf("ZOO" to "3", "ALPHA" to "1", "MIKE" to "2"),
            command = listOf("/bin/sh", "-c", "echo hi"),
        )

        val expectedArgv = listOf(
            File(paths.bin, "proot").absolutePath,
            "--kill-on-exit", "--link2symlink", "--sysvipc", "-L", "-0",
            "-r", rootfsDir.absolutePath,
            "-w", "/root",
            "--mount=/dev", "--mount=/proc", "--mount=/sys",
            "--mount=/system", "--mount=/apex", "--mount=/data",
            "--mount=${paths.tmp.absolutePath}:/dev/shm",
            "--mount=${paths.data.absolutePath}:/opt/aibrowser/data",
            "--mount=host-a:guest-a",
            "--mount=host-b:guest-b",
            "/usr/bin/env", "-i",
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "ALPHA=1", "MIKE=2", "ZOO=3",
            "/bin/sh", "-c", "echo hi",
        )
        assertEquals(expectedArgv, spec.argv)

        val expectedEnv = mapOf(
            "LD_LIBRARY_PATH" to paths.lib.absolutePath,
            "PROOT_TMP_DIR" to paths.prootTmp.absolutePath,
            "PROOT_LOADER" to "${paths.nativeLibraryDir}/libproot-loader.so",
            "PATH" to "${paths.bin.absolutePath}:/system/bin",
        )
        assertEquals(expectedEnv, spec.env)
    }
}