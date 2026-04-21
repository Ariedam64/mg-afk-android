package com.mgafk.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Shared rarity palette — game-authentic colors (mirrors Gemini's --rarity-*).
val RarityCommonColor = Color(0xFFE7E7E7)
val RarityUncommonColor = Color(0xFF67BD4D)
val RarityRareColor = Color(0xFF0071C6)
val RarityLegendaryColor = Color(0xFFFFC734)
val RarityMythicalColor = Color(0xFF9944A7)
val RarityDivineColor = Color(0xFFFF7835)
// Fallback solid color for Celestial (used where a Brush can't be animated,
// e.g. plain text). Prefer [rememberRarityBrush] when painting a background
// or border so the animated gradient renders instead.
val RarityCelestialColor = Color(0xFFFF00FF)

// Animated Celestial gradient — same hues as Gemini's badge, but the stops
// are re-weighted to favour blue + violet (which occupy ~85% of the band)
// with only a thin gold highlight near the end.
private val CelestialC0 = Color(0xFF00B4D8) // cyan-blue
private val CelestialC1 = Color(0xFF7C2AE8) // violet
private val CelestialC2 = Color(0xFFA0007E) // magenta
private val CelestialC3 = Color(0xFFFFD700) // gold

// Minimum alpha for the animated Celestial border so it stays vivid even when
// callers ask for a softer border (the rarest tier should always pop).
private const val CELESTIAL_MIN_ALPHA = 0.95f

// 130° CSS direction → screen-space unit vector (sin, cos of 130° clockwise from up).
private const val CELESTIAL_DIR_X = 0.766f
private const val CELESTIAL_DIR_Y = 0.643f

// Full gradient length (px). Much wider than a tile so the visible window always
// sits inside the gradient (no clamp edges), and `shift` can slide that window.
private const val CELESTIAL_SPAN = 400f

// How far the window actually travels each half-cycle. Stopping well before
// CELESTIAL_SPAN keeps the tile from ever being fully inside the gold section,
// so we only ever see a sliver of yellow at peak shift.
private const val CELESTIAL_MAX_SHIFT = 240f

fun rarityColor(rarity: String?): Color = when (rarity?.lowercase()) {
    "common" -> RarityCommonColor
    "uncommon" -> RarityUncommonColor
    "rare" -> RarityRareColor
    "legendary" -> RarityLegendaryColor
    "mythical", "mythic" -> RarityMythicalColor
    "divine" -> RarityDivineColor
    "celestial" -> RarityCelestialColor
    else -> TextMuted
}

/**
 * Brush that mirrors Gemini's rarity badge background:
 * - Celestial → animated diagonal gradient (4s cycle)
 * - All others → solid rarity color (optionally with alpha)
 */
@Composable
fun rememberRarityBrush(rarity: String?, alpha: Float = 1f): Brush {
    val isCelestial = rarity?.equals("celestial", ignoreCase = true) == true
    if (!isCelestial) {
        val base = rarityColor(rarity)
        return SolidColor(if (alpha < 1f) base.copy(alpha = alpha) else base)
    }

    // Mirrors CSS `background-position: 0% 50% → 100% 50% → 0% 50%` over 4s:
    // two-second tween going 0 → span, then Reverse brings it back = 4s cycle.
    val transition = rememberInfiniteTransition(label = "rarity-celestial")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = CELESTIAL_MAX_SHIFT,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "celestial-shift",
    )

    // Clamp alpha up: even when the caller dims other rarities (e.g. 0.5f borders),
    // Celestial keeps a near-opaque look so it's visibly the rarest tier.
    val effectiveAlpha = alpha.coerceAtLeast(CELESTIAL_MIN_ALPHA)
    fun tint(c: Color): Color = if (effectiveAlpha < 1f) c.copy(alpha = effectiveAlpha) else c

    // Re-weighted stops: blue + violet dominate (0% → 85%), magenta is a thin
    // bridge, gold is just a sliver right at the end. Combined with the limited
    // shift range above, this keeps the border visibly blue/violet at all times
    // and only flashes a hint of gold at peak shift.
    val stops: Array<Pair<Float, Color>> = arrayOf(
        0.00f to tint(CelestialC0),
        0.50f to tint(CelestialC1),
        0.85f to tint(CelestialC2),
        1.00f to tint(CelestialC3),
    )

    // Slide the gradient along the 130° axis: start = -shift · dir, so the tile
    // (at origin) sees the gradient's midsection move across it each cycle.
    val startX = -shift * CELESTIAL_DIR_X
    val startY = -shift * CELESTIAL_DIR_Y
    val endX = startX + CELESTIAL_SPAN * CELESTIAL_DIR_X
    val endY = startY + CELESTIAL_SPAN * CELESTIAL_DIR_Y
    return Brush.linearGradient(
        *stops,
        start = Offset(startX, startY),
        end = Offset(endX, endY),
        tileMode = TileMode.Clamp,
    )
}

/**
 * Rounded border styled by rarity. Celestial animates as a shifting gradient,
 * other rarities render as a solid color (with optional alpha).
 */
@Composable
fun Modifier.rarityBorder(
    rarity: String?,
    width: Dp = 1.5.dp,
    shape: Shape,
    alpha: Float = 0.5f,
): Modifier {
    val brush = rememberRarityBrush(rarity, alpha)
    return this.border(BorderStroke(width, brush), shape)
}
