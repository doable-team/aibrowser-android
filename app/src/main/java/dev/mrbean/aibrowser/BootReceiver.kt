package dev.mrbean.aibrowser

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.mrbean.aibrowser.engine.AppConfig
import dev.mrbean.aibrowser.engine.ConfigStore
import dev.mrbean.aibrowser.engine.Paths
import dev.mrbean.aibrowser.service.ServiceController
import java.io.File

/**
 * Starts [dev.mrbean.aibrowser.service.AiBrowserService] after a reboot (and
 * after an in-place app update) when "Start on boot" is enabled and the rootfs
 * is installed. Kept thin: it reads the config, decides, and fires one intent.
 *
 * On Android 12+ a foreground service started from a BOOT_COMPLETED receiver is
 * exempt from the background-service restrictions, so the receiver's work stays
 * minimal and [ServiceController.startForegroundService] is safe here.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val paths = Paths.from(context)
        val config = ConfigStore(paths.data).load()
        val installed = File(paths.rootfs, "usr/bin/env").isFile &&
            config.rootfsVersion.isNotEmpty()
        if (decideBootStart(config, installed)) {
            ServiceController.start(context, ServiceController.ACTION_START_ALL)
        }
    }
}

/** Pure decision for [BootReceiver]; `true` only when boot start is on and the rootfs exists. */
fun decideBootStart(config: AppConfig, installed: Boolean): Boolean =
    config.startOnBoot && installed