package dev.mrbean.aibrowser.ui

import dev.mrbean.aibrowser.engine.CommandResult
import dev.mrbean.aibrowser.engine.PrepareReport
import dev.mrbean.aibrowser.engine.SelfTestReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfTestSummaryTest {

    @Test
    fun `passed report yields PASSED with links version and details`() {
        val links = (1..16).map { "bin/tool$it" to "libtool$it.so" }
        val proot = CommandResult(0, "|__| |__|__\\_____/\\_____/\\____| 5.1.0\n", "")
        val busybox = CommandResult(0, "ok\n", "")
        val summary = summarize(PrepareReport(links, emptyList()), SelfTestReport(proot, busybox))

        assertEquals(SelfTestState.PASSED, summary.state)
        assertEquals(16, summary.links)
        assertEquals("5.1.0", summary.prootVersion)
        assertTrue(summary.details.contains("Prepared 16 symlinks"))
        assertTrue(summary.details.contains("proot --version (exit 0)"))
    }

    @Test
    fun `failed report yields FAILED`() {
        val report = PrepareReport(emptyList(), emptyList())
        val proot = CommandResult(1, "", "proot: not installed\n")
        val busybox = CommandResult(1, "", "busybox: command not found\n")
        val summary = summarize(report, SelfTestReport(proot, busybox))

        assertEquals(SelfTestState.FAILED, summary.state)
        assertEquals(0, summary.links)
        assertNull(summary.prootVersion)
        assertTrue(summary.details.contains("proot: not installed"))
        assertTrue(summary.details.contains("busybox: command not found"))
    }

    @Test
    fun `null reports yield RUNNING`() {
        val summary = summarize(null, null)

        assertEquals(SelfTestState.RUNNING, summary.state)
        assertEquals(0, summary.links)
        assertNull(summary.prootVersion)
        assertTrue(summary.details.isEmpty())
    }

    @Test
    fun `proot output without a version falls back to null`() {
        val proot = CommandResult(0, "proot\n", "")
        val busybox = CommandResult(0, "ok\n", "")
        val summary = summarize(PrepareReport(emptyList(), emptyList()), SelfTestReport(proot, busybox))

        assertEquals(SelfTestState.PASSED, summary.state)
        assertNull(summary.prootVersion)
    }
}