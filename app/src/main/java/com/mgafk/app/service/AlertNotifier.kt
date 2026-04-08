package com.mgafk.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.mgafk.app.MgAfkApp
import com.mgafk.app.data.model.AlertConfig
import com.mgafk.app.data.model.AlertMode
import com.mgafk.app.data.model.AlertSection
import com.mgafk.app.data.model.PetSnapshot
import com.mgafk.app.data.model.ShopSnapshot
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.websocket.Constants

/**
 * Sends notifications or triggers loud alarms with a full-screen activity.
 */
class AlertNotifier(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)
    private var notificationId = 1000

    // Dedup tracking — cleared when the condition goes away
    private val firedShopKeys = mutableSetOf<String>()
    private val firedHungerPets = mutableSetOf<String>()
    private var firedWeather: String = ""

    // ── Public check methods ──

    fun checkPetHunger(pets: List<PetSnapshot>, alerts: AlertConfig) {
        val hungerAlert = alerts.items["hunger<5"] ?: return
        if (!hungerAlert.enabled) return

        val currentLowPets = mutableSetOf<String>()
        val items = mutableListOf<DisplayItem>()

        for (pet in pets) {
            val maxHunger = Constants.PET_HUNGER_COSTS[pet.species.lowercase()] ?: continue
            val percent = (pet.hunger.toFloat() / maxHunger) * 100
            if (percent < Constants.PET_HUNGER_THRESHOLD) {
                currentLowPets.add(pet.id)
                if (pet.id !in firedHungerPets) {
                    firedHungerPets.add(pet.id)
                    val petEntry = MgApi.findPet(pet.species.lowercase())
                    items.add(DisplayItem(
                        label = "${pet.name} (${pet.species}) — ${"%.1f".format(percent)}%",
                        spriteUrl = petEntry?.sprite,
                    ))
                }
            }
        }
        firedHungerPets.retainAll(currentLowPets)

        if (items.isNotEmpty()) {
            dispatchAlert("Pet Hunger", items, alerts.modeFor(AlertSection.PET))
        }
    }

    fun checkWeather(weather: String, previousWeather: String, alerts: AlertConfig) {
        if (weather == previousWeather || weather.isBlank()) return
        if (weather == firedWeather) return
        val key = "weather:$weather"
        val alert = alerts.items[key] ?: return
        if (!alert.enabled) return
        firedWeather = weather

        val weatherEntry = MgApi.weatherInfo(weather)
        dispatchAlert(
            title = "Weather Change",
            items = listOf(DisplayItem(label = weather, spriteUrl = weatherEntry?.sprite)),
            mode = alerts.modeFor(AlertSection.WEATHER),
        )
    }

    fun checkShopItems(shops: List<ShopSnapshot>, alerts: AlertConfig) {
        val currentKeys = mutableSetOf<String>()
        val items = mutableListOf<DisplayItem>()

        for (shop in shops) {
            for (itemName in shop.itemNames) {
                val key = "shop:${shop.type}:$itemName"
                currentKeys.add(key)
                if (key in firedShopKeys) continue
                val alert = alerts.items[key] ?: continue
                if (!alert.enabled) continue
                firedShopKeys.add(key)

                val entry = resolveShopEntry(shop.type, itemName)
                items.add(DisplayItem(
                    label = entry?.name ?: itemName,
                    spriteUrl = entry?.sprite,
                ))
            }
        }
        firedShopKeys.retainAll(currentKeys)

        if (items.isNotEmpty()) {
            dispatchAlert("Shop Alert", items, alerts.modeFor(AlertSection.SHOP))
        }
    }

    fun stopAlarm() {
        stopGlobalAlarm()
    }

    /** Fire a fake alert for testing. Bypasses all dedup logic. */
    fun testAlert(mode: AlertMode) {
        val plants = MgApi.getPlants().values.take(3)
        val items = if (plants.isNotEmpty()) {
            plants.map { DisplayItem(label = it.name, spriteUrl = it.sprite) }
        } else {
            listOf(
                DisplayItem(label = "Test Seed"),
                DisplayItem(label = "Test Tool"),
            )
        }
        dispatchAlert("Shop Alert (TEST)", items, mode)
    }

    // ── Dispatch ──

    private data class DisplayItem(val label: String, val spriteUrl: String? = null)

    // Alarm debounce: events arriving within 300ms are combined into one alarm.
    private val pendingAlarmItems = mutableListOf<DisplayItem>()
    private val handler = Handler(Looper.getMainLooper())
    private val flushAlarm = Runnable {
        if (pendingAlarmItems.isEmpty()) return@Runnable
        launchAlarm("Alert", pendingAlarmItems.toList())
        pendingAlarmItems.clear()
    }

    private fun dispatchAlert(title: String, items: List<DisplayItem>, mode: AlertMode) {
        when (mode) {
            AlertMode.NOTIFICATION -> sendGroupedNotification(title, items)
            AlertMode.ALARM -> {
                pendingAlarmItems.addAll(items)
                handler.removeCallbacks(flushAlarm)
                handler.postDelayed(flushAlarm, 300)
            }
        }
    }

    // ── Notification mode ──

    private fun sendGroupedNotification(title: String, items: List<DisplayItem>) {
        val body = if (items.size == 1) items.first().label
        else "${items.size} alerts"
        val id = notificationId++
        val spriteUrl = items.firstOrNull()?.spriteUrl

        // Load sprite in background, then post notification with it
        Thread {
            val bitmap = loadBitmap(spriteUrl)

            val style = NotificationCompat.InboxStyle()
                .setBigContentTitle(title)
            for (item in items) {
                style.addLine(item.label)
            }

            val builder = NotificationCompat.Builder(context, MgAfkApp.CHANNEL_ALERTS)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .setStyle(style)

            if (bitmap != null) {
                builder.setLargeIcon(bitmap)
            }

            manager.notify(id, builder.build())
        }.start()
    }

    private fun loadBitmap(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return try {
            java.net.URL(url).openStream().use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {
            null
        }
    }

    // ── Alarm mode ──

    private fun launchAlarm(title: String, items: List<DisplayItem>) {
        stopGlobalAlarm()

        // Play alarm sound
        playAlarmSound()
        vibrate()

        val names = items.map { it.label }.toTypedArray()
        val sprites = items.map { it.spriteUrl.orEmpty() }.toTypedArray()

        // Launch AlarmActivity directly (works both locked and unlocked)
        val activityIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmActivity.EXTRA_TITLE, title)
            putExtra(AlarmActivity.EXTRA_NAMES, names)
            putExtra(AlarmActivity.EXTRA_SPRITES, sprites)
        }
        context.startActivity(activityIntent)

        // Also post notification as fallback (if activity doesn't show, e.g. DND restrictions)
        val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmActivity.EXTRA_TITLE, title)
            putExtra(AlarmActivity.EXTRA_NAMES, names)
            putExtra(AlarmActivity.EXTRA_SPRITES, sprites)
        }
        val fullScreenPending = PendingIntent.getActivity(
            context, 1, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Stop intent for notification button
        val stopIntent = Intent(context, AlarmStopReceiver::class.java).apply {
            action = AlarmStopReceiver.ACTION_STOP_ALARM
        }
        val stopPending = PendingIntent.getBroadcast(
            context, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = items.joinToString(", ") { it.label }
        val notification = NotificationCompat.Builder(context, MgAfkApp.CHANNEL_ALARMS)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPending, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Alarm", stopPending)
            .setContentIntent(fullScreenPending)
            .setDeleteIntent(stopPending)
            .build()

        manager.notify(ALARM_NOTIFICATION_ID, notification)
    }

    // ── Helpers ──

    private fun resolveShopEntry(type: String, itemId: String): MgApi.GameEntry? {
        val map = when (type) {
            "seed" -> MgApi.getPlants()
            "tool" -> MgApi.getItems()
            "egg" -> MgApi.getEggs()
            "decor" -> MgApi.getDecors()
            else -> emptyMap()
        }
        return map[itemId]
    }

    private fun playAlarmSound() {
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, alarmUri)
                isLooping = true
                prepare()
                start()
            }
            synchronized(lock) {
                activePlayer = player
                activeContext = context
            }
        } catch (_: Exception) { }
    }

    private fun vibrate() {
        val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(VibratorManager::class.java)
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, 0),
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            }
        } catch (_: Exception) { }
    }

    companion object {
        private const val ALARM_NOTIFICATION_ID = 9999
        private val lock = Any()
        private var activePlayer: MediaPlayer? = null
        private var activeContext: Context? = null

        fun stopGlobalAlarm() {
            synchronized(lock) {
                activePlayer?.let {
                    try { if (it.isPlaying) it.stop(); it.release() } catch (_: Exception) { }
                }
                activePlayer = null

                activeContext?.let { ctx ->
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            ctx.getSystemService(VibratorManager::class.java)
                                ?.defaultVibrator?.cancel()
                        } else {
                            @Suppress("DEPRECATION")
                            (ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.cancel()
                        }
                    } catch (_: Exception) { }

                    ctx.getSystemService(NotificationManager::class.java)
                        ?.cancel(ALARM_NOTIFICATION_ID)
                }
                activeContext = null
            }
        }
    }
}
