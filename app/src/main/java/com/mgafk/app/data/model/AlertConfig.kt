package com.mgafk.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AlertConfig(
    val items: Map<String, AlertItem> = emptyMap(),
    val sectionModes: Map<String, AlertMode> = emptyMap(),
    val collapsed: Map<String, Boolean> = emptyMap(),
    val petHungerThreshold: Int = 5,
    val feedingTroughThreshold: Int = 3,
) {
    fun modeFor(section: AlertSection): AlertMode =
        sectionModes[section.key] ?: AlertMode.NOTIFICATION

    /**
     * Resolves the effective alert mode for a single item.
     *
     * If the section mode is NOTIFICATION or ALARM, that's the answer for every item in the section.
     * If the section mode is CUSTOM, the per-item `mode` field decides.
     * Always returns NOTIFICATION or ALARM (never CUSTOM).
     */
    fun resolveMode(section: AlertSection, itemKey: String): AlertMode {
        val sectionMode = modeFor(section)
        if (sectionMode != AlertMode.CUSTOM) return sectionMode
        val itemMode = items[itemKey]?.mode ?: AlertMode.NOTIFICATION
        return if (itemMode == AlertMode.CUSTOM) AlertMode.NOTIFICATION else itemMode
    }
}

@Serializable
data class AlertItem(
    val enabled: Boolean = false,
    /**
     * Only consulted when the section's mode is CUSTOM.
     * Valid effective values: NOTIFICATION or ALARM. CUSTOM here is treated as NOTIFICATION fallback.
     */
    val mode: AlertMode = AlertMode.NOTIFICATION,
)

@Serializable
enum class AlertMode { NOTIFICATION, ALARM, CUSTOM }

enum class AlertSection(val key: String, val label: String) {
    SHOP("shop", "Shops"),
    WEATHER("weather", "Weather"),
    PET("pet", "Pets"),
    FEEDING_TROUGH("feeding_trough", "Feeding Trough"),
}
