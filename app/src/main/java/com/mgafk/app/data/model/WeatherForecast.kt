package com.mgafk.app.data.model

/**
 * One weather event on the station's timeline.
 *
 * The API sends its own `starts_in_ms` / `ends_in_ms`, but those are stale the moment the
 * response arrives, so only the absolute timestamps are kept and the countdowns are measured
 * against the current clock.
 */
data class WeatherEvent(
    val id: String,
    /** The name the game shows, e.g. "Amber Moon" for the id `AmberMoon`. */
    val label: String,
    /** "Hydro", "Lunar", or null for the Clear Skies gaps between events. */
    val group: String?,
    val mutation: String?,
    val spriteUrl: String?,
    val startsAtMs: Long,
    val endsAtMs: Long,
) {
    val isLunar: Boolean get() = group == GROUP_LUNAR

    fun startsInMs(atMs: Long): Long = (startsAtMs - atMs).coerceAtLeast(0L)

    fun endsInMs(atMs: Long): Long = (endsAtMs - atMs).coerceAtLeast(0L)

    companion object {
        const val GROUP_LUNAR = "Lunar"
    }
}

/**
 * What the Weather Station shows: the current weather and what is coming.
 *
 * The three cards are [now], [next] and [nextLunar]. The last one deliberately skips [next]
 * when that is already a lunar event, so the two cards never announce the same thing; that is
 * the game's own rule.
 */
data class WeatherForecast(
    val now: WeatherEvent?,
    val upcoming: List<WeatherEvent> = emptyList(),
) {
    /**
     * Events that have not started yet at [atMs].
     *
     * The API repeats the running event as the first entry of its list, and an event can start
     * between two refreshes, so "upcoming" is decided against the clock rather than trusted.
     */
    private fun stillToCome(atMs: Long): List<WeatherEvent> = upcoming.filter { it.startsAtMs > atMs }

    fun next(atMs: Long): WeatherEvent? = stillToCome(atMs).firstOrNull()

    fun nextLunar(atMs: Long): WeatherEvent? {
        val toCome = stillToCome(atMs)
        // Skip the Next card's own event when it is lunar, so the two never say the same thing.
        val candidates = if (toCome.firstOrNull()?.isLunar == true) toCome.drop(1) else toCome
        return candidates.firstOrNull { it.isLunar }
    }
}
