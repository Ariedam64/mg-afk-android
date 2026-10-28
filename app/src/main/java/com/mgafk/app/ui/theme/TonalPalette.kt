package com.mgafk.app.ui.theme

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Every color the app paints with, as ARGB ints. Built by [TonalPalette.build] from one
 * accent: each surface is a neutral ground tinted toward the accent, so picking a new
 * accent retints the whole app.
 */
data class PaletteArgb(
    val isDark: Boolean,
    /** Window background, the lowest layer. */
    val background: Int,
    /** Inset areas inside a card (rows, input fields). */
    val surfaceLow: Int,
    /** Cards. */
    val surface: Int,
    /** Raised elements on a card: chips, progress tracks, the bottom bar. */
    val surfaceHigh: Int,
    /** The one big highlighted card on a screen (weather on Home). */
    val hero: Int,
    val outline: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textMuted: Int,
    /** The accent, nudged until it reads as text on [surface]. */
    val accent: Int,
    /** Text and icons drawn on an [accent] fill. */
    val onAccent: Int,
    /** Selected chip or pill background. */
    val accentContainer: Int,
    val onAccentContainer: Int,
    val good: Int,
    val warn: Int,
    val bad: Int,
    val idle: Int,
    /** Text on a [good] or [bad] fill, e.g. a confirm or delete button. */
    val onGood: Int,
    val onBad: Int,
)

object TonalPalette {
    private const val DARK_GROUND = 0xFF121016.toInt()
    private const val LIGHT_GROUND = 0xFFFBF9FE.toInt()
    private const val INK_DARK = 0xFF14111A.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()

    /** WCAG AA for body text. */
    const val TEXT_CONTRAST = 4.5

    fun build(accentArgb: Int, dark: Boolean): PaletteArgb {
        val ground = if (dark) DARK_GROUND else LIGHT_GROUND
        fun tint(amount: Double) = mix(ground, accentArgb, amount)
        val toward = if (dark) WHITE else BLACK

        val background = tint(if (dark) 0.04 else 0.03)
        val surfaceLow = tint(if (dark) 0.07 else 0.05)
        val surface = tint(if (dark) 0.11 else 0.08)
        val surfaceHigh = tint(if (dark) 0.20 else 0.16)
        val hero = tint(if (dark) 0.28 else 0.22)
        val outline = tint(if (dark) 0.26 else 0.24)

        val textPrimary = if (dark) 0xFFECE8F4.toInt() else 0xFF1B1823.toInt()
        // Secondary and muted text must stay readable on the busiest surface they sit on.
        val textSecondary = readable(mix(textPrimary, surface, 0.25), surfaceHigh, toward)
        val textMuted = readable(mix(textPrimary, surface, 0.4), surfaceHigh, toward)

        val accent = readable(accentArgb, surface, toward)
        val onAccent = bestOn(accent, INK_DARK, WHITE)
        val accentContainer = mix(surface, accentArgb, if (dark) 0.35 else 0.22)
        // A very light accent makes this container light even in dark mode, so pick the side.
        val onAccentContainer = readable(textPrimary, accentContainer, bestOn(accentContainer, BLACK, WHITE))

        val good = readable(if (dark) 0xFF7FE0A0.toInt() else 0xFF1E7A3C.toInt(), surface, toward)
        val warn = readable(if (dark) 0xFFF4C55A.toInt() else 0xFF8F5600.toInt(), surface, toward)
        val bad = readable(if (dark) 0xFFFF8A80.toInt() else 0xFFB3261E.toInt(), surface, toward)
        val idle = readable(if (dark) 0xFF8A8796.toInt() else 0xFF6B6878.toInt(), surface, toward)

        return PaletteArgb(
            isDark = dark,
            background = background,
            surfaceLow = surfaceLow,
            surface = surface,
            surfaceHigh = surfaceHigh,
            hero = hero,
            outline = outline,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            textMuted = textMuted,
            accent = accent,
            onAccent = onAccent,
            accentContainer = accentContainer,
            onAccentContainer = onAccentContainer,
            good = good,
            warn = warn,
            bad = bad,
            idle = idle,
            onGood = bestOn(good, INK_DARK, WHITE),
            onBad = bestOn(bad, INK_DARK, WHITE),
        )
    }

    /** Linear blend from [from] (t = 0) to [to] (t = 1), alpha forced opaque. */
    fun mix(from: Int, to: Int, t: Double): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * t).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** WCAG relative luminance. */
    fun luminance(argb: Int): Double {
        fun linear(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
    }

    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** Moves [fg] toward [toward] in small steps until it reaches [TEXT_CONTRAST] on [bg]. */
    fun readable(fg: Int, bg: Int, toward: Int): Int {
        var color = fg
        repeat(20) {
            if (contrast(color, bg) >= TEXT_CONTRAST) return color
            color = mix(color, toward, 0.12)
        }
        // Mid-grey backgrounds only clear the bar with pure black or white.
        return toward
    }

    private fun bestOn(bg: Int, dark: Int, light: Int): Int =
        if (contrast(bg, dark) >= contrast(bg, light)) dark else light
}
