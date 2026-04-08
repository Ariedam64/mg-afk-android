package com.mgafk.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager

class MgAfkApp : Application() {

    companion object {
        const val CHANNEL_SERVICE = "mgafk_service"
        const val CHANNEL_ALERTS = "mgafk_alerts"
        const val CHANNEL_ALARMS = "mgafk_alarms"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

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
