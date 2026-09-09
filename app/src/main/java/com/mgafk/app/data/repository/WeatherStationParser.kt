package com.mgafk.app.data.repository

import com.mgafk.app.data.model.WeatherEvent
import com.mgafk.app.data.model.WeatherForecast
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Turns the `/weather-station` payload into the three cards' data.
 *
 * Only the absolute timestamps are kept: the payload's own `starts_in_ms` / `ends_in_ms` are
 * measured at the server's `generated_at` and are already stale on arrival, so the countdowns
 * are recomputed locally (see [WeatherEvent.startsInMs]).
 */
object WeatherStationParser {

    fun parse(payload: JsonObject): WeatherForecast = WeatherForecast(
        now = (payload["now"] as? JsonObject)?.let(::event),
        upcoming = (payload["next"] as? JsonArray)
            ?.mapNotNull { element -> (element as? JsonObject)?.let(::event) }
            ?.sortedBy { it.startsAtMs }
            .orEmpty(),
    )

    /**
     * The events of a `/weather-station/next` answer, which carries them under `events` in the
     * same shape the dashboard uses.
     *
     * That endpoint is how the station fills a card the dashboard's five-entry list left empty:
     * asked for the lunar ids, it scans forward until it finds one.
     */
    fun parseEvents(payload: JsonObject): List<WeatherEvent> =
        (payload["events"] as? JsonArray)
            ?.mapNotNull { element -> (element as? JsonObject)?.let(::event) }
            ?.sortedBy { it.startsAtMs }
            .orEmpty()

    /** Null for an entry missing the timestamps the cards count down to. */
    private fun event(obj: JsonObject): WeatherEvent? {
        val id = obj.string("id") ?: return null
        val startsAt = obj.long("started_at") ?: return null
        val endsAt = obj.long("ended_at") ?: return null
        return WeatherEvent(
            id = id,
            label = obj.string("weather") ?: id,
            group = obj.string("group"),
            mutation = obj.string("mutation"),
            spriteUrl = obj.string("sprite"),
            startsAtMs = startsAt,
            endsAtMs = endsAt,
        )
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
}
