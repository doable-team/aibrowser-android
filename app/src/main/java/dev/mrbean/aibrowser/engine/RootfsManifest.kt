package dev.mrbean.aibrowser.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * The rootfs release manifest fetched by the installer. [url] is the
 * canonical release asset URL; when the manifest was fetched from a plain
 * directory listing (a mirror), [tarballUrl] derives the asset URL from the
 * directory the manifest came from instead.
 */
@Serializable
data class RootfsManifest(
    val version: String,
    val file: String,
    val size: Long,
    val sha256: String,
    val url: String,
    val minApp: String = "",
    val packages: Map<String, String> = emptyMap(),
    val entries: Long = 0,
) {
    fun tarballUrl(manifestUrl: String): String =
        if (manifestUrl.endsWith(MANIFEST_NAME)) {
            manifestUrl.substringBeforeLast("/") + "/" + file
        } else {
            url
        }

    companion object {
        private const val MANIFEST_NAME = "/manifest.json"

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(json: String): RootfsManifest = this.json.decodeFromString(json)
    }
}