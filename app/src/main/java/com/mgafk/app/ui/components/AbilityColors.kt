package com.mgafk.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.mgafk.app.data.repository.MgApi
import kotlin.math.cos
import kotlin.math.sin

/** What an ability with no colour of its own is painted with. */
private val AbilityFallback = Color(0xFF646464)

/**
 * How an ability is painted: its own colour, or the gradient the game draws a couple of them
 * with (Rainbow Granter, Gold Granter).
 *
 * Both come from the API, keyed by ability id, so a colour the game changes follows on its own
 * and a new ability arrives painted rather than grey.
 */
internal fun abilityBrush(abilityId: String?): Brush {
    val entry = abilityId?.let { MgApi.getAbilities()[it] } ?: return SolidColor(AbilityFallback)
    val gradient = entry.abilityGradient
    if (gradient != null) {
        val stops = gradient.stops.map { it.offset.toFloat() to parseColor(it.color) }
        return Brush.linearGradient(
            colorStops = stops.toTypedArray(),
            start = Offset.Zero,
            end = gradientEnd(gradient.angleDegrees),
        )
    }
    return SolidColor(entry.color?.let(::parseColor) ?: AbilityFallback)
}

/** The flat colour for the places that paint a plain square rather than a brush. */
internal fun abilityColor(abilityId: String?): Color {
    val entry = abilityId?.let { MgApi.getAbilities()[it] } ?: return AbilityFallback
    // A gradient's first stop stands in for it, which is what the game's own small chips show.
    entry.abilityGradient?.stops?.firstOrNull()?.let { return parseColor(it.color) }
    return entry.color?.let(::parseColor) ?: AbilityFallback
}

/**
 * Where a linear gradient of [angleDegrees] ends, measured from the origin.
 *
 * Compose wants two points rather than an angle. The span is deliberately large so the ramp
 * covers the whole chip whatever its size; anything past the end is clamped to the last stop.
 */
private fun gradientEnd(angleDegrees: Double): Offset {
    val radians = Math.toRadians(angleDegrees)
    return Offset(
        x = (cos(radians) * GRADIENT_SPAN).toFloat(),
        y = (sin(radians) * GRADIENT_SPAN).toFloat(),
    )
}

/** Comfortably past the widest ability chip, in pixels. */
private const val GRADIENT_SPAN = 400.0

private fun parseColor(raw: String): Color = try {
    Color(android.graphics.Color.parseColor(raw))
} catch (e: IllegalArgumentException) {
    AbilityFallback
}
