package dev.mrbean.aibrowser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NoVncIndexTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `writes an index html that redirects to vnc html`() {
        val rootfs = tmp.newFolder("rootfs")
        val novnc = File(rootfs, "usr/share/novnc")
        assertTrue(novnc.mkdirs())

        NoVncIndex.ensure(rootfs)

        val index = File(novnc, "index.html")
        assertTrue(index.isFile)
        assertTrue(index.readText().startsWith("<!doctype html>"))
        assertTrue(index.readText().contains("vnc.html?autoconnect=true"))
    }

    @Test
    fun `does not overwrite an existing index html a user edited`() {
        val rootfs = tmp.newFolder("rootfs")
        val novnc = File(rootfs, "usr/share/novnc")
        assertTrue(novnc.mkdirs())
        val index = File(novnc, "index.html")
        index.writeText("custom landing page")

        NoVncIndex.ensure(rootfs)
        NoVncIndex.ensure(rootfs)

        assertEquals("custom landing page", index.readText())
    }

    @Test
    fun `a rootfs without the novnc directory is left untouched and does not throw`() {
        val rootfs = tmp.newFolder("rootfs")

        NoVncIndex.ensure(rootfs)

        assertFalse(File(rootfs, "usr/share").exists())
    }
}