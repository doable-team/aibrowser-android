package dev.mrbean.aibrowser.ui

import dev.mrbean.aibrowser.engine.PrepareReport
import dev.mrbean.aibrowser.engine.SelfTestReport

enum class SelfTestState { RUNNING, PASSED, FAILED }

data class SelfTestSummary(
    val state: SelfTestState,
    val links: Int,
    val prootVersion: String?,
    val details: String,
)

/**
 * Reduces the prepare and self-test reports into the summary shown on the first
 * onboarding step. A null report means the check has not finished yet (RUNNING);
 * otherwise the self-test decides PASSED vs FAILED and [details] holds the raw output.
 */
fun summarize(report: PrepareReport?, selfTest: SelfTestReport?): SelfTestSummary {
    if (report == null || selfTest == null) return SelfTestSummary(SelfTestState.RUNNING, 0, null, "")
    val passed = selfTest.proot.exitCode == 0 && selfTest.busybox.exitCode == 0
    return SelfTestSummary(
        state = if (passed) SelfTestState.PASSED else SelfTestState.FAILED,
        links = report.linksCreated.size,
        prootVersion = prootVersionOf(selfTest),
        details = buildString {
            appendLine("Prepared ${report.linksCreated.size} symlinks.")
            if (report.missingNativeFiles.isNotEmpty()) {
                appendLine("MISSING native files: ${report.missingNativeFiles.joinToString()}")
            }
            if (report.errors.isNotEmpty()) {
                appendLine("ERRORS:")
                report.errors.forEach { appendLine("  $it") }
            }
            appendLine("proot --version (exit ${selfTest.proot.exitCode}):")
            append(selfTest.proot.stdout)
            append(selfTest.proot.stderr)
            appendLine("busybox echo ok (exit ${selfTest.busybox.exitCode}):")
            append(selfTest.busybox.stdout)
            append(selfTest.busybox.stderr)
        },
    )
}

/**
 * Parses the version from the first line of proot's output that contains a
 * digit-dot-digit, e.g. the banner "|__| |__|__\_____/\_____/\____| 5.1.0".
 */
fun prootVersionOf(selfTest: SelfTestReport): String? {
    val lines = selfTest.proot.stdout.lineSequence() + selfTest.proot.stderr.lineSequence()
    val versionLine = lines.firstOrNull { VERSION_MARKER.containsMatchIn(it) } ?: return null
    return VERSION.find(versionLine)?.value
}

private val VERSION_MARKER = Regex("\\d\\.\\d")
private val VERSION = Regex("\\d+(\\.\\d+)+")