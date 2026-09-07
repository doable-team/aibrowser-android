package dev.mrbean.aibrowser.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class AppConfig(
    val startOnBoot: Boolean = false,
    val mcpHost: String = "",
    val statusHost: String = "",
    val viewerHost: String = "",
    val setupComplete: Boolean = false,
    val mirrorUrl: String = "",
    val viewOnly: Boolean = true,
    val rootfsVersion: String = "",
    val oemBatteryDone: Boolean = false,
)

/** Reads and writes `data/config.json`; missing or corrupt files yield defaults. */
class ConfigStore(private val dataDir: File) {

    private val file: File get() = File(dataDir, "config.json")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): AppConfig {
        dataDir.mkdirs()
        return runCatching { json.decodeFromString<AppConfig>(file.readText()) }
            .getOrDefault(AppConfig())
    }

    fun save(config: AppConfig) {
        dataDir.mkdirs()
        val temp = File(dataDir, "config.json.tmp")
        temp.writeText(json.encodeToString(config))
        // renameTo is atomic on the same filesystem; copy as a fallback.
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }
}