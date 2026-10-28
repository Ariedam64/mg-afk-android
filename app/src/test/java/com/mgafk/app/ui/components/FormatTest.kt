package com.mgafk.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `countdown picks the two largest units`() {
        assertEquals("45s", formatCountdown(45_000))
        assertEquals("2m 10s", formatCountdown(130_000))
        assertEquals("1h 04m", formatCountdown(3_840_000))
    }

    @Test
    fun `countdown rounds partial seconds up so it never shows 0s early`() {
        assertEquals("1s", formatCountdown(1))
        assertEquals("0s", formatCountdown(0))
        assertEquals("0s", formatCountdown(-5_000))
    }

    @Test
    fun `clock shows hours only when needed`() {
        assertEquals("2:41", formatClock(161))
        assertEquals("0:05", formatClock(5))
        assertEquals("1:02:03", formatClock(3723))
    }
}
