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

    /** Rain, Snow and Thunderstorm. The Clear Skies gaps carry no group and are neither. */
    val isHydro: Boolean get() = group == GROUP_HYDRO

    fun startsInMs(atMs: Long): Long = (startsAtMs - atMs).coerceAtLeast(0L)

    fun endsInMs(atMs: Long): Long = (endsAtMs - atMs).coerceAtLeast(0L)

    companion object {
        const val GROUP_LUNAR = "Lunar"
        const val GROUP_HYDRO = "Hydro"

        /** The two lunar weathers, which is what the station has to ask the API for by name. */
        val LUNAR_IDS = listOf("Dawn", "AmberMoon")

        /** The three hydro weathers, same reason. */
        val HYDRO_IDS = listOf("Rain", "Frost", "Thunderstorm")
    }
}

/**
 * What the Weather Station shows, as three cards with fixed roles: [now], [nextHydro] and
 * [nextLunar].
 *
 * Each card only ever shows its own kind. A lunar event landing before the next hydro one does
 * not take the hydro card, and the lunar card shows its own next event even when that is also
 * the next event overall. Neither card should be left empty: the repository tops the forecast
 * up when the dashboard's short list happens to hold only one of the two kinds.
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

    fun nextHydro(atMs: Long): WeatherEvent? = stillToCome(atMs).firstOrNull { it.isHydro }

    fun nextLunar(atMs: Long): WeatherEvent? = stillToCome(atMs).firstOrNull { it.isLunar }

    fun hasHydro(atMs: Long): Boolean = nextHydro(atMs) != null

    fun hasLunar(atMs: Long): Boolean = nextLunar(atMs) != null
}
