package dev.mrbean.aibrowser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NetGuardTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `writes the netguard script and service into an installed rootfs`() {
        val rootfs = tmp.newFolder("rootfs")
        val opt = File(rootfs, "opt/aibrowser")
        assertTrue(opt.mkdirs())

        NetGuard.ensure(rootfs)

        val js = File(opt, "netguard.js")
        assertTrue(js.isFile)
        assertTrue(js.readText().contains(NetGuard.MARKER))
        val sh = File(opt, "services/netguard.sh")
        assertTrue(sh.isFile)
        assertEquals(NetGuard.NETGUARD_SH, sh.readText())
    }

    @Test
    fun `a different marker line overwrites the old version`() {
        val rootfs = tmp.newFolder("rootfs")
        val opt = File(rootfs, "opt/aibrowser")
        assertTrue(opt.mkdirs())
        val js = File(opt, "netguard.js")
        js.writeText("// aibrowser-netguard v0\nconsole.log('old');\n")

        NetGuard.ensure(rootfs)

        assertEquals(NetGuard.NETGUARD_JS, js.readText())
    }

    @Test
    fun `an up-to-date netguard script is not rewritten`() {
        val rootfs = tmp.newFolder("rootfs")
        val opt = File(rootfs, "opt/aibrowser")
        assertTrue(opt.mkdirs())
        val js = File(opt, "netguard.js")
        js.writeText(NetGuard.NETGUARD_JS)
        val before = js.lastModified()

        NetGuard.ensure(rootfs)

        assertEquals(before, js.lastModified())
        assertEquals(NetGuard.NETGUARD_JS, js.readText())
    }

    @Test
    fun `a rootfs without opt aibrowser is left alone and does not throw`() {
        val rootfs = tmp.newFolder("rootfs")

        NetGuard.ensure(rootfs)

        assertFalse(File(rootfs, "opt").exists())
    }

    @Test
    fun `the embedded guard matches the copy shipped in the rootfs overlay`() {
        // The same script lives twice: as a constant here (written into an
        // installed rootfs) and in rootfs-overlay (baked into new images).
        // They must not drift; a fix applied to one only is a silent hole.
        val overlay = File("../rootfs-overlay/opt/aibrowser/netguard.js")
        if (!overlay.isFile) return
        assertEquals(overlay.readText(), NetGuard.NETGUARD_JS)
    }
}
