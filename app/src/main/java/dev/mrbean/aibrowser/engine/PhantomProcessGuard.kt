package dev.mrbean.aibrowser.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Android's phantom-process monitor kills an app's child processes beyond a
 * global limit (32 by default). The rootfs services, proot and Chromium's
 * renderers all count. The developer option "Disable child process
 * restrictions" writes [SETTING] = "false", but it resets on reboot on many
 * devices, so the app writes it itself whenever it holds
 * WRITE_SECURE_SETTINGS (granted once over adb).
 */
object PhantomProcessGuard {

    const val SETTING = "settings_enable_monitor_phantom_procs"
    const val GRANT_COMMAND = "adb shell pm grant dev.mrbean.aibrowser android.permission.WRITE_SECURE_SETTINGS"

    enum class State { DISABLED, RESTRICTED }

    /** DISABLED when the monitor is off (the setting reads "false"), else RESTRICTED. */
    fun state(context: Context): State {
        val value = runCatching { Settings.Global.getString(context.contentResolver, SETTING) }.getOrNull()
        return if (value == "false") State.DISABLED else State.RESTRICTED
    }

    fun canWrite(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Turns the monitor off when the permission allows it; returns the resulting state. */
    fun ensureDisabled(context: Context): State {
        if (state(context) == State.DISABLED) return State.DISABLED
        if (canWrite(context)) {
            runCatching { Settings.Global.putString(context.contentResolver, SETTING, "false") }
        }
        return state(context)
    }
}
