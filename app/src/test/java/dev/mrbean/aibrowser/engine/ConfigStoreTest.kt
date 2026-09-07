package dev.mrbean.aibrowser.engine

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ConfigStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `round-trips a config through the data directory`() {
        val store = ConfigStore(tmp.newFolder("data"))
        val config = AppConfig(
            startOnBoot = true,
            mcpHost = "mcp.mrbean.dev",
            statusHost = "status.mrbean.dev",
            viewerHost = "viewer.mrbean.dev",
            setupComplete = true,
            mirrorUrl = "https://mirror.example/rootfs.tar.xz",
            viewOnly = false,
            rootfsVersion = "0.1.0",
        )

        store.save(config)

        assertEquals(config, store.load())
    }

    @Test
    fun `returns defaults when the file is missing`() {
        val store = ConfigStore(tmp.newFolder("data"))

        assertEquals(AppConfig(), store.load())
    }

    @Test
    fun `returns defaults when the file is unreadable`() {
        val dataDir = tmp.newFolder("data")
        File(dataDir, "config.json").writeText("not json {")
        val store = ConfigStore(dataDir)

        assertEquals(AppConfig(), store.load())
    }
}