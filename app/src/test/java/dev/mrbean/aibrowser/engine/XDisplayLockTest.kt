package dev.mrbean.aibrowser.engine

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class XDisplayLockTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun lock(value: String): File =
        File(tmp.root, "tmp/.X1-lock").apply { parentFile.mkdirs(); writeText(value) }

    @Test fun `removes dead owner lock and socket`() {
        val lock = lock("     12345\n")
        val socket = File(tmp.root, "tmp/.X11-unix/X1").apply {
            parentFile.mkdirs(); writeText("")
        }
        assertTrue(XDisplayLock.recover(tmp.root) { false })
        assertFalse(lock.exists())
        assertFalse(socket.exists())
    }

    @Test fun `preserves live owner`() {
        val lock = lock("12345")
        assertFalse(XDisplayLock.recover(tmp.root) { true })
        assertTrue(lock.exists())
    }

    @Test fun `preserves malformed and invalid locks`() {
        for (value in listOf("", "invalid", "0", "-1")) {
            val lock = lock(value)
            assertFalse(XDisplayLock.recover(tmp.root) { false })
            assertTrue(lock.exists())
        }
    }

    @Test fun `missing lock is harmless`() {
        assertFalse(XDisplayLock.recover(tmp.root) { false })
    }
}
