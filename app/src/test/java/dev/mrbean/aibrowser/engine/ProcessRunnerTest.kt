package dev.mrbean.aibrowser.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProcessRunnerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `runs a host command and reports its lines and exit code`() = runBlocking {
        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()

        val result = ProcessRunner().runHost(
            cwd = tmp.root,
            argv = listOf("/bin/sh", "-c", "echo out; echo err 1>&2; exit 3"),
            env = emptyMap(),
        ) { line, isStderr ->
            (if (isStderr) stderr else stdout).add(line)
        }

        assertEquals(listOf("out"), stdout)
        assertEquals(listOf("err"), stderr)
        assertEquals(3, result.exitCode)
        assertEquals(false, result.timedOut)
    }

    @Test
    fun `missing binary returns exit code -1 and a stderr line`() = runBlocking {
        val stderr = mutableListOf<String>()

        val result = ProcessRunner().runHost(
            cwd = tmp.root,
            argv = listOf("/definitely/not/a/real/binary", "arg"),
            env = emptyMap(),
        ) { line, isStderr ->
            if (isStderr) stderr.add(line)
        }

        assertEquals(-1, result.exitCode)
        assertEquals(false, result.timedOut)
        assertTrue(stderr.isNotEmpty())
    }

    @Test
    fun `timeout escalates to destroyForcibly`() = runBlocking {
        val result = ProcessRunner().runHost(
            cwd = tmp.root,
            argv = listOf("/bin/sh", "-c", "trap '' TERM; while :; do sleep 1; done"),
            env = emptyMap(),
            timeoutMs = 500,
        )

        assertTrue(result.timedOut)
    }

    @Test
    fun `pidOf returns a positive pid for a running process`() {
        val process = ProcessBuilder("/bin/sh", "-c", "sleep 2").start()
        try {
            val pid = ProcessRunner().pidOf(process)
            assertTrue("expected a positive pid, got $pid", pid != null && pid > 0)
        } finally {
            process.destroy()
            process.waitFor()
        }
    }
}