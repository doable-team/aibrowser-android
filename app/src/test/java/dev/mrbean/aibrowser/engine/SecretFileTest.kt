package dev.mrbean.aibrowser.engine

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

class SecretFileTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `write then read trims whitespace`() {
        val dataDir = tmp.newFolder("data")
        val secret = SecretFile(File(dataDir, "tunnel.token"))

        secret.write("  eyJhbGciOiJSUzI1NiIs... \n")

        assertEquals("eyJhbGciOiJSUzI1NiIs...", secret.readOrEmpty())
    }

    @Test
    fun `readOrEmpty is empty when the file is missing`() {
        val dataDir = tmp.newFolder("data")

        assertEquals("", SecretFile(File(dataDir, "mcp.host")).readOrEmpty())
    }

    @Test
    fun `clearing writes an empty file`() {
        val dataDir = tmp.newFolder("data")
        val secret = SecretFile(File(dataDir, "tunnel.token"))

        secret.write("some-token")
        secret.write("")

        assertEquals("", secret.readOrEmpty())
    }

    @Test
    fun `sets mode 600 when the filesystem supports posix permissions`() {
        val dataDir = tmp.newFolder("data")
        val file = File(dataDir, "tunnel.token")

        SecretFile(file).write("secret")

        val perms = runCatching { Files.getPosixFilePermissions(file.toPath()) }.getOrNull()
        if (perms != null) {
            assertEquals(
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                perms,
            )
        }
    }
}