package com.mgafk.app.data.model

import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
enum class WakeLockMode {
    /** Never acquire a CPU wake lock. */
    OFF,
    /** Acquire automatically after the screen has been off for a while, release on unlock. */
    SMART,
    /** Always keep the CPU wake lock while a session is running. */
    ALWAYS,
}

@Serializable
enum class PurchaseMode {
    /** Tap buys x1, long-press buys all remaining stock. */
    HYBRID,
    /** Tap always buys x1. No bulk purchase. */
    SINGLE,
    /** Tap immediately buys all remaining stock. */
    BULK,
}

/**
 * Time window during which alarms are downgraded to silent notifications.
 *
 * `startMinute` and `endMinute` are minutes since midnight (0..1439).
 * If `startMinute > endMinute`, the window wraps midnight; the start day
 * (per [activeDays]) is the day used to evaluate membership.
 *
 * `activeDays` uses ISO day-of-week values: 1=Monday..7=Sunday.
 */
@Serializable
data class AlarmSchedule(
    val enabled: Boolean = false,
    val startMinute: Int = 22 * 60, // 22:00
    val endMinute: Int = 7 * 60,    // 07:00
    val activeDays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
)

/** True iff alarms should be silenced right now. */
fun AlarmSchedule.isSilentAt(now: LocalDateTime): Boolean {
    if (!enabled) return false
    if (activeDays.isEmpty()) return false
    val minutesNow = now.hour * 60 + now.minute
    val today = now.dayOfWeek.value          // 1..7 ISO
    val yesterday = ((today - 2 + 7) % 7) + 1
    return if (startMinute <= endMinute) {
        today in activeDays && minutesNow in startMinute until endMinute
    } else {
        // Window wraps midnight
        (today in activeDays && minutesNow >= startMinute) ||
            (yesterday in activeDays && minutesNow < endMinute)
    }
}

@Serializable
data class AppSettings(
    // Background & Battery
    val wifiLockEnabled: Boolean = true,
    val wakeLockMode: WakeLockMode = WakeLockMode.ALWAYS,
    val wakeLockAutoDelayMin: Int = 5,

    // Reconnection
    val retryDelayMs: Long = 1500,
    val retryMaxDelayMs: Long = 60000,
    val retrySupersededDelayMs: Long = 30000,
    val notifyOnDisconnect: Boolean = false,

    // Shops
    val purchaseMode: PurchaseMode = PurchaseMode.HYBRID,

    // Storages — auto-consolidate inventory stacks into matching storage slots
    val autoStockSeedSilo: Boolean = false,
    val autoStockDecorShed: Boolean = false,

    // Play in game — inject the Gemini userscript into the WebView
    val injectGeminiMod: Boolean = true,

    // Alarm sound URI (RingtoneManager). Empty = system default alarm.
    val alarmSoundUri: String = "",

    // Alarm silence schedule (alarms downgraded to silent notifications during the window)
    val alarmSchedule: AlarmSchedule = AlarmSchedule(),

    // Developer
    val showDebugMenu: Boolean = false,
)
