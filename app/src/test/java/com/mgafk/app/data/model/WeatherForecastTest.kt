package com.mgafk.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Weather Station has three cards with fixed roles: what is running now, the next Hydro
 * weather, and the next Lunar one. Each card only ever shows its own kind, so a lunar event can
 * never take the Hydro card's place, and neither card can be left empty by an event of the
 * other kind arriving first.
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

    // ── The Hydro card ──

    @Test fun `the hydro card shows the first hydro event`() {
        val forecast = WeatherForecast(now = null, upcoming = listOf(hydroSoon, lunarSoon, hydroLater))

        assertEquals(hydroSoon, forecast.nextHydro(nowMs))
    }

    /** The bug this replaces: a lunar arriving first used to take over the Hydro card. */
    @Test fun `a lunar event never takes the hydro card`() {
        val forecast = WeatherForecast(now = null, upcoming = listOf(lunarSoon, hydroLater))

        assertEquals(hydroLater, forecast.nextHydro(nowMs))
    }

    @Test fun `the gaps between events are not hydro weather`() {
        val clearSkies = event("Sunny", null, 2 * 60_000)
        val forecast = WeatherForecast(now = null, upcoming = listOf(clearSkies, hydroSoon))

        assertEquals(hydroSoon, forecast.nextHydro(nowMs))
    }

    // ── The Lunar card ──

    @Test fun `the lunar card shows the first lunar event`() {
        val forecast = WeatherForecast(now = null, upcoming = listOf(hydroSoon, lunarSoon, lunarLater))

        assertEquals(lunarSoon, forecast.nextLunar(nowMs))
    }

    /**
     * The other half of the same bug: the lunar card used to skip its own event whenever that
     * event was also the next one overall, which is precisely when it should show it.
     */
    @Test fun `the lunar card keeps its event even when it comes first`() {
        val forecast = WeatherForecast(now = null, upcoming = listOf(lunarSoon, hydroLater, lunarLater))

        assertEquals(lunarSoon, forecast.nextLunar(nowMs))
        assertEquals(hydroLater, forecast.nextHydro(nowMs))
    }

    // ── Both cards against the clock ──

    /** The API repeats the running event first; it has started, so it is not "next". */
    @Test fun `an event that already started is not next`() {
        val running = event("Rain", "Hydro", 0)
        val forecast = WeatherForecast(now = running, upcoming = listOf(running, hydroLater))

        assertEquals(hydroLater, forecast.nextHydro(nowMs))
    }

    @Test fun `an event that starts during the refresh window drops out on its own`() {
        val forecast = WeatherForecast(now = null, upcoming = listOf(hydroSoon, hydroLater))

        assertEquals(hydroSoon, forecast.nextHydro(nowMs))
        assertEquals(hydroLater, forecast.nextHydro(nowMs + 5 * 60_000))
    }

    @Test fun `the lunar card moves on once its event has started`() {
        val forecast = WeatherForecast(now = null, upcoming = listOf(lunarSoon, lunarLater))

        assertEquals(lunarSoon, forecast.nextLunar(nowMs))
        assertEquals(lunarLater, forecast.nextLunar(nowMs + 10 * 60_000))
    }

    /**
     * A card with nothing to show means the forecast was not filled in far enough. The
     * repository is what guarantees it (see MgApi.fetchWeatherStation); the model reports the
     * gap honestly rather than borrowing from the other card.
     */
    @Test fun `a forecast without one of the kinds leaves that card empty`() {
        val hydroOnly = WeatherForecast(now = null, upcoming = listOf(hydroSoon, hydroLater))

        assertNull(hydroOnly.nextLunar(nowMs))
        assertEquals(hydroSoon, hydroOnly.nextHydro(nowMs))
    }

    @Test fun `an empty forecast has no cards to show`() {
        val forecast = WeatherForecast(now = null, upcoming = emptyList())

        assertNull(forecast.nextHydro(nowMs))
        assertNull(forecast.nextLunar(nowMs))
    }

    /** What the repository looks at to decide whether it must top the forecast up. */
    @Test fun `the forecast reports which kinds it is missing`() {
        val hydroOnly = WeatherForecast(now = null, upcoming = listOf(hydroSoon))

        assertEquals(true, hydroOnly.hasHydro(nowMs))
        assertEquals(false, hydroOnly.hasLunar(nowMs))

        val both = WeatherForecast(now = null, upcoming = listOf(hydroSoon, lunarSoon))
        assertEquals(true, both.hasHydro(nowMs))
        assertEquals(true, both.hasLunar(nowMs))
    }

    // ── Countdowns ──

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
