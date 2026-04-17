package com.mgafk.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

class MgAfkApp : Application(), ImageLoaderFactory {

    companion object {
        const val CHANNEL_SERVICE = "mgafk_service"
        const val CHANNEL_ALERTS = "mgafk_alerts"
        const val CHANNEL_ALARMS = "mgafk_alarms"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
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

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val alarmAudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val alarmsChannel = NotificationChannel(
            CHANNEL_ALARMS,
            "Game Alarms",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Loud alarm alerts that bypass silent mode"
            setSound(alarmSound, alarmAudioAttributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
        }

        manager.createNotificationChannel(serviceChannel)
        manager.createNotificationChannel(alertsChannel)
        manager.createNotificationChannel(alarmsChannel)
    }
}
