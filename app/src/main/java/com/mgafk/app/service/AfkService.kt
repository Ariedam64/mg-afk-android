package com.mgafk.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mgafk.app.MainActivity
import com.mgafk.app.MgAfkApp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Foreground service that keeps the app alive in background for AFK sessions.
 * Manages WifiLock and WakeLock based on user settings.
 *
 * Wake lock modes:
 * - OFF: never hold a CPU wake lock
 * - SMART: acquire after [EXTRA_WAKE_LOCK_DELAY_MIN] minutes of screen off, release on unlock.
 *   Uses AlarmManager.setExactAndAllowWhileIdle to guarantee firing even in Doze mode.
 * - ALWAYS: hold a CPU wake lock as long as the service runs
 */
class AfkService : Service() {

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val silentAudio = SilentAudioLoop()
    private var watchdogBound = false

    private var wakeLockMode = MODE_OFF
    private var wakeLockDelayMin = 15

    private val watchdogConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            watchdogBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            // Watchdog process died. Respawn it so the mutual death-watch keeps working.
            watchdogBound = false
            startAndBindWatchdog()
        }

        override fun onBindingDied(name: ComponentName?) {
            watchdogBound = false
            startAndBindWatchdog()
        }

        override fun onNullBinding(name: ComponentName?) {
            watchdogBound = true
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_USER_PRESENT -> onScreenUnlocked()
            }
        }
    }

    private val smartAlarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_SMART_WAKE_LOCK) {
                emitLog("smart alarm fired")
                acquireWakeLock()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Two "we got resurrected with nothing to do" paths:
        //   1. intent == null      → system restart after START_STICKY kill
        //   2. action == SELF_RESTART → our post-onTaskRemoved alarm fired
        // In both, the activity/ViewModel are gone so there's no RoomClient to
        // keep alive. Post a resume notification and stop. Tapping the notif
        // launches MainActivity, whose init auto-reconnects wantConnected sessions.
        if (intent == null || intent.action == ACTION_SELF_RESTART) {
            emitLog(
                "service resurrected",
                if (intent == null) "null intent (system restart)"
                else "self-restart alarm fired",
            )
            // We may have been launched via startForegroundService() (the
            // watchdog resurrection path) or auto-restarted by the system.
            // Either way Android requires a startForeground() call within the
            // start timeout, even though we are about to stop immediately.
            // Skipping it throws ForegroundServiceDidNotStartInTimeException,
            // and the mutual death-watch turns that single crash into a crash
            // loop ("MG AFK keeps stopping"). Promote first, then demote+stop.
            // If promotion is refused (e.g. Android 15 FGS time limit) we still
            // stop cleanly without crashing.
            tryStartForegroundAsMediaPlayback(NOTIFICATION_ID, buildNotification())
            postResumeNotification(this)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // Activity is starting us with real config — cancel any pending
        // self-restart alarm queued by a previous onTaskRemoved.
        cancelSelfRestart()

        if (!tryStartForegroundAsMediaPlayback(NOTIFICATION_ID, buildNotification())) {
            // System refused the foreground promotion (e.g. Android 15 6h FGS
            // time limit). Don't crash; tell the user they need to reopen.
            emitLog("service start refused", "foreground promotion rejected")
            postResumeNotification(this)
            stopSelf()
            return START_NOT_STICKY
        }
        // Foreground for a live session now: clear any stale "tap to resume".
        cancelResumeNotification(this)
        if (silentAudio.start()) emitLog("silent audio started")
        startAndBindWatchdog()

        val wantWifiLock = intent.getBooleanExtra(EXTRA_WIFI_LOCK, true)
        if (wantWifiLock) acquireWifiLock() else releaseWifiLock()

        wakeLockMode = intent.getIntExtra(EXTRA_WAKE_LOCK_MODE, MODE_OFF)
        wakeLockDelayMin = intent.getIntExtra(EXTRA_WAKE_LOCK_DELAY_MIN, 15)

        val modeName = when (wakeLockMode) { MODE_SMART -> "smart"; MODE_ALWAYS -> "always"; else -> "off" }
        emitLog("service started", "wifi=${if (wantWifiLock) "on" else "off"} cpu=$modeName delay=${wakeLockDelayMin}min")

        applyWakeLockMode()

        return START_STICKY
    }

    /**
     * Fired when the user swipes the app out of Recents. On stock Android the
     * service would survive, but several OEM skins (Samsung, Xiaomi, …) kill
     * the whole process. Schedule an inexact-but-while-idle alarm to restart
     * ourselves a few seconds later with [ACTION_SELF_RESTART], which our
     * onStartCommand turns into a resume notification.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        emitLog("task removed", "scheduling self-restart in ${TASK_REMOVED_RESTART_DELAY_MS / 1000}s")
        scheduleSelfRestart()
        super.onTaskRemoved(rootIntent)
    }

    private fun scheduleSelfRestart() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val triggerAt = SystemClock.elapsedRealtime() + TASK_REMOVED_RESTART_DELAY_MS
        // setAndAllowWhileIdle works in Doze and doesn't require USE_EXACT_ALARM.
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            selfRestartPendingIntent(),
        )
    }

    private fun cancelSelfRestart() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(selfRestartPendingIntent())
    }

    private fun selfRestartPendingIntent(): PendingIntent {
        val restartIntent = Intent(this, AfkService::class.java).setAction(ACTION_SELF_RESTART)
        return foregroundServicePendingIntent(RESTART_REQUEST_CODE, restartIntent)
    }

    override fun onCreate() {
        super.onCreate()
        // Screen on/off — system broadcasts, no export flag needed
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, screenFilter)

        // Smart alarm — app-internal broadcast
        val alarmFilter = IntentFilter(ACTION_SMART_WAKE_LOCK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smartAlarmReceiver, alarmFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(smartAlarmReceiver, alarmFilter)
        }
    }

    override fun onDestroy() {
        emitLog("service stopped")
        cancelSmartAlarm()
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(smartAlarmReceiver) } catch (_: Exception) {}
        releaseWakeLock()
        releaseWifiLock()
        silentAudio.stop()
        // onDestroy fires for legitimate stopService() calls — NOT for kills.
        // So this is the right place to bring the watchdog down with us:
        // a real shutdown should leave nothing running, while a kill leaves
        // the watchdog alive to resurrect us.
        unbindAndStopWatchdog()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Wake lock mode logic ──

    private fun applyWakeLockMode() {
        cancelSmartAlarm()
        when (wakeLockMode) {
            MODE_ALWAYS -> acquireWakeLock()
            MODE_SMART -> {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                if (!pm.isInteractive) {
                    scheduleSmartWakeLock()
                } else {
                    releaseWakeLock()
                }
            }
            else -> releaseWakeLock()
        }
    }

    private fun onScreenOff() {
        emitLog("screen off")
        if (wakeLockMode == MODE_SMART) {
            scheduleSmartWakeLock()
        }
    }

    private fun onScreenUnlocked() {
        emitLog("screen unlocked")
        if (wakeLockMode == MODE_SMART) {
            cancelSmartAlarm()
            releaseWakeLock()
        }
    }

    // ── Smart alarm (survives Doze) ──

    private fun scheduleSmartWakeLock() {
        cancelSmartAlarm()
        val delayMs = wakeLockDelayMin.toLong() * 60_000L
        val triggerAt = SystemClock.elapsedRealtime() + delayMs

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        // On Android 12+ check if exact alarms are allowed, fallback to inexact if not
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            emitLog("smart wake lock scheduled (inexact)", "exact alarms not permitted, using inexact. delay=${wakeLockDelayMin}min")
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                smartAlarmPendingIntent(),
            )
        } else {
            emitLog("smart wake lock scheduled", "will activate in ${wakeLockDelayMin}min")
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                smartAlarmPendingIntent(),
            )
        }
    }

    private fun cancelSmartAlarm() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(smartAlarmPendingIntent())
    }

    private fun smartAlarmPendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            SMART_ALARM_REQUEST_CODE,
            Intent(ACTION_SMART_WAKE_LOCK).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    // ── Watchdog (companion process for mutual resurrection) ──

    private fun startAndBindWatchdog() {
        WatchdogService.start(this)
        if (watchdogBound) return
        try {
            bindService(
                Intent(this, WatchdogService::class.java),
                watchdogConnection,
                BIND_AUTO_CREATE,
            )
        } catch (_: Exception) {}
    }

    private fun unbindAndStopWatchdog() {
        // Tell the watchdog this is a legitimate shutdown BEFORE unbinding —
        // otherwise its onServiceDisconnected callback would try to resurrect
        // us during the very shutdown we're performing. The flag is set in
        // the watchdog process via an intent action; a short grace window
        // there absorbs the IPC delay.
        try {
            startService(
                Intent(this, WatchdogService::class.java)
                    .setAction(WatchdogService.ACTION_SHUTDOWN),
            )
        } catch (_: Exception) {}
        if (watchdogBound) {
            try { unbindService(watchdogConnection) } catch (_: Exception) {}
            watchdogBound = false
        }
        WatchdogService.stop(this)
    }

    // ── Lock management ──

    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return
        emitLog("wifi lock acquired")
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "mgafk:wifi")
            .apply { acquire() }
    }

    private fun releaseWifiLock() {
        if (wifiLock?.isHeld == true) emitLog("wifi lock released")
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        emitLog("cpu wake lock acquired")
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mgafk:cpu")
            .apply { acquire() }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) emitLog("cpu wake lock released")
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, MgAfkApp.CHANNEL_SERVICE)
            .setContentTitle("MG AFK")
            .setContentText("Session active in background")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            // Android 12+: post the notification immediately rather than the
            // default 10s grace, so the system has stronger proof we're a
            // legitimate foreground service.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val SMART_ALARM_REQUEST_CODE = 42
        private const val RESTART_REQUEST_CODE = 43
        private const val ACTION_SMART_WAKE_LOCK = "com.mgafk.app.SMART_WAKE_LOCK"
        internal const val ACTION_SELF_RESTART = "com.mgafk.app.SELF_RESTART"
        private const val TASK_REMOVED_RESTART_DELAY_MS = 3_000L

        const val EXTRA_WIFI_LOCK = "extra_wifi_lock"
        const val EXTRA_WAKE_LOCK_MODE = "extra_wake_lock_mode"
        const val EXTRA_WAKE_LOCK_DELAY_MIN = "extra_wake_lock_delay_min"

        const val MODE_OFF = 0
        const val MODE_SMART = 1
        const val MODE_ALWAYS = 2

        private val _logs = MutableSharedFlow<ServiceLog>(extraBufferCapacity = 64)
        val logs: SharedFlow<ServiceLog> = _logs.asSharedFlow()

        private fun emitLog(event: String, detail: String = "") {
            _logs.tryEmit(ServiceLog(event = event, detail = detail))
        }
    }

    data class ServiceLog(
        val timestamp: Long = System.currentTimeMillis(),
        val event: String = "",
        val detail: String = "",
    )
}
