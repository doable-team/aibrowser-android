package dev.mrbean.aibrowser.engine

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetSocketAddress

class RootfsUpdatesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: HttpServer
    private lateinit var base: String
    private lateinit var paths: Paths
    private lateinit var config: ConfigStore

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
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
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `same version gives UpToDate`() = runBlocking {
        serveManifest("/manifest.json", manifestJson("0.1.0"))
        config.save(config.load().copy(rootfsVersion = "0.1.0"))

        val updates = RootfsUpdates(paths, Downloader(), config)
        updates.check("$base/manifest.json")

        assertEquals(UpdateStatus.UpToDate("0.1.0"), updates.status.value)
        assertTrue(File(paths.data, "rootfs-manifest-check.json").isFile)
        assertEquals("0.1.0", config.load().rootfsVersion)
    }

    @Test
    fun `nothing installed gives UpToDate`() = runBlocking {
        serveManifest("/manifest.json", manifestJson("0.1.0"))

        val updates = RootfsUpdates(paths, Downloader(), config)
        updates.check("$base/manifest.json")

        assertEquals(UpdateStatus.UpToDate("0.1.0"), updates.status.value)
    }

    @Test
    fun `different version gives Available with the size`() = runBlocking {
        serveManifest("/manifest.json", manifestJson("0.1.1"))
        config.save(config.load().copy(rootfsVersion = "0.1.0"))

        val updates = RootfsUpdates(paths, Downloader(), config)
        updates.check("$base/manifest.json")

        assertEquals(UpdateStatus.Available("0.1.1", 298_000_000L), updates.status.value)
    }

    @Test
    fun `a 404 gives Failed`() = runBlocking {
        server.createContext("/missing") { exchange ->
            try {
                exchange.sendResponseHeaders(404, -1)
            } finally {
                exchange.close()
            }
        }

        val updates = RootfsUpdates(paths, Downloader(), config)
        updates.check("$base/missing")

        val status = updates.status.value
        assertTrue("expected Failed, got $status", status is UpdateStatus.Failed)
    }

    private fun serveManifest(path: String, json: String) {
        server.createContext(path) { exchange ->
            try {
                val body = json.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            } finally {
                exchange.close()
            }
        }
    }

    private fun manifestJson(version: String): String =
        """{"version":"$version","file":"aibrowser-rootfs-$version-arm64.tar.xz","size":298000000,"sha256":"a","url":"http://example.invalid/aibrowser-rootfs-$version-arm64.tar.xz"}"""
}