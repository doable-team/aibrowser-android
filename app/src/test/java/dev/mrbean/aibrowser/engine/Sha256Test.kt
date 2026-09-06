package dev.mrbean.aibrowser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Sha256Test {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `hashes a file and reports progress`() {
        val file = File(tmp.root, "abc.txt")
        file.writeText("abc")

        val calls = mutableListOf<Long>()
        val hex = Sha256.hex(file) { calls.add(it) }

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex)
        assertTrue(calls.isNotEmpty())
        assertEquals(3L, calls.last())
    }
}