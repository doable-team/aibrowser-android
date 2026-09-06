package dev.mrbean.aibrowser.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Lifecycle state of one service. */
sealed class ServiceState {
    /** The service cannot run, e.g. `tunnel` without a token. */
    data class Disabled(val reason: String) : ServiceState()

    /** The loop is about to start (or restart) the process. */
    data object Starting : ServiceState()

    /** The process has been started and is running; [sinceMs] is its start time. */
    data class Running(val sinceMs: Long) : ServiceState()

    /** The process exited unexpectedly; a retry is scheduled for [retryAtMs]. */
    data class Backoff(val retryAtMs: Long, val attempt: Int, val lastExit: Int) : ServiceState()

    /** Stopped on purpose. */
    data object Stopped : ServiceState()
}

data class ServiceStatus(
    val def: ServiceDef,
    val state: ServiceState,
    val restarts: Int,
    val lastLines: List<String>,
)

/**
 * Runs one child process per service (a proot session per service, started with
 * `--kill-on-exit`, so destroying the proot process ends the service). A loop
 * per started service restarts the process on unexpected exit with a
 * 1, 2, 4, 8, 16, 30, 30, ... second backoff; every state change is written
 * atomically to `data/state.json` for the in-rootfs status service.
 *
 * Pure Kotlin + coroutines (no Android classes) so it is unit-testable. State
 * mutation is serialized with a [Mutex]; log writes use a plain lock because
 * they happen from the non-suspend [ProcessRunner.onLine] callback.
 */
