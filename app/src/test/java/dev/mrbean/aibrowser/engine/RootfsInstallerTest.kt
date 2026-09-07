package dev.mrbean.aibrowser.engine

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest

class RootfsInstallerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: HttpServer
    private lateinit var base: String
    private lateinit var paths: Paths
    private lateinit var config: ConfigStore
    private lateinit var fakeRunner: FakeRunner

    private val file = "aibrowser-rootfs-0.1.0-arm64.tar.xz"
    private lateinit var tarballBytes: ByteArray
    private lateinit var manifestJson: String

    class FakeRunner : ProcessRunner() {
        val calls = mutableListOf<ProcessSpec>()
        var simulateExtraction: (() -> Unit)? = null
        var result = RunResult(exitCode = 0, timedOut = false)
        var stdoutLines = 1

        override suspend fun run(
            spec: ProcessSpec,
            cwd: File?,
            timeoutMs: Long?,
            onStarted: (pid: Int?) -> Unit,
            onLine: (line: String, isStderr: Boolean) -> Unit,
        ): RunResult {
            calls.add(spec)
            simulateExtraction?.invoke()
            repeat(stdoutLines) { i -> onLine("entry-$i", false) }
            return result
        }
    }

    @Before
    fun setUp() {
        tarballBytes = "this is not a real tarball, but the installer never inspects the bytes\n".toByteArray()
        val sha = sha256(tarballBytes)
        manifestJson =
            """{"version":"0.1.0","file":"$file","size":${tarballBytes.size},"sha256":"$sha","url":"http://example.invalid/$file"}"""

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/manifest.json") { exchange -> serve(exchange, manifestJson.toByteArray(), "application/json") }
        server.createContext("/$file") { exchange -> serve(exchange, tarballBytes, "application/octet-stream") }
        server.start()
        base = "http://127.0.0.1:${server.address.port}"

        val root = tmp.newFolder("files")
        paths = Paths(
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
        paths.ensureDirs()
        config = ConfigStore(paths.data)
        fakeRunner = FakeRunner()
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `installs the rootfs end to end`() = runBlocking {
        fakeRunner.simulateExtraction = {
            val env = File(paths.rootfs, "usr/bin/env")
            env.parentFile?.mkdirs()
            env.writeText("env")
        }
        val installer = RootfsInstaller(paths, fakeRunner, Downloader(), config)
        val manifestUrl = "$base/manifest.json"
        val tarball = File(paths.data, file)

        installer.install(manifestUrl)

        assertEquals(InstallState.Installed("0.1.0"), installer.state.value)
        assertEquals(1, fakeRunner.calls.size)
        val expectedArgv = listOf(
            File(paths.bin, "proot").absolutePath,
            "--link2symlink",
            "-0",
            File(paths.bin, "tar").absolutePath,
            "x",
            "-J",
            "--delay-directory-restore",
            "--preserve-permissions",
            "-v",
            "-f",
            tarball.absolutePath,
            "-C",
            paths.rootfs.absolutePath,
        )
        assertEquals(expectedArgv, fakeRunner.calls[0].argv)
        assertEquals(
            mapOf(
                "LD_LIBRARY_PATH" to paths.lib.absolutePath,
                "PROOT_TMP_DIR" to paths.prootTmp.absolutePath,
                "PROOT_LOADER" to "${paths.nativeLibraryDir}/libproot-loader.so",
                "PATH" to "${paths.bin.absolutePath}:/system/bin",
            ),
            fakeRunner.calls[0].env,
        )

        assertEquals("nameserver 1.1.1.1\nnameserver 8.8.8.8\n", File(paths.rootfs, "etc/resolv.conf").readText())
        assertEquals("127.0.0.1 localhost\n::1 localhost\n", File(paths.rootfs, "etc/hosts").readText())
        assertTrue(File(paths.rootfs, "root/profile").isDirectory)
        assertTrue(File(paths.rootfs, "root/logs").isDirectory)
        assertEquals(
            "--disable-session-crashed-bubble\n--hide-crash-restore-bubble\n",
            File(paths.data, "chromium.flags").readText(),
        )

        assertEquals("0.1.0", config.load().rootfsVersion)
        assertEquals(manifestUrl, config.load().mirrorUrl)
        assertTrue(config.load().startOnBoot)
        assertFalse(tarball.exists())
        assertFalse(File(tarball.path + ".part").exists())
        assertTrue(installer.isInstalled())
    }

    @Test
    fun `a checksum mismatch fails and never calls the runner`() = runBlocking {
        val badSha = "0".repeat(64)
        val badJson =
            """{"version":"0.1.0","file":"$file","size":${tarballBytes.size},"sha256":"$badSha","url":"$base/$file"}"""
        server.createContext("/manifest-bad.json") { exchange -> serve(exchange, badJson.toByteArray(), "application/json") }

        val installer = RootfsInstaller(paths, fakeRunner, Downloader(), config)
        installer.install("$base/manifest-bad.json")

        val state = installer.state.value
        assertTrue("expected Failed, got $state", state is InstallState.Failed)
        assertTrue((state as InstallState.Failed).message.contains("checksum"))
        assertTrue(fakeRunner.calls.isEmpty())
        val tarball = File(paths.data, file)
        assertFalse(tarball.exists())
        assertFalse(File(tarball.path + ".part").exists())
    }

    @Test
    fun `extraction reports done and total entries`() = runBlocking {
        fakeRunner.stdoutLines = 10
        fakeRunner.simulateExtraction = {
            val env = File(paths.rootfs, "usr/bin/env")
            env.parentFile?.mkdirs()
            env.writeText("env")
        }
        val withEntries = manifestJson.replaceFirst(
            """{"version":"0.1.0","file":""",
            """{"version":"0.1.0","entries":10,"file":""",
        )
        server.createContext("/entries/manifest.json") {
            exchange -> serve(exchange, withEntries.toByteArray(), "application/json")
        }
        server.createContext("/entries/$file") { exchange -> serve(exchange, tarballBytes, "application/octet-stream") }

        val installer = RootfsInstaller(paths, fakeRunner, Downloader(), config)
        val observed = mutableListOf<InstallState>()
        val collectJob = launch { installer.state.collect { observed += it } }
        installer.install("$base/entries/manifest.json")

        assertTrue(
            "expected an Extracting(done=10,total=10) before Installed, saw: $observed",
            observed.any { it is InstallState.Extracting && it.done == 10L && it.total == 10L },
        )
        assertEquals(InstallState.Installed("0.1.0"), installer.state.value)
        collectJob.cancel()
    }

    @Test
    fun `uninstall removes the rootfs and clears the version`() = runBlocking {
        val env = File(paths.rootfs, "usr/bin/env")
        env.parentFile?.mkdirs()
        env.writeText("env")
        val resolv = File(paths.rootfs, "etc/resolv.conf")
        resolv.parentFile?.mkdirs()
        resolv.writeText("nameserver 1.1.1.1\n")
        config.save(config.load().copy(rootfsVersion = "0.1.0"))
        val installer = RootfsInstaller(paths, fakeRunner, Downloader(), config)
        assertTrue(installer.isInstalled())

        installer.uninstall()

        assertFalse(File(paths.rootfs, "usr/bin/env").exists())
        assertEquals("", config.load().rootfsVersion)
        assertEquals(InstallState.Idle, installer.state.value)
        assertFalse(installer.isInstalled())
    }

    private fun serve(exchange: HttpExchange, body: ByteArray, contentType: String) {
        try {
            exchange.responseHeaders.add("Content-Type", contentType)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        } finally {
            exchange.close()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}