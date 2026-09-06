package dev.mrbean.aibrowser.engine

import java.io.File

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
)

data class SelfTestReport(
    val proot: CommandResult,
    val busybox: CommandResult,
)

/** Runs `bin/proot --version` and `bin/busybox echo ok` with the proot env. */
class SelfTest(
    private val paths: Paths,
    private val runner: ProcessRunner,
) {

    suspend fun run(): SelfTestReport {
        val env = prootEnv(paths)
        return SelfTestReport(
            proot = runHost(listOf(File(paths.bin, "proot").absolutePath, "--version"), env),
            busybox = runHost(listOf(File(paths.bin, "busybox").absolutePath, "echo", "ok"), env),
        )
    }

    private suspend fun runHost(argv: List<String>, env: Map<String, String>): CommandResult {
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val result = runner.runHost(paths.root, argv, env, timeoutMs = 30_000) { line, isStderr ->
            (if (isStderr) stderr else stdout).append(line).append('\n')
        }
        return CommandResult(result.exitCode, stdout.toString(), stderr.toString(), result.timedOut)
    }
}