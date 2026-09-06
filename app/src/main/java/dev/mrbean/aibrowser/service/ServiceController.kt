package dev.mrbean.aibrowser.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Starts [AiBrowserService] with one of its action intents. Always uses
 * [ContextCompat.startForegroundService]; the service calls `startForeground`
 * in return, so every action is safe to fire from the UI or a notification.
 */
object ServiceController {

    const val ACTION_START_ALL = "dev.mrbean.aibrowser.action.START_ALL"
    const val ACTION_STOP_ALL = "dev.mrbean.aibrowser.action.STOP_ALL"
    const val ACTION_RESTART_ALL = "dev.mrbean.aibrowser.action.RESTART_ALL"
    const val ACTION_START = "dev.mrbean.aibrowser.action.START"
    const val ACTION_STOP = "dev.mrbean.aibrowser.action.STOP"
    const val ACTION_RESTART = "dev.mrbean.aibrowser.action.RESTART"
    const val EXTRA_NAME = "dev.mrbean.aibrowser.action.EXTRA_NAME"

    fun start(context: Context, action: String, name: String? = null) {
        val intent = Intent(context, AiBrowserService::class.java).setAction(action)
        if (name != null) intent.putExtra(EXTRA_NAME, name)
        ContextCompat.startForegroundService(context, intent)
    }
}