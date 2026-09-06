package dev.mrbean.aibrowser.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class RootfsManifestTest {

    private val sample = """{"version":"0.1.0","file":"aibrowser-rootfs-0.1.0-arm64.tar.xz","size":311713516,"sha256":"8558b472047960aba4723f02c7e85da005cfb9e2b42ddb9c22ce33e02b30436e","url":"https://github.com/mrbeandev/aibrowser-android/releases/download/rootfs-0.1.0/aibrowser-rootfs-0.1.0-arm64.tar.xz","minApp":"0.1.0","packages":{"chromium":"152.0.7977.82-1~deb13u1"}}"""

    @Test
    fun `parses the release manifest`() {
        val manifest = RootfsManifest.parse(sample)

        assertEquals("0.1.0", manifest.version)
        assertEquals("aibrowser-rootfs-0.1.0-arm64.tar.xz", manifest.file)
        assertEquals(311713516L, manifest.size)
        assertEquals("8558b472047960aba4723f02c7e85da005cfb9e2b42ddb9c22ce33e02b30436e", manifest.sha256)
        assertEquals(
            "https://github.com/mrbeandev/aibrowser-android/releases/download/rootfs-0.1.0/aibrowser-rootfs-0.1.0-arm64.tar.xz",
            manifest.url,
        )
        assertEquals("0.1.0", manifest.minApp)
        assertEquals(mapOf("chromium" to "152.0.7977.82-1~deb13u1"), manifest.packages)
    }

    @Test
    fun `tarballUrl uses the url for a non-directory manifest url`() {
        val manifest = RootfsManifest.parse(sample)

        assertEquals(manifest.url, manifest.tarballUrl("https://example.com/rootfs/manifest.json?token=abc"))
    }

    @Test
    fun `tarballUrl derives the asset from the manifest directory`() {
        val manifest = RootfsManifest.parse(sample)

        assertEquals(
            "https://mirror.mrbean.dev/aibrowser/aibrowser-rootfs-0.1.0-arm64.tar.xz",
            manifest.tarballUrl("https://mirror.mrbean.dev/aibrowser/manifest.json"),
        )
    }
}