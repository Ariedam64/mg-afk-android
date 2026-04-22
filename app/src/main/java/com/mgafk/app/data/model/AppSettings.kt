package com.mgafk.app.data.model

import kotlinx.serialization.Serializable

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

    // Developer
    val showDebugMenu: Boolean = false,
)
