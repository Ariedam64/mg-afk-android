package com.mgafk.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
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

    private var wakeLockMode = MODE_OFF
    private var wakeLockDelayMin = 15

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
        startForeground(NOTIFICATION_ID, buildNotification())

        val wantWifiLock = intent?.getBooleanExtra(EXTRA_WIFI_LOCK, true) ?: true
        if (wantWifiLock) acquireWifiLock() else releaseWifiLock()

        wakeLockMode = intent?.getIntExtra(EXTRA_WAKE_LOCK_MODE, MODE_OFF) ?: MODE_OFF
        wakeLockDelayMin = intent?.getIntExtra(EXTRA_WAKE_LOCK_DELAY_MIN, 15) ?: 15

        val modeName = when (wakeLockMode) { MODE_SMART -> "smart"; MODE_ALWAYS -> "always"; else -> "off" }
        emitLog("service started", "wifi=${if (wantWifiLock) "on" else "off"} cpu=$modeName delay=${wakeLockDelayMin}min")

        applyWakeLockMode()

        return START_STICKY
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
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, MgAfkApp.CHANNEL_SERVICE)
            .setContentTitle("MG AFK")
            .setContentText("Session active in background")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val SMART_ALARM_REQUEST_CODE = 42
        private const val ACTION_SMART_WAKE_LOCK = "com.mgafk.app.SMART_WAKE_LOCK"

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
