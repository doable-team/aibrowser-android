package dev.mrbean.aibrowser.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
     *  marks [destroyed] (the runner's destroy path). When [processTree] is
     *  set it reports the fake proot pid through [onStarted] and completes as
     *  soon as the fake tree receives SIGTERM on the guest root. */
    class FakeRunner : ProcessRunner() {
        val calls = mutableListOf<ProcessSpec>()
        var exitCode = 0
        var exitDelayMs = 0L
        var linesToEmit = 0
        var destroyed = false
        var processTree: FakeProcessTree? = null

        override suspend fun run(
            spec: ProcessSpec,
            cwd: File?,
            timeoutMs: Long?,
            onStarted: (pid: Int?) -> Unit,
            onLine: (line: String, isStderr: Boolean) -> Unit,
        ): RunResult {
            calls.add(spec)
            destroyed = false
            val tree = processTree
            onStarted(tree?.prootPid)
            try {
                if (tree != null) {
                    val startNanos = System.nanoTime()
                    while (currentCoroutineContext().isActive &&
                        !tree.sigtermOnGuestRoot() &&
                        exitDelayMs > 0 &&
                        System.nanoTime() - startNanos < exitDelayMs * 1_000_000L
                    ) {
                        delay(10)
                    }
                } else {
                    delay(exitDelayMs)
                }
                repeat(linesToEmit) { onLine("line-$it", false) }
                return RunResult(exitCode, false)
            } catch (e: CancellationException) {
                destroyed = true
                throw e
            }
        }
    }

    /** A [ProcessTree] with a fixed proot -> guest root -> grand-child topology. */
    class FakeProcessTree : ProcessTree {
        val prootPid = 4_200_001
        val guestRootPid = 4_200_002
        val grandChildPid = 4_200_003
        val signaled = mutableListOf<Pair<Int, Int>>()
        val children: Map<Int, List<Int>> = mapOf(
            prootPid to listOf(guestRootPid),
            guestRootPid to listOf(grandChildPid),
        )

        override fun childrenOf(pid: Int): List<Int> = children[pid] ?: emptyList()

        override fun signal(pid: Int, signal: Int) {
            signaled.add(pid to signal)
        }

        fun sigtermOnGuestRoot(): Boolean = signaled.any { it.first == guestRootPid && it.second == 15 }
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
    fun `stop signals SIGTERM to the guest root and ends Stopped without backoff`() = runTest {
        runner.exitDelayMs = 10_000
        val tree = FakeProcessTree()
        runner.processTree = tree
        val supervisor = ServiceSupervisor(
            paths, runner, backgroundScope,
            clock = { testScheduler.currentTime },
            processTree = tree,
        )

        supervisor.start("gate")
        runCurrent()
        assertTrue(supervisor.statuses.value["gate"]?.state is ServiceState.Running)

        supervisor.stop("gate")
        runCurrent()

        assertTrue(
            "expected SIGTERM to the guest root, got ${tree.signaled}",
            tree.signaled.contains(tree.guestRootPid to 15),
        )
        assertEquals(ServiceState.Stopped, supervisor.statuses.value["gate"]?.state)
        assertEquals(0, supervisor.statuses.value["gate"]?.restarts)
    }

    @Test
    fun `an unexpected exit SIGKILLs the remaining guest tree before backing off`() = runTest {
        runner.exitCode = 1
        runner.exitDelayMs = 0
        val tree = FakeProcessTree()
        runner.processTree = tree
        val supervisor = ServiceSupervisor(
            paths, runner, backgroundScope,
            clock = { testScheduler.currentTime },
            processTree = tree,
        )

        supervisor.start("gate")
        runCurrent()

        assertEquals(
            listOf(tree.guestRootPid to 9, tree.grandChildPid to 9),
            tree.signaled.filter { it.second == 9 },
        )
        assertTrue(supervisor.statuses.value["gate"]?.state is ServiceState.Backoff)
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

    @Test
    fun `startAll refuses when the rootfs is missing`() = runTest {
        val supervisor = ServiceSupervisor(paths, runner, backgroundScope, clock = { testScheduler.currentTime })

        assertFalse(supervisor.startAll())
        assertTrue(runner.calls.isEmpty())
        assertTrue(supervisor.statuses.value.isEmpty())
    }

    @Test
    fun `startAll returns true when the rootfs is present`() = runTest {
        val env = File(paths.rootfs, "usr/bin/env")
        env.parentFile?.mkdirs()
        env.writeText("env")
        val supervisor = ServiceSupervisor(paths, runner, backgroundScope, clock = { testScheduler.currentTime })

        assertTrue(supervisor.startAll())
    }

    @Test
    fun `oversized log files rotate at supervisor start`() = runTest {
        val gateLog = File(paths.logs, "gate.log")
        gateLog.writeText("x".repeat((2L * 1024 * 1024).toInt() + 1))

        ServiceSupervisor(paths, runner, backgroundScope, clock = { testScheduler.currentTime })

        assertFalse(gateLog.exists())
        assertTrue(File(paths.logs, "gate.log.1").exists())
    }

    @Test
    fun `supervisor start creates the chromium flags defaults when missing`() = runTest {
        ServiceSupervisor(paths, runner, backgroundScope, clock = { testScheduler.currentTime })

        val flags = File(paths.data, "chromium.flags")
        assertTrue(flags.isFile)
        assertEquals("--disable-session-crashed-bubble\n--hide-crash-restore-bubble\n", flags.readText())
    }

    @Test
    fun `clearLog empties the ring and truncates the log file`() = runTest {
        runner.exitDelayMs = 0
        runner.linesToEmit = 10
        val supervisor = ServiceSupervisor(paths, runner, backgroundScope, clock = { testScheduler.currentTime })
        supervisor.start("gate")
        runCurrent()
        assertEquals(10, supervisor.logLines("gate").size)
        assertEquals(10, File(paths.logs, "gate.log").readLines().size)

        supervisor.clearLog("gate")

        assertTrue(supervisor.logLines("gate").isEmpty())
        assertEquals("", File(paths.logs, "gate.log").readText())
    }
}