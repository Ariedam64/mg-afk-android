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
 * The game's own `/platform/v1/shops` carries the weather block the station now reads, shaped
 * as captured from it: ISO timestamps, a `current` that is null during the Clear Skies gaps,
 * and an `upcoming` list holding the next event of each group.
 *
 * The game withholds which lunar weather is coming (`weatherId` and `name` are null on it),
 * which is the same secret the Lunar card keeps.
 */
class GameWeatherParserTest {

    private fun parse(body: String) =
        GameWeatherParser.parse(AppJson.default.parseToJsonElement(body).jsonObject)

    private val payload = """
        {"weather": {
          "current": {
            "weatherId": "Frost", "name": "Snow", "groupId": "Hydro",
            "startsAt": "2026-09-11T14:20:00.000Z",
            "endsAt": "2026-09-11T14:30:00.000Z"
          },
          "upcoming": [
            {"weatherId": null, "name": null, "groupId": "Lunar",
             "startsAt": "2026-09-11T16:00:00.000Z", "endsAt": "2026-09-11T16:10:00.000Z"},
            {"weatherId": "Thunderstorm", "name": "Thunderstorm", "groupId": "Hydro",
             "startsAt": "2026-09-11T17:05:00.000Z", "endsAt": "2026-09-11T17:15:00.000Z"}
          ]
        }}
    """.trimIndent()

    @Test fun `it reads the running weather`() {
        val now = assertNotNull("expected a current weather", parse(payload).now).let { parse(payload).now!! }

        assertEquals("Frost", now.id)
        assertEquals("Snow", now.label)
        assertEquals(WeatherEvent.GROUP_HYDRO, now.group)
        assertEquals(1789136400000L, now.startsAtMs)
        assertEquals(1789137000000L, now.endsAtMs)
    }

    @Test fun `it reads one upcoming event per group`() {
        val forecast = parse(payload)
        val at = forecast.now!!.startsAtMs

        assertEquals("Thunderstorm", forecast.nextHydro(at)?.id)
        assertEquals(WeatherEvent.GROUP_LUNAR, forecast.nextLunar(at)?.group)
    }

    /** The game hides which lunar it will be, so the card has nothing to leak. */
    @Test fun `an unnamed lunar keeps its group and its timing`() {
        val lunar = parse(payload).nextLunar(1789136400000L)!!

        assertTrue(lunar.isLunar)
        assertEquals(1789142400000L, lunar.startsAtMs)
        assertEquals("", lunar.id)
        assertEquals("Lunar", lunar.label)
    }

    // ── The Clear Skies gaps ──

    /**
     * Between events the game reports no current weather at all. That is Clear Skies, and it
     * runs until the next event starts, so the Now card stays filled and still counts down.
     */
    @Test fun `no current weather reads as Clear Skies until the next event`() {
        val gap = parse(
            """
            {"weather": {"current": null, "upcoming": [
              {"weatherId": "Thunderstorm", "name": "Thunderstorm", "groupId": "Hydro",
               "startsAt": "2026-09-17T09:15:00.000Z", "endsAt": "2026-09-17T09:25:00.000Z"}
            ]}}
            """.trimIndent()
        )

        val now = gap.now!!
        assertEquals("Sunny", now.id)
        assertEquals("Clear Skies", now.label)
        assertNull("a gap belongs to no group", now.group)
        assertEquals(1789636500000L, now.endsAtMs)
    }

    @Test fun `a gap with nothing coming has no end to count down to`() {
        val gap = parse("""{"weather": {"current": null, "upcoming": []}}""")

        assertEquals("Sunny", gap.now?.id)
        assertEquals(0L, gap.now?.endsAtMs)
    }

    @Test fun `a payload without a weather block parses to nothing`() {
        val empty = parse("""{"shops": {}}""")

        assertNull(empty.now)
        assertTrue(empty.upcoming.isEmpty())
    }
}
