package dev.mrbean.aibrowser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ChromiumFlagsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `creates the default flags when the file is missing`() {
        val data = tmp.newFolder("data")

        ChromiumFlags.ensure(data)

        assertEquals(ChromiumFlags.DEFAULT_FLAGS, File(data, "chromium.flags").readText())
    }

    @Test
    fun `a file with only the old flags gains the proxy flags and keeps an operator line`() {
        val data = tmp.newFolder("data")
        val file = File(data, "chromium.flags")
        file.writeText("--disable-session-crashed-bubble\n--hide-crash-restore-bubble\n--foo=bar\n")

        ChromiumFlags.ensure(data)

        // The operator's line survives; every default line that was missing is
        // appended, whatever the current default set is.
        val written = file.readText()
        assertEquals(
            "--disable-session-crashed-bubble\n--hide-crash-restore-bubble\n--foo=bar\n",
            written.lines().filter { it == "--foo=bar" || it.contains("crash") }.joinToString("\n", postfix = "\n"),
        )
        for (line in ChromiumFlags.DEFAULT_FLAGS.trimEnd('\n').split("\n")) {
            assertTrue(written.lines().contains(line))
        }
    }

    @Test
    fun `an up-to-date file is not rewritten`() {
        val data = tmp.newFolder("data")
        val file = File(data, "chromium.flags")
        file.writeText(ChromiumFlags.DEFAULT_FLAGS)
        val before = file.lastModified()

        ChromiumFlags.ensure(data)

        assertEquals(before, file.lastModified())
        assertEquals(ChromiumFlags.DEFAULT_FLAGS, file.readText())
    }
}