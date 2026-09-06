package dev.mrbean.aibrowser

import dev.mrbean.aibrowser.engine.AppConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BootReceiver] itself only reads the config and fires one intent; the whole
 * decision lives in [decideBootStart], which is covered here.
 */
class BootReceiverTest {

    @Test
    fun `decideBootStart requires both startOnBoot and an installed rootfs`() {
        assertTrue(decideBootStart(AppConfig(startOnBoot = true), installed = true))
        assertFalse(decideBootStart(AppConfig(startOnBoot = false), installed = true))
        assertFalse(decideBootStart(AppConfig(startOnBoot = true), installed = false))
        assertFalse(decideBootStart(AppConfig(), installed = false))
    }
}