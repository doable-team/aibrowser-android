package dev.mrbean.aibrowser.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class RunResult(
    val exitCode: Int,
    val timedOut: Boolean,
)

/**
 * Runs [ProcessSpec] with [ProcessBuilder], pumping stdout and stderr line by
 * line to [onLine] from coroutines. An optional timeout destroys the process.
 * Cancellation destroys the process too (escalating to `destroyForcibly` after
 * five seconds) and then rethrows [CancellationException], so a cancelled run
 * never leaves a child process behind.
 */
open class ProcessRunner {

    open suspend fun run(
        spec: ProcessSpec,
        cwd: File? = null,
        timeoutMs: Long? = null,
        onLine: (line: String, isStderr: Boolean) -> Unit = { _, _ -> },
    ): RunResult = withContext(Dispatchers.IO) {
        val builder = ProcessBuilder(spec.argv)
        builder.environment().clear()
        builder.environment().putAll(spec.env)
        if (cwd != null) builder.directory(cwd)
        val process = try {
            builder.start()
        } catch (e: IOException) {
            onLine(e.message ?: e.javaClass.simpleName, true)
            return@withContext RunResult(exitCode = -1, timedOut = false)
        }

        coroutineScope {
            val stdout = launch {
                runCatching { process.inputStream.bufferedReader().forEachLine { onLine(it, false) } }
            }
            val stderr = launch {
                runCatching { process.errorStream.bufferedReader().forEachLine { onLine(it, true) } }
            }

            val deadline = if (timeoutMs != null) System.nanoTime() + timeoutMs * 1_000_000L else null
            var timedOut = false
            try {
                while (process.isAlive) {
                    if (!currentCoroutineContext().isActive) {
                        destroy(process)
                        throw CancellationException()
                    }
                    if (deadline != null && System.nanoTime() >= deadline) {
                        destroy(process)
                        timedOut = true
                        break
                    }
                    delay(25)
                }
            } catch (e: CancellationException) {
                destroy(process)
                throw e
            }

            stdout.join()
            stderr.join()
            RunResult(process.exitValue(), timedOut)
        }
    }

    /** Runs a host command (outside any rootfs); used by the self-test. */
    open suspend fun runHost(
        cwd: File,
        argv: List<String>,
        env: Map<String, String>,
        timeoutMs: Long? = null,
        onLine: (line: String, isStderr: Boolean) -> Unit = { _, _ -> },
    ): RunResult = run(ProcessSpec(argv, env), cwd, timeoutMs, onLine)

    private fun destroy(process: Process) {
        process.destroy()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor()
        }
    }
}