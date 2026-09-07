package dev.mrbean.aibrowser.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortConflictTest {

    @Test
    fun `vnc bind failure is detected`() {
        assertTrue(
            PortConflict.detected(
                listOf("(EE) vncExtInit: Failed to bind socket: Address already in use (98)"),
            ),
        )
    }

    @Test
    fun `python bind failure is detected`() {
        assertTrue(PortConflict.detected(listOf("OSError: [Errno 98] Address already in use")))
    }

    @Test
    fun `node bind failure is detected`() {
        assertTrue(
            PortConflict.detected(
                listOf("Error: listen EADDRINUSE: address already in use 127.0.0.1:18931"),
            ),
        )
    }

    @Test
    fun `a line further back than 30 lines is not detected`() {
        val lines = buildList {
            add("(EE) vncExtInit: Failed to bind socket: Address already in use (98)")
            repeat(30) { add("noise-$it") }
        }
        assertFalse(PortConflict.detected(lines))
    }

    @Test
    fun `ordinary output is not detected`() {
        assertFalse(PortConflict.detected(listOf("Connections: Accepted")))
    }
}