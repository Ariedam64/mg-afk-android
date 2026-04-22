package com.mgafk.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
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
        private const val LEGACY_CHANNEL_ALARMS = "mgafk_alarms"
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        // Warm the Gemini userscript cache so the in-app Play WebView has it
        // ready as soon as the user taps Play.
        appScope.launch { GeminiFetcher.fetchLatest(this@MgAfkApp) }
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

        manager.createNotificationChannel(serviceChannel)
        manager.createNotificationChannel(alertsChannel)
        manager.createNotificationChannel(alarmsChannel)

        // Clean up the v1 alarm channel from older installs.
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ALARMS)
    }
}