class ServiceSupervisor(
    private val paths: Paths,
    private val runner: ProcessRunner,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private companion object {
        const val START_GAP_MS = 1_500L
        const val RESET_AFTER_RUNNING_MS = 60_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val MAX_LOG_BYTES = 2 * 1024 * 1024L
        const val RING_CAPACITY = 300
        const val TUNNEL_SERVICE = "tunnel"
        const val TUNNEL_TOKEN_FILE = "tunnel.token"
    }

    private val mutex = Mutex()
    private val logLock = ReentrantLock()

    private val _statuses = MutableStateFlow<Map<String, ServiceStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, ServiceStatus>> = _statuses.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()
    private val stoppedOnPurpose = mutableMapOf<String, Boolean>()
    private val restarts = mutableMapOf<String, Int>()
    private val rings = mutableMapOf<String, LogRing>()

    private val json = Json { ignoreUnknownKeys = true }

    /** Starts one service; `tunnel` without a token becomes [ServiceState.Disabled]. */
    suspend fun start(name: String) {
        mutex.withLock {
            if (name == TUNNEL_SERVICE && !hasTunnelToken()) {
                jobs.remove(name)?.cancel()
                stoppedOnPurpose[name] = false
                updateStatusLocked(name, ServiceState.Disabled("no tunnel token"))
                return
            }
            if (jobs[name]?.isActive == true) return
            stoppedOnPurpose[name] = false
            jobs[name] = launchLoop(name)
        }
    }

    /** Stops one service: cancels its loop, destroys the process, state [ServiceState.Stopped]. */
    suspend fun stop(name: String) {
        mutex.withLock {
            if (_statuses.value[name]?.state is ServiceState.Disabled) return
            stoppedOnPurpose[name] = true
            jobs.remove(name)?.cancel()
            updateStatusLocked(name, ServiceState.Stopped)
        }
    }

    suspend fun restart(name: String) {
        stop(name)
        start(name)
    }

    /** Starts every service in table order with a 1.5 s gap between starts. */
    fun startAll() {
        scope.launch {
            for (def in Services.all) {
                start(def.name)
                delay(START_GAP_MS)
            }
        }
    }

    /** Stops every service in reverse table order. */
    fun stopAll() {
        scope.launch {
            for (def in Services.all.asReversed()) {
                stop(def.name)
            }
        }
    }

    fun restartAll() {
        scope.launch {
            for (def in Services.all.asReversed()) {
                stop(def.name)
            }
            for (def in Services.all) {
                start(def.name)
                delay(START_GAP_MS)
            }
        }
    }

    /** Last up-to-[RING_CAPACITY] lines of a service's stdout/stderr. */
    fun logLines(name: String): List<String> = snapshotRing(name)

    private fun launchLoop(name: String): Job = scope.launch {
        var attempt = 0
        while (isActive) {
            setState(name, ServiceState.Starting)
            yield()
            val startedAt = clock()
            setState(name, ServiceState.Running(startedAt))
            val spec = ProotCommand.build(
                paths = paths,
                rootfsDir = paths.rootfs,
                workingDir = "/root",
                command = listOf("sh", Services.scriptPath(name)),
            )
            val result = try {
                runner.run(spec, onLine = { line, _ -> appendLog(name, line) })
            } catch (e: CancellationException) {
                // The loop was cancelled (stop(), or the supervisor scope died);
                // stop() already recorded the Stopped state.
                break
            }
            if (stoppedOnPurpose[name] == true) break
            if (clock() - startedAt >= RESET_AFTER_RUNNING_MS) attempt = 0
            val delayMs = delayForAttempt(attempt + 1)
            attempt += 1
            restarts[name] = (restarts[name] ?: 0) + 1
            setState(name, ServiceState.Backoff(clock() + delayMs, attempt, result.exitCode))
            delay(delayMs)
        }
    }

    private suspend fun setState(name: String, state: ServiceState) {
        mutex.withLock {
            updateStatusLocked(name, state)
        }
    }

    /** Assumes the mutex is held. */
    private fun updateStatusLocked(name: String, state: ServiceState) {
        val def = Services.all.first { it.name == name }
        val now = clock()
        _statuses.value = _statuses.value + (name to ServiceStatus(
            def = def,
            state = state,
            restarts = restarts[name] ?: 0,
            lastLines = snapshotRing(name),
        ))
        writeStateFileLocked(now)
    }

    private fun writeStateFileLocked(now: Long) {
        val services = _statuses.value.mapValues { (_, status) ->
            val stateKey = when (status.state) {
                is ServiceState.Running -> "run"
                is ServiceState.Disabled -> "disabled"
                else -> "down"
            }
            val since = when (val s = status.state) {
                is ServiceState.Running -> s.sinceMs / 1000
                else -> now / 1000
            }
            StateFileEntry(stateKey, since)
        }
        paths.data.mkdirs()
        val file = File(paths.data, "state.json")
        val temp = File(paths.data, "state.json.tmp")
        temp.writeText(json.encodeToString(StateFile(services)))
        // renameTo is atomic on the same filesystem; copy as a fallback.
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }

    private fun appendLog(name: String, line: String) {
        logLock.withLock {
            val ring = rings.getOrPut(name) { LogRing(RING_CAPACITY) }
            ring.add(line)
            val file = File(paths.logs, "$name.log")
            if (file.length() > MAX_LOG_BYTES) {
                val rotated = File(paths.logs, "$name.log.1")
                rotated.delete()
                file.renameTo(rotated)
            }
            file.appendText(line + "\n")
        }
    }

    private fun snapshotRing(name: String): List<String> = logLock.withLock {
        rings[name]?.snapshot() ?: emptyList()
    }

    private fun hasTunnelToken(): Boolean {
        val file = File(paths.data, TUNNEL_TOKEN_FILE)
        return file.isFile && file.readText().isNotBlank()
    }

    private fun delayForAttempt(attempt: Int): Long = when {
        attempt <= 5 -> (1L shl (attempt - 1)) * 1000
        else -> MAX_BACKOFF_MS
    }

    /** Bounded in-memory line ring. */
    private class LogRing(private val capacity: Int) {
        private val deque = ArrayDeque<String>(capacity)
        fun add(line: String) {
            if (deque.size >= capacity) deque.removeFirst()
            deque.addLast(line)
        }
        fun snapshot(): List<String> = deque.toList()
    }

    @Serializable
    private data class StateFileEntry(val state: String, val since: Long)

    @Serializable
    private data class StateFile(val services: Map<String, StateFileEntry>)
}