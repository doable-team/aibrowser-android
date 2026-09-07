package dev.mrbean.aibrowser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtensionInstallerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var paths: Paths
    private lateinit var installer: ExtensionInstaller

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
        installer = ExtensionInstaller(paths)
    }

    @Test
    fun `a flat zip installs under a directory named after the picked file`() {
        val bytes = zip(
            "manifest.json" to """{"name":"My Ext","version":"1.0"}""".toByteArray(),
            "background.js" to "console.log('hi');".toByteArray(),
        )

        val installed = installer.install("My Ext v2.zip", ByteArrayInputStream(bytes))

        assertEquals("my-ext-v2", installed.directory)
        val manifest = File(File(paths.data, "extensions"), "my-ext-v2/manifest.json")
        assertTrue(manifest.exists())
        assertTrue(File(manifest.parentFile, "background.js").exists())
    }

    @Test
    fun `a zip wrapped in one top-level directory installs with the wrapper stripped`() {
        val bytes = zip(
            "wrap/manifest.json" to """{"name":"Wrapped","version":"2.0"}""".toByteArray(),
            "wrap/background.js" to "console.log('wrapped');".toByteArray(),
        )

        val installed = installer.install("wrapped.crx", ByteArrayInputStream(bytes))

        assertEquals("wrapped", installed.directory)
        val dir = File(File(paths.data, "extensions"), "wrapped")
        assertTrue(File(dir, "manifest.json").exists())
        assertFalse(File(dir, "wrap").exists())
    }

    @Test
    fun `metadata carries the manifest name and version and a MSG name falls back to the directory`() {
        val installed = installer.install(
            "myext.zip",
            ByteArrayInputStream(zip("manifest.json" to """{"name":"My Ext","version":"1.0"}""".toByteArray())),
        )
        assertEquals("My Ext", installed.name)
        assertEquals("1.0", installed.version)

        val localised = installer.install(
            "local.zip",
            ByteArrayInputStream(zip("manifest.json" to """{"name":"__MSG_appName__","version":"1.0"}""".toByteArray())),
        )
        assertEquals("local", localised.name)
    }

    @Test
    fun `a zip without a manifest throws and leaves no directory or staging behind`() {
        val bytes = zip("background.js" to "console.log('x');".toByteArray())

        try {
            installer.install("nomf.zip", ByteArrayInputStream(bytes))
            fail("expected an ExtensionException")
        } catch (e: ExtensionException) {
            assertTrue(e.message!!.contains("manifest.json"))
        }

        val extensions = File(paths.data, "extensions")
        assertTrue(extensions.listFiles()?.isEmpty() == true)
        assertFalse(File(extensions, ".staging").exists())
    }

    @Test
    fun `a zip-slip entry is rejected and writes nothing outside the extensions directory`() {
        val bytes = zip(
            "manifest.json" to """{"name":"evil","version":"1"}""".toByteArray(),
            "../escape.txt" to "pwned".toByteArray(),
        )

        try {
            installer.install("evil.zip", ByteArrayInputStream(bytes))
            fail("expected an ExtensionException")
        } catch (e: ExtensionException) {
            assertEquals("unsafe path in the archive: ../escape.txt", e.message)
        }

        assertFalse(File(tmp.root, "escape.txt").exists())
        assertFalse(File(paths.data, "escape.txt").exists())
        assertFalse(File(File(paths.data, "extensions"), ".staging").exists())
    }

    @Test
    fun `a CRX3 file installs like a plain zip`() {
        val zipBytes = zip("manifest.json" to """{"name":"Crx Ext","version":"3"}""".toByteArray())
        val crx = ByteArrayOutputStream().apply {
            write("Cr24".toByteArray(Charsets.US_ASCII))
            write(le32(3))
            write(le32(16))
            write(ByteArray(16))
            write(zipBytes)
        }.toByteArray()

        val installed = installer.install("crx.crx", ByteArrayInputStream(crx))

        assertEquals("crx", installed.directory)
        assertEquals("Crx Ext", installed.name)
        assertTrue(File(File(paths.data, "extensions"), "crx/manifest.json").exists())
    }

    @Test
    fun `installing the same file name twice replaces the contents`() {
        val first = zip(
            "manifest.json" to """{"name":"Replaced","version":"1"}""".toByteArray(),
            "old.js" to "console.log('old');".toByteArray(),
        )
        installer.install("same.zip", ByteArrayInputStream(first))
        val dir = File(File(paths.data, "extensions"), "same")
        assertTrue(File(dir, "old.js").exists())

        val second = zip(
            "manifest.json" to """{"name":"Replaced","version":"2"}""".toByteArray(),
            "new.js" to "console.log('new');".toByteArray(),
        )
        installer.install("same.zip", ByteArrayInputStream(second))

        assertFalse(File(dir, "old.js").exists())
        assertTrue(File(dir, "new.js").exists())
        assertEquals("2", installer.list().first { it.directory == "same" }.version)
    }

    @Test
    fun `list returns the installed extensions and remove deletes one and refuses bad names`() {
        installer.install("aaa.zip", ByteArrayInputStream(zip("manifest.json" to """{"name":"Aaa","version":"1"}""".toByteArray())))
        installer.install("bbb.zip", ByteArrayInputStream(zip("manifest.json" to """{"name":"Bbb","version":"2"}""".toByteArray())))

        val listed = installer.list()
        assertEquals(listOf("aaa", "bbb"), listed.map { it.directory })
        assertEquals("Aaa", listed[0].name)
        assertEquals("1", listed[0].version)

        assertTrue(installer.remove("aaa"))
        assertEquals(listOf("bbb"), installer.list().map { it.directory })

        assertFalse(installer.remove("../evil"))
        assertFalse(installer.remove("a/b"))
        assertTrue(File(File(paths.data, "extensions"), "bbb").exists())
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun le32(value: Int): ByteArray =
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        )
}