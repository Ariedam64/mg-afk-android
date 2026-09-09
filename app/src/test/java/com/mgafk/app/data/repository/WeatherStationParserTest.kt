package com.mgafk.app.data.repository

import com.mgafk.app.data.AppJson
import com.mgafk.app.data.model.WeatherEvent
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsed against a captured response rather than a hand-written one, so the shape is the API's
 * and not our idea of it.
 */
class WeatherStationParserTest {

    private val payload = AppJson.default.parseToJsonElement(
        checkNotNull(javaClass.classLoader?.getResourceAsStream("weather_station.json")) {
            "weather_station.json missing from test resources"
        }.bufferedReader().readText()
    ).jsonObject

    private val forecast = WeatherStationParser.parse(payload)

    @Test fun `reads the current weather`() {
        val now = assertNotNull("expected a current weather", forecast.now).let { forecast.now!! }
        assertEquals("Sunny", now.id)
        assertEquals("Clear Skies", now.label)
        // The gaps between events carry no group and no mutation.
        assertNull(now.group)
        assertTrue(now.endsAtMs > now.startsAtMs)
    }

    @Test fun `reads the upcoming events in order`() {
        assertEquals(5, forecast.upcoming.size)
        val starts = forecast.upcoming.map { it.startsAtMs }
        assertEquals(starts.sorted(), starts)
    }

    @Test fun `keeps the fields the cards render`() {
        val hydro = forecast.upcoming.first { it.group == "Hydro" }
        assertTrue(hydro.label.isNotBlank())
        assertTrue(hydro.spriteUrl.orEmpty().startsWith("https://"))
        assertTrue(hydro.mutation.orEmpty().isNotBlank())
    }

    @Test fun `marks lunar events as such`() {
        val lunar = forecast.upcoming.firstOrNull { it.group == WeatherEvent.GROUP_LUNAR }
        assertNotNull("the captured payload should contain a lunar event", lunar)
        assertTrue(lunar!!.isLunar)
        // Dawn and Amber Moon are the only lunar weathers, and each lasts ten minutes.
        assertEquals(10L * 60_000, lunar.endsAtMs - lunar.startsAtMs)
    }

    // ── Topping the forecast up ──

    /**
     * `/weather-station/next?ids=...` answers with the same event shape under `events`, which
     * is what lets the station guarantee a lunar even when the dashboard's short list has none.
     */
    @Test fun `it reads the targeted next endpoint`() {
        val payload = AppJson.default.parseToJsonElement(
            """
            {"from": 1788952208465, "count": 1, "complete": true, "events": [
              {"id": "AmberMoon", "weather": "Amber Moon", "group": "Lunar",
               "started_at": 1788955200000, "ended_at": 1788955800000,
               "mutation": "Ambershine", "sprite": "https://example/AmberMoonIcon.png"}
            ]}
            """.trimIndent()
        ).jsonObject

        val events = WeatherStationParser.parseEvents(payload)

        assertEquals(1, events.size)
        assertEquals("AmberMoon", events.single().id)
        assertEquals("Amber Moon", events.single().label)
        assertTrue(events.single().isLunar)
        assertEquals(1788955200000L, events.single().startsAtMs)
    }

    @Test fun `a response without events reads as none`() {
        val empty = AppJson.default.parseToJsonElement("""{"count": 0}""").jsonObject

        assertTrue(WeatherStationParser.parseEvents(empty).isEmpty())
    }

    @Test fun `a payload without a forecast parses to an empty one`() {
        val empty = WeatherStationParser.parse(AppJson.default.parseToJsonElement("{}").jsonObject)

        assertNull(empty.now)
        assertTrue(empty.upcoming.isEmpty())
    }
}
