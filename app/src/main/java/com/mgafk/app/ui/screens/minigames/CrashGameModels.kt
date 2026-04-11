package com.mgafk.app.ui.screens.minigames

import androidx.compose.ui.graphics.Color

// ── Background themes per milestone ──
internal data class SpaceTheme(
    val topColor: Color,
    val bottomColor: Color,
    val starColor: Color,
    val label: String,
)

internal val THEME_LAUNCH = SpaceTheme(
    topColor = Color(0xFF0A1628),
    bottomColor = Color(0xFF1A2940),
    starColor = Color.White.copy(alpha = 0.4f),
    label = "LAUNCH",
)
internal val THEME_SKY = SpaceTheme(
    topColor = Color(0xFF0D1B2A),
    bottomColor = Color(0xFF1B3A5C),
    starColor = Color.White.copy(alpha = 0.5f),
    label = "ATMOSPHERE",
)
internal val THEME_SPACE = SpaceTheme(
    topColor = Color(0xFF050D1A),
    bottomColor = Color(0xFF0A1628),
    starColor = Color.White.copy(alpha = 0.7f),
    label = "SPACE",
)
internal val THEME_DEEP = SpaceTheme(
    topColor = Color(0xFF1A0A0A),
    bottomColor = Color(0xFF2A0F0F),
    starColor = Color(0xFFFF6B6B).copy(alpha = 0.5f),
    label = "DANGER ZONE",
)
internal val THEME_EXTREME = SpaceTheme(
    topColor = Color(0xFF2A0000),
    bottomColor = Color(0xFF400A0A),
    starColor = Color(0xFFFF4444).copy(alpha = 0.6f),
    label = "CRITICAL",
)

internal fun themeForMultiplier(mult: Double): SpaceTheme = when {
    mult >= 10.0 -> THEME_EXTREME
    mult >= 5.0 -> THEME_DEEP
    mult >= 2.0 -> THEME_SPACE
    mult >= 1.3 -> THEME_SKY
    else -> THEME_LAUNCH
}

// Particle for trail/explosion
internal data class CrashParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    var decay: Float,
    var size: Float,
    var color: Color,
)

// Star in the background
internal data class Star(
    val xFrac: Float, // 0..1
    val yFrac: Float, // 0..1
    val size: Float,
    val twinkleSpeed: Float,
    val twinkleOffset: Float,
)

internal fun ClosedFloatingPointRange<Float>.random(): Float {
    return start + (Math.random() * (endInclusive - start)).toFloat()
}
