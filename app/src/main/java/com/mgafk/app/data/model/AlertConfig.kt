package com.mgafk.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AlertConfig(
    val items: Map<String, AlertItem> = emptyMap(),
    val globalMode: AlertMode = AlertMode.NOTIFICATION, // kept for migration compat
    val sectionModes: Map<String, AlertMode> = emptyMap(),
    val collapsed: Map<String, Boolean> = emptyMap(),
) {
    fun modeFor(section: AlertSection): AlertMode =
        sectionModes[section.key] ?: AlertMode.NOTIFICATION
}

@Serializable
data class AlertItem(
    val enabled: Boolean = false,
    val mode: AlertMode = AlertMode.NOTIFICATION,
)

@Serializable
enum class AlertMode { NOTIFICATION, ALARM }

enum class AlertSection(val key: String, val label: String) {
    SHOP("shop", "Shops"),
    WEATHER("weather", "Weather"),
    PET("pet", "Pets"),
}
