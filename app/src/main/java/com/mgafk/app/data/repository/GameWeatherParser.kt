package com.mgafk.app.data.repository

import com.mgafk.app.data.model.WeatherEvent
import com.mgafk.app.data.model.WeatherForecast
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/**
 * Reads the weather block the game publishes on `/platform/v1/shops`.
 *
 * This is the game's own schedule rather than a model of it, so it needs no topping up: the
 * block carries the running weather and the next event of each group, which is exactly what the
 * station's three cards show.
 *
 * Two shapes to know. Between events the game reports no current weather at all, which is Clear
 * Skies and lasts until the next event starts. And a lunar event arrives with no id and no name,
 * because the game withholds which of Dawn or Amber Moon it will be.
 */
object GameWeatherParser {

    /** What the game calls the gaps between events, and the id its sprite is filed under. */
    private const val CLEAR_SKIES_ID = "Sunny"
    private const val CLEAR_SKIES_LABEL = "Clear Skies"

    fun parse(payload: JsonObject): WeatherForecast {
        val weather = payload["weather"] as? JsonObject ?: return WeatherForecast(now = null)
        val upcoming = (weather["upcoming"] as? JsonArray)
            ?.mapNotNull { element -> (element as? JsonObject)?.let(::event) }
            ?.sortedBy { it.startsAtMs }
            .orEmpty()
        val current = (weather["current"] as? JsonObject)?.let(::event)
            ?: clearSkiesUntil(upcoming.firstOrNull()?.startsAtMs)

        return WeatherForecast(now = current, upcoming = upcoming)
    }

    /**
     * The gap between two events, so the Now card stays filled and still counts down.
     *
     * With nothing scheduled there is no end to count to, which the card reads as no countdown
     * rather than as a gap that just ended.
     */
    private fun clearSkiesUntil(nextStartsAtMs: Long?) = WeatherEvent(
        id = CLEAR_SKIES_ID,
        label = CLEAR_SKIES_LABEL,
        group = null,
        mutation = null,
        spriteUrl = null,
        startsAtMs = 0L,
        endsAtMs = nextStartsAtMs ?: 0L,
    )

    /** Null for an entry without the timings the cards count down to. */
    private fun event(obj: JsonObject): WeatherEvent? {
        val startsAt = obj.instant("startsAt") ?: return null
        val endsAt = obj.instant("endsAt") ?: return null
        val group = obj.string("groupId")
        return WeatherEvent(
            // A lunar event carries neither, and the card shows it by its group alone.
            id = obj.string("weatherId").orEmpty(),
            label = obj.string("name") ?: group.orEmpty(),
            group = group,
            mutation = null,
            spriteUrl = null,
            startsAtMs = startsAt,
            endsAtMs = endsAt,
        )
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    /** The game sends ISO-8601 instants, e.g. `2026-09-11T14:20:00.000Z`. */
    private fun JsonObject.instant(key: String): Long? = string(key)?.let {
        try {
            Instant.parse(it).toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }
}
