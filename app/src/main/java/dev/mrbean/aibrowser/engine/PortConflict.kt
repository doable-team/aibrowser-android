package dev.mrbean.aibrowser.engine

/**
 * Recognises a "the port is taken" exit in a service's output. A service whose
 * port belongs to another process can never come up on its own, so the
 * supervisor stops it instead of retrying forever.
 */
object PortConflict {
    /** Shown on the service card when a service gave up for this reason. */
    const val REASON = "port already in use — another app may be holding it"

    private val markers = listOf(
        "address already in use",
        "address in use",
        "eaddrinuse",
        "failed to bind",
        "cannot bind",
        "could not bind",
    )

    /** True when the tail of [lines] carries a bind failure. */
    fun detected(lines: List<String>): Boolean {
        // Only the tail matters: the port complaint appears right before the
        // process exits, and older, unrelated output must not trigger it.
        return lines.takeLast(30).any { line ->
            val lower = line.lowercase()
            markers.any { lower.contains(it) }
        }
    }
}