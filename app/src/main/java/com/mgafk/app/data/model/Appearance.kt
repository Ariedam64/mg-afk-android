package com.mgafk.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** How rounded cards, buttons and chips are. */
@Serializable
enum class CornerStyle { SHARP, SOFT, ROUND }

/** Padding and gaps between elements. */
@Serializable
enum class LayoutDensity { COMPACT, COMFORTABLE }

/** Multiplier applied on top of the system font scale. */
@Serializable
enum class TextSize(val scale: Float) { SMALL(0.92f), NORMAL(1f), LARGE(1.12f) }

/** Cards the user can hide from the Home screen. */
@Serializable
enum class DashboardCard(val label: String) {
    WEATHER("Weather"),
    PETS("Pet hunger"),
    SHOPS("Shop restock"),
    STATUS("Live status"),
}

/**
 * Everything the user can change about how the app looks. Every surface color is
 * derived from [accentArgb], so changing it retints the whole app, not just buttons.
 */
@Serializable
data class Appearance(
    val accentArgb: Int = DEFAULT_ACCENT,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val corners: CornerStyle = CornerStyle.ROUND,
    val density: LayoutDensity = LayoutDensity.COMFORTABLE,
    val textSize: TextSize = TextSize.NORMAL,
    val hiddenDashboardCards: Set<DashboardCard> = emptySet(),
) {
    companion object {
        const val DEFAULT_ACCENT: Int = 0xFF8B6CFF.toInt()

        /** Swatches offered on the Customize screen. */
        val ACCENT_PRESETS: List<Int> = listOf(
            0xFF8B6CFF, // violet
            0xFF6C8CFF, // blue
            0xFF2FA4A0, // teal
            0xFF4CAF6A, // leaf
            0xFFC6E43A, // lime
            0xFFF2B33D, // amber
            0xFFE4572E, // vermilion
            0xFFE86AA6, // pink
        ).map { it.toInt() }
    }
}
