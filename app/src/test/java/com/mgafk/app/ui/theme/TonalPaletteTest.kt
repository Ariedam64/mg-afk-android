package com.mgafk.app.ui.theme

import com.mgafk.app.data.model.Appearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TonalPaletteTest {

    private val accents = Appearance.ACCENT_PRESETS + listOf(
        0xFFFFFFFF.toInt(), // the extremes a custom pick could reach
        0xFF000000.toInt(),
        0xFF808080.toInt(),
    )

    private fun hex(argb: Int) = "#%08X".format(argb)

    @Test
    fun `text colors are readable on cards and raised elements for every accent`() {
        for (accent in accents) for (dark in listOf(true, false)) {
            val p = TonalPalette.build(accent, dark)
            val where = "accent ${hex(accent)} dark=$dark"
            for ((name, fg) in listOf("primary" to p.textPrimary, "secondary" to p.textSecondary, "muted" to p.textMuted)) {
                assertTrue("$name on surface, $where", TonalPalette.contrast(fg, p.surface) >= 4.5)
                assertTrue("$name on surfaceHigh, $where", TonalPalette.contrast(fg, p.surfaceHigh) >= 4.5)
            }
            assertTrue("accent on surface, $where", TonalPalette.contrast(p.accent, p.surface) >= 4.5)
            assertTrue("status on surface, $where", listOf(p.good, p.warn, p.bad).all { TonalPalette.contrast(it, p.surface) >= 4.5 })
            assertTrue("onAccent on accent, $where", TonalPalette.contrast(p.onAccent, p.accent) >= 4.5)
            assertTrue("onAccentContainer, $where", TonalPalette.contrast(p.onAccentContainer, p.accentContainer) >= 4.5)
        }
    }

    @Test
    fun `surfaces are tinted by the accent so a new accent retints the app`() {
        val violet = TonalPalette.build(0xFF8B6CFF.toInt(), dark = true)
        val teal = TonalPalette.build(0xFF2FA4A0.toInt(), dark = true)
        assertNotEquals(violet.surface, teal.surface)
        assertNotEquals(violet.background, teal.background)
    }

    @Test
    fun `surfaces climb in lightness in dark mode`() {
        val p = TonalPalette.build(Appearance.DEFAULT_ACCENT, dark = true)
        val steps = listOf(p.background, p.surfaceLow, p.surface, p.surfaceHigh, p.hero).map(TonalPalette::luminance)
        assertEquals(steps.sorted(), steps)
    }

    @Test
    fun `mix keeps the endpoints and is opaque`() {
        val a = 0xFF102030.toInt()
        val b = 0xFFF0E0D0.toInt()
        assertEquals(a, TonalPalette.mix(a, b, 0.0))
        assertEquals(b, TonalPalette.mix(a, b, 1.0))
        assertEquals(0xFF, TonalPalette.mix(0x00000000, 0x00FFFFFF, 0.5) ushr 24)
    }
}
