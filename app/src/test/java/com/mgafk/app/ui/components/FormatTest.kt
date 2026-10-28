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

    @Test
    fun `time remaining shows two units at most and drops seconds once hours show`() {
        val now = 1_000_000L
        assertEquals("45s", formatTimeRemaining(now + 45_000, now))
        assertEquals("2m 30s", formatTimeRemaining(now + 150_000, now))
        assertEquals("1h 5m", formatTimeRemaining(now + 3_930_000, now))
    }

    @Test
    fun `time remaining truncates partial seconds and is blank once due`() {
        val now = 1_000_000L
        assertEquals("1s", formatTimeRemaining(now + 1_999, now))
        assertEquals("0s", formatTimeRemaining(now + 999, now))
        assertEquals("", formatTimeRemaining(now, now))
        assertEquals("", formatTimeRemaining(now - 5_000, now))
    }

    @Test
    fun `quantity shows exact counts below a thousand`() {
        assertEquals("0", formatQuantity(0))
        assertEquals("999", formatQuantity(999))
    }

    @Test
    fun `quantity abbreviates thousands and millions`() {
        // One decimal follows the device locale, so build the expected separator the same way.
        assertEquals("%.1fK".format(1.5), formatQuantity(1_500))
        assertEquals("%.1fK".format(9.9), formatQuantity(9_949))
        assertEquals("12K", formatQuantity(12_345))
        assertEquals("999K", formatQuantity(999_999))
        assertEquals("%.1fM".format(1.2), formatQuantity(1_234_567))
    }

    @Test
    fun `quantity drops a trailing zero decimal`() {
        assertEquals("1K", formatQuantity(1_000))
        assertEquals("1K", formatQuantity(1_049))
        assertEquals("2M", formatQuantity(2_000_000))
        assertEquals("2M", formatQuantity(2_040_000))
    }
}
