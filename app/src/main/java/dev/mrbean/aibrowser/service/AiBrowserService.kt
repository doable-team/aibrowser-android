package dev.mrbean.aibrowser.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.mrbean.aibrowser.AiBrowserApp
import dev.mrbean.aibrowser.MainActivity
import dev.mrbean.aibrowser.R
import dev.mrbean.aibrowser.engine.Services
import dev.mrbean.aibrowser.engine.ServiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that runs the service supervisor. A persistent
 * notification ("<running> of <total> services running") with a "Stop all"
 * action; a PARTIAL_WAKE_LOCK is held while at least one service is running.
 */
class AiBrowserService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val graph get() = (application as AiBrowserApp).graph

    private val wakeLock by lazy {
        (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AiBrowser:services")
            .apply { setReferenceCounted(false) }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        serviceScope.launch {
            graph.supervisor.statuses.collect { statuses ->
                val running = statuses.values.count { it.state is ServiceState.Running }
                updateWakeLock(running > 0)
                updateNotification(running)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ServiceController.ACTION_START_ALL
        startForeground(buildNotification(runningCount()))
        when (action) {
            ServiceController.ACTION_START_ALL -> graph.supervisor.startAll()
            ServiceController.ACTION_STOP_ALL -> {
                graph.supervisor.stopAll()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ServiceController.ACTION_RESTART_ALL -> graph.supervisor.restartAll()
            ServiceController.ACTION_START ->
                intent?.getStringExtra(ServiceController.EXTRA_NAME)?.let { name ->
                    serviceScope.launch { graph.supervisor.start(name) }
                }
            ServiceController.ACTION_STOP ->
                intent?.getStringExtra(ServiceController.EXTRA_NAME)?.let { name ->
                    serviceScope.launch { graph.supervisor.stop(name) }
                }
            ServiceController.ACTION_RESTART ->
                intent?.getStringExtra(ServiceController.EXTRA_NAME)?.let { name ->
                    serviceScope.launch { graph.supervisor.restart(name) }
                }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (wakeLock.isHeld) wakeLock.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun runningCount(): Int =
        graph.supervisor.statuses.value.values.count { it.state is ServiceState.Running }

    private fun updateNotification(running: Int) {
        ContextCompat.getSystemService(this, NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(running))
    }

    private fun buildNotification(running: Int): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopAllIntent = PendingIntent.getForegroundService(
            this, 1,
            Intent(this, AiBrowserService::class.java).setAction(ServiceController.ACTION_STOP_ALL),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AiBrowser")
            .setContentText("$running of ${Services.all.size} services running")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .addAction(0, "Stop all", stopAllIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun startForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Services", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun updateWakeLock(needed: Boolean) {
        if (needed) {
            if (!wakeLock.isHeld) wakeLock.acquire()
        } else if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    private companion object {
        const val CHANNEL_ID = "services"
        const val NOTIFICATION_ID = 1
    }
}