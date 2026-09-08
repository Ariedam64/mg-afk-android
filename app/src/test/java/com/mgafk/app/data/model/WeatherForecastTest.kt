package com.mgafk.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Weather Station shows three cards: what is happening now, what comes next, and the next
 * lunar event. The third one is not simply "the next Lunar": when the next event is already
 * lunar, the game shows the one after it instead, so the two cards never say the same thing.
 */
class WeatherForecastTest {

    private fun event(id: String, group: String?, startsInMs: Long) = WeatherEvent(
        id = id,
        label = id,
        group = group,
        mutation = null,
        spriteUrl = null,
        startsAtMs = 1_000_000L + startsInMs,
        endsAtMs = 1_000_000L + startsInMs + 600_000L,
    )

    private val hydroSoon = event("Rain", "Hydro", 5 * 60_000)
    private val hydroLater = event("Thunderstorm", "Hydro", 40 * 60_000)
    private val lunarSoon = event("Dawn", "Lunar", 10 * 60_000)
    private val lunarLater = event("AmberMoon", "Lunar", 4 * 60 * 60_000)

    private val nowMs = 1_000_000L

    @Test fun `next is the first upcoming event`() {
        val forecast = WeatherForecast(now = null, upcoming = listOf(hydroSoon, lunarSoon, lunarLater))

        assertEquals(hydroSoon, forecast.next(nowMs))
    }

    /**
     * The API repeats the running event as the first entry of its `next` list, and between two
     * refreshes an upcoming event can start. Neither is "next", or the card counts down to zero
     * and sits there next to a Now card showing the very same weather.
     */
    @Test fun `an event that already started is not next`() {
        val running = event("Rain", "Hydro", 0)
        val forecast = WeatherForecast(now = running, upcoming = listOf(running, hydroLater))

        assertEquals(hydroLater, forecast.next(nowMs))
    }

    @Test fun `an event that starts during the refresh window drops out on its own`() {
        val forecast = WeatherForecast(now = null, upcoming = listOf(hydroSoon, hydroLater))

        assertEquals(hydroSoon, forecast.next(nowMs))
        // Five minutes later hydroSoon has begun, so the card moves on without a refetch.
        assertEquals(hydroLater, forecast.next(nowMs + 5 * 60_000))
    }

    @Test fun `the lunar card is the first lunar event when the next one is not lunar`() {
        val forecast = WeatherForecast(now = null, upcoming = listOf(hydroSoon, lunarSoon, lunarLater))

        assertEquals(lunarSoon, forecast.nextLunar(nowMs))
    }

    /** Otherwise both cards would show the same event, which is what the game avoids. */
    @Test fun `the lunar card skips the next event when that one is already lunar`() {
        val forecast = WeatherForecast(now = null, upcoming = listOf(lunarSoon, hydroLater, lunarLater))

        assertEquals(lunarSoon, forecast.next(nowMs))
        assertEquals(lunarLater, forecast.nextLunar(nowMs))
    }

    @Test fun `an empty forecast has no cards to show`() {
        val forecast = WeatherForecast(now = null, upcoming = emptyList())

        assertNull(forecast.next(nowMs))
        assertNull(forecast.nextLunar(nowMs))
    }

    @Test fun `no lunar in range leaves that card empty`() {
        val forecast = WeatherForecast(now = null, upcoming = listOf(hydroSoon, hydroLater))

        assertNull(forecast.nextLunar(nowMs))
    }

    // ── Countdowns ──

    /**
     * The payload's own countdowns are stale the moment it arrives, so the card counts from the
     * absolute timestamps instead. Anything already past reads as zero rather than negative.
     */
    @Test fun `a countdown is measured from the current time`() {
        assertEquals(5 * 60_000L, hydroSoon.startsInMs(atMs = 1_000_000L))
        assertEquals(60_000L, hydroSoon.startsInMs(atMs = 1_000_000L + 4 * 60_000))
    }

    @Test fun `a countdown never goes negative`() {
        assertEquals(0L, hydroSoon.startsInMs(atMs = 1_000_000L + 10 * 60_000))
        assertEquals(0L, hydroSoon.endsInMs(atMs = 1_000_000L + 60 * 60_000))
    }

    @Test fun `the current event counts down to its end`() {
        val current = event("Rain", "Hydro", 0)

        assertEquals(600_000L, current.endsInMs(atMs = 1_000_000L))
        assertEquals(300_000L, current.endsInMs(atMs = 1_000_000L + 300_000))
    }
}
