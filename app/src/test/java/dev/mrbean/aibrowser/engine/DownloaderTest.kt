package dev.mrbean.aibrowser.engine

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetSocketAddress
import java.util.Random

class DownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: HttpServer
    private lateinit var base: String
    private val data = ByteArray(300 * 1024)

    @Volatile
    private var sawRange = false

    @Volatile
    private var lastRange: String? = null

    @Before
    fun setUp() {
        Random(42).nextBytes(data)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/range") { exchange -> handleRange(exchange) }
        server.createContext("/no-range") { exchange -> handleNoRange(exchange) }
        server.createContext("/missing") { exchange ->
            try {
                exchange.sendResponseHeaders(404, -1)
            } finally {
                exchange.close()
            }
        }
        server.createContext("/redirect") { exchange ->
            try {
                exchange.responseHeaders.add("Location", "/range")
                exchange.sendResponseHeaders(302, -1)
            } finally {
                exchange.close()
            }
        }
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `downloads the full body`() = runBlocking {
        val target = File(tmp.root, "rootfs.tar.xz")

        Downloader().download("$base/range", target)

        assertArrayEquals(data, target.readBytes())
        assertTrue(!File(target.path + ".part").exists())
    }

    @Test
    fun `resumes from an existing part file with a range request`() = runBlocking {
        val target = File(tmp.root, "rootfs.tar.xz")
        File(target.path + ".part").writeBytes(data.copyOfRange(0, 100 * 1024))

        Downloader().download("$base/range", target)

        assertArrayEquals(data, target.readBytes())
        assertTrue(sawRange)
        assertEquals("bytes=102400-", lastRange)
    }

    @Test
    fun `restarts when the server ignores the range request`() = runBlocking {
        val target = File(tmp.root, "rootfs.tar.xz")
        File(target.path + ".part").writeBytes(data.copyOfRange(0, 100 * 1024))

        Downloader().download("$base/no-range", target)

        assertArrayEquals(data, target.readBytes())
        assertTrue(sawRange)
    }

    @Test
    fun `throws a DownloadException on 404`() = runBlocking {
        val target = File(tmp.root, "rootfs.tar.xz")

        try {
            Downloader().download("$base/missing", target)
            fail("expected DownloadException")
        } catch (e: DownloadException) {
            assertEquals(404, e.status)
        }
    }

    @Test
    fun `follows redirects`() = runBlocking {
        val target = File(tmp.root, "rootfs.tar.xz")

        Downloader().download("$base/redirect", target)

        assertArrayEquals(data, target.readBytes())
    }

    private fun handleRange(exchange: HttpExchange) {
        try {
            val range = exchange.requestHeaders.getFirst("Range")
            if (range == null) {
                exchange.sendResponseHeaders(200, data.size.toLong())
                exchange.responseBody.use { it.write(data) }
            } else {
                val start = Regex("bytes=(\\d+)-").find(range)?.groupValues?.get(1)?.toLong()
                if (start == null) {
                    exchange.sendResponseHeaders(400, -1)
                } else {
                    sawRange = true
                    lastRange = range
                    val from = start.toInt()
                    exchange.responseHeaders.add("Content-Range", "bytes $start-${data.size - 1}/${data.size}")
                    exchange.sendResponseHeaders(206, (data.size - from).toLong())
                    exchange.responseBody.use { it.write(data, from, data.size - from) }
                }
            }
        } finally {
            exchange.close()
        }
    }

    private fun handleNoRange(exchange: HttpExchange) {
        try {
            sawRange = exchange.requestHeaders.containsKey("Range")
            exchange.sendResponseHeaders(200, data.size.toLong())
            exchange.responseBody.use { it.write(data) }
        } finally {
            exchange.close()
        }
    }
}