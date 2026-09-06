package dev.mrbean.aibrowser.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceSupervisorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var paths: Paths
    private lateinit var runner: FakeRunner

    /** A [ProcessRunner] that simulates a process: it runs for [exitDelayMs],
     *  emits [linesToEmit] lines, then exits with [exitCode]; cancelling it
     *  marks [destroyed] (the runner's destroy path). */
    class FakeRunner : ProcessRunner() {
        val calls = mutableListOf<ProcessSpec>()
        var exitCode = 0
        var exitDelayMs = 0L
        var linesToEmit = 0
        var destroyed = false

        override suspend fun run(
            spec: ProcessSpec,
            cwd: File?,
            timeoutMs: Long?,
            onLine: (line: String, isStderr: Boolean) -> Unit,
        ): RunResult {
            calls.add(spec)
            destroyed = false
            try {
                delay(exitDelayMs)
                repeat(linesToEmit) { onLine("line-$it", false) }
                return RunResult(exitCode, false)
            } catch (e: CancellationException) {
                destroyed = true
                throw e
            }
        }
    }

    @Before
    fun setUp() {
        val root = tmp.newFolder("files")
        paths = Paths(
            root = root,
            bin = File(root, "bin"),
            lib = File(root, "lib"),
            tmp = File(root, "tmp"),
            prootTmp = File(root, "proot_tmp"),
            rootfs = File(root, "rootfs"),
            data = File(root, "data"),
            logs = File(root, "logs"),
            nativeLibraryDir = "/data/app/package/lib/arm64-v8a",
        )
        paths.ensureDirs()
        File(paths.data, "tunnel.token").delete()
        runner = FakeRunner()
    }

    private fun stateJson(): String = File(paths.data, "state.json").readText()

    @Test
    fun `start gate reaches Running, builds the proot argv and writes run state`() = runTest {
        runner.exitDelayMs = 10_000
        val supervisor = ServiceSupervisor(paths, runner, backgroundScope, clock = { testScheduler.currentTime })
        val seen = mutableListOf<ServiceState>()
        backgroundScope.launch {
            supervisor.statuses.collect { map ->
                map["gate"]?.state?.let { if (seen.lastOrNull() != it) seen.add(it) }
            }
        }

        supervisor.start("gate")
        runCurrent()

        assertEquals(listOf(ServiceState.Starting, ServiceState.Running(0)), seen)
        assertTrue(supervisor.statuses.value["gate"]?.state is ServiceState.Running)

        val argv = runner.calls.single().argv
        assertEquals(File(paths.bin, "proot").absolutePath, argv.first())
        assertEquals("sh", argv[argv.lastIndex - 1])
        assertEquals("/opt/aibrowser/services/gate.sh", argv.last())

        val json = stateJson()
        assertTrue(json.contains("\"gate\""))
        assertTrue(json.contains("\"run\""))
    }

    @Test
    fun `an immediate exit backs off with 1s then 2s delays`() = runTest {
        runner.exitCode = 1
        runner.exitDelayMs = 0
        val supervisor = ServiceSupervisor(paths, runner, backgroundScope, clock = { testScheduler.currentTime })

        supervisor.start("gate")
        runCurrent()

        assertEquals(ServiceState.Backoff(1000, 1, 1), supervisor.statuses.value["gate"]?.state)

        advanceTimeBy(1000)
        runCurrent()

        assertEquals(ServiceState.Backoff(3000, 2, 1), supervisor.statuses.value["gate"]?.state)
        assertEquals(2, supervisor.statuses.value["gate"]?.restarts)
    }

    @Test
    fun `stop destroys the process and writes down state`() = runTest {
        runner.exitDelayMs = 10_000
        val supervisor = ServiceSupervisor(paths, runner, backgroundScope, clock = { testScheduler.currentTime })

        supervisor.start("gate")
        runCurrent()
        assertTrue(supervisor.statuses.value["gate"]?.state is ServiceState.Running)

        supervisor.stop("gate")
        runCurrent()

        assertEquals(ServiceState.Stopped, supervisor.statuses.value["gate"]?.state)
        assertTrue("process was not destroyed", runner.destroyed)
        val json = stateJson()
        assertTrue(json.contains("\"gate\""))
        assertTrue(json.contains("\"down\""))
    }

    @Test
    fun `tunnel without a token becomes disabled and nothing runs`() = runTest {
        val supervisor = ServiceSupervisor(paths, runner, backgroundScope, clock = { testScheduler.currentTime })

        supervisor.start("tunnel")
        runCurrent()

        assertEquals(ServiceState.Disabled("no tunnel token"), supervisor.statuses.value["tunnel"]?.state)
        assertTrue(runner.calls.isEmpty())
        val json = stateJson()
        assertTrue(json.contains("\"tunnel\""))
        assertTrue(json.contains("\"disabled\""))
    }

    @Test
    fun `the log ring keeps at most 300 lines and the file receives them all`() = runTest {
        runner.exitDelayMs = 0
        runner.linesToEmit = 400
        val supervisor = ServiceSupervisor(paths, runner, backgroundScope, clock = { testScheduler.currentTime })

        supervisor.start("gate")
        runCurrent()

        val ring = supervisor.logLines("gate")
        assertEquals(300, ring.size)
        assertEquals("line-399", ring.last())
        assertEquals(400, File(paths.logs, "gate.log").readLines().size)
    }
}