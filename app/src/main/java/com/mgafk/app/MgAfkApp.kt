package com.mgafk.app

import android.app.ActivityManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.mgafk.app.data.CrashLog
import com.mgafk.app.data.repository.GeminiFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MgAfkApp : Application(), ImageLoaderFactory {

    companion object {
        const val CHANNEL_SERVICE = "mgafk_service"
        const val CHANNEL_ALERTS = "mgafk_alerts"
        // Bumped from "mgafk_alarms" to "mgafk_alarms_v2" because the channel now
        // has no sound/vibration (handled by AlertNotifier so the user-chosen
        // sound is the only one that plays). Notification channels are immutable
        // after creation, so a new id is required to change those attributes.
        const val CHANNEL_ALARMS = "mgafk_alarms_v2"
        // Dedicated channel for the companion watchdog FGS notification — kept
        // at IMPORTANCE_MIN so it stays collapsed and silent in the shade.
        const val CHANNEL_WATCHDOG = "mgafk_watchdog"
        // "Tap to resume" notifications. Own channel (separate from the loud
        // Game Alerts) at IMPORTANCE_DEFAULT: a normal sound, no intrusive
        // heads-up, and the user can mute it independently.
        const val CHANNEL_RESUME = "mgafk_resume"
        private const val LEGACY_CHANNEL_ALARMS = "mgafk_alarms"
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val isMain = isMainProcess()
        // Install the crash recorder in EVERY process (main + :watchdog) first,
        // so a crash during the rest of bootstrapping is captured too. This is
        // how we get the real stack trace for intermittent background crashes
        // instead of guessing.
        CrashLog.install(this, if (isMain) "main" else "watchdog")
        CrashLog.trimIfLarge(this)
        createNotificationChannels()
        // The :watchdog process also instantiates Application; skip any
        // non-trivial bootstrapping there since it only needs the channels
        // (already created above) to post its own foreground notification.
        if (!isMain) return
        // Warm the Gemini userscript cache so the in-app Play WebView has it
        // ready as soon as the user taps Play.
        appScope.launch { GeminiFetcher.fetchLatest(this@MgAfkApp) }
    }

    private fun isMainProcess(): Boolean {
        val pid = android.os.Process.myPid()
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return true
        val info = try {
            am.runningAppProcesses
        } catch (_: Exception) {
            null
        }
        val processName = info?.firstOrNull { it.pid == pid }?.processName
        return processName == null || processName == packageName
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                // ~25% of app memory budget, holds decoded bitmaps for hot sprites.
                MemoryCache.Builder(this).maxSizePercent(0.25).build()
            }
            .diskCache {
                // 256 MB of persistent disk cache for sprite PNGs (base + composed).
                // Stored in the app's internal cache dir — cleared with app data.
                DiskCache.Builder()
                    .directory(cacheDir.resolve("sprite_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            // Ignore server Cache-Control; the MG API sends 24h max-age but we want
            // sprites to stay cached until explicitly evicted (they're versioned by URL
            // query string, so a bundle update produces a new URL and new cache entry).
            .respectCacheHeaders(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "AFK Connection",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps the WebSocket connection alive in background"
        }

        val alertsChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "Game Alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Shop items, pet hunger, weather alerts"
        }

        // No sound or vibration on the channel itself — AlertNotifier owns the
        // sound (MediaPlayer with the user-chosen URI) and the vibration so
        // there's a single source of truth and no overlap with the channel.
        val alarmsChannel = NotificationChannel(
            CHANNEL_ALARMS,
            "Game Alarms",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Loud alarm alerts that bypass silent mode"
            setSound(null, null)
            enableVibration(false)
        }

        val watchdogChannel = NotificationChannel(
            CHANNEL_WATCHDOG,
            "Background Watchdog",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "Companion process that resurrects the main service if killed"
            setShowBadge(false)
        }

        val resumeChannel = NotificationChannel(
            CHANNEL_RESUME,
            "Session Resume",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Lets you know a background session stopped and needs a tap to resume"
        }

        manager.createNotificationChannel(serviceChannel)
        manager.createNotificationChannel(alertsChannel)
        manager.createNotificationChannel(alarmsChannel)
        manager.createNotificationChannel(watchdogChannel)
        manager.createNotificationChannel(resumeChannel)

        // Clean up the v1 alarm channel from older installs.
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ALARMS)
    }
}
