package com.mgafk.app.service

import android.app.AlarmManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.mgafk.app.MgAfkApp

/**
 * Companion foreground service that lives in the `:watchdog` process. Its
 * sole job is to resurrect [AfkService] whenever the main process dies. The
 * trick: Android can kill our two processes independently, so as long as at
 * least one survives, the surviving one rebinds and respawns the other.
 *
 * Both services use foregroundServiceType="mediaPlayback" and play a silent
 * audio loop, so the OS / OEM task killers treat them as honest media services
 * and leave them alone. (mediaPlayback is also not subject to the Android 15
 * 6-hour dataSync time limit.)
 */
class WatchdogService : Service() {

    private val binder = Binder()
    private val silentAudio = SilentAudioLoop()
    private var afkBound = false
    private var shuttingDown = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val afkConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            afkBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            afkBound = false
            scheduleAfkResurrection()
        }

        override fun onBindingDied(name: ComponentName?) {
            afkBound = false
            scheduleAfkResurrection()
        }

        override fun onNullBinding(name: ComponentName?) {
            // AfkService.onBind returns null (it has no real IBinder API). The
            // binding is purely a death-watch, so null binding just means alive.
            afkBound = true
        }
    }

    /**
     * Brief grace window before resurrecting. If the disconnect was caused by
     * a legitimate shutdown, [ACTION_SHUTDOWN] arrives during this window and
     * flips [shuttingDown] — we skip the restart and just stop.
     */
    private fun scheduleAfkResurrection() {
        mainHandler.postDelayed({
            if (!shuttingDown) restartAfkService()
        }, RESURRECTION_GRACE_MS)
    }

    override fun onCreate() {
        super.onCreate()
        bindToAfk()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHUTDOWN) {
            // AfkService is going down legitimately and told us not to resurrect it.
            shuttingDown = true
            mainHandler.removeCallbacksAndMessages(null)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!tryStartForegroundAsMediaPlayback(NOTIFICATION_ID, buildNotification())) {
            // System refused the foreground promotion (e.g. Android 15 6h FGS
            // time limit). Stop instead of crash-looping on startForeground;
            // AfkService and the periodic worker handle recovery.
            stopSelf()
            return START_NOT_STICKY
        }
        silentAudio.start()
        if (!afkBound) bindToAfk()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleSelfRestart()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        try { unbindService(afkConnection) } catch (_: Exception) {}
        silentAudio.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun bindToAfk() {
        try {
            bindService(
                Intent(this, AfkService::class.java),
                afkConnection,
                BIND_AUTO_CREATE,
            )
        } catch (_: Exception) {
            // If bind fails (rare), schedule a retry through self-restart.
            scheduleSelfRestart()
        }
    }

    private fun restartAfkService() {
        // Use ACTION_SELF_RESTART so AfkService's onStartCommand posts the
        // "Tap to resume" notification and shuts down cleanly — we don't try
        // to keep both processes running indefinitely with no UI to drive a
        // WebSocket, we just make sure the user gets a clear signal.
        val intent = Intent(this, AfkService::class.java).setAction(AfkService.ACTION_SELF_RESTART)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (_: Exception) {
            // Background-start may be blocked on Android 12+. Fall back to an
            // alarm-driven self-restart, which has its own exemption path.
            scheduleAfkSelfRestartAlarm()
        }
    }

    private fun scheduleSelfRestart() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val triggerAt = SystemClock.elapsedRealtime() + WATCHDOG_RESTART_DELAY_MS
        val intent = Intent(this, WatchdogService::class.java)
        val pendingIntent = foregroundServicePendingIntent(WATCHDOG_RESTART_REQUEST_CODE, intent)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            pendingIntent,
        )
    }

    /**
     * Last-resort fallback when restartAfkService gets blocked by Android 12+
     * background-start rules. Reuses AfkService's own [AfkService.ACTION_SELF_RESTART]
     * action so the resume notification path fires.
     */
    private fun scheduleAfkSelfRestartAlarm() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val triggerAt = SystemClock.elapsedRealtime() + WATCHDOG_RESTART_DELAY_MS
        val intent = Intent(this, AfkService::class.java).setAction(AfkService.ACTION_SELF_RESTART)
        val pendingIntent = foregroundServicePendingIntent(AFK_FALLBACK_REQUEST_CODE, intent)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            pendingIntent,
        )
    }

    private fun buildNotification() = NotificationCompat.Builder(this, MgAfkApp.CHANNEL_WATCHDOG)
        .setContentTitle("MG AFK")
        .setContentText("Background watchdog active")
        .setSmallIcon(android.R.drawable.ic_menu_rotate)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()

    companion object {
        private const val NOTIFICATION_ID = 5
        private const val WATCHDOG_RESTART_REQUEST_CODE = 44
        private const val AFK_FALLBACK_REQUEST_CODE = 45
        private const val WATCHDOG_RESTART_DELAY_MS = 3_000L
        // Generous enough for IPC delivery of ACTION_SHUTDOWN across processes
        // under load. Resurrection is meant to be reactive, not instant; users
        // wouldn't notice another second of delay.
        private const val RESURRECTION_GRACE_MS = 1500L
        internal const val ACTION_SHUTDOWN = "com.mgafk.app.WATCHDOG_SHUTDOWN"

        fun start(context: Context) {
            val intent = Intent(context, WatchdogService::class.java)
            // May be called from a background context (e.g. AfkService's
            // onServiceDisconnected). Android 12+ can reject a background
            // foreground-service start with ForegroundServiceStartNotAllowed
            // Exception. Swallow it instead of crashing: the periodic
            // AfkWatchdogWorker and the next foreground start recover the
            // watchdog without taking the whole app down.
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchdogService::class.java))
        }
    }
}
