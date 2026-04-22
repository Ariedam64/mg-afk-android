package com.mgafk.app.data.websocket

object Constants {
    const val DEFAULT_HOST = "magicgarden.gg"
    const val DEFAULT_VERSION = "db34dc9"
    const val TEXT_PING_MS = 2000L
    const val APP_PING_MS = 2000L
    const val GAME_NAME = "Quinoa"
    const val DEFAULT_UA = "Mozilla/5.0"
    const val RETRY_MAX = Int.MAX_VALUE
    const val RETRY_DELAY_MS = 1500L
    const val RETRY_JITTER_MS = 1000L
    const val RETRY_MAX_DELAY_MS = 60000L
    const val AUTH_RETRY_MAX = 5

    val KNOWN_CLOSE_CODES = setOf(4100, 4200, 4250, 4300, 4310, 4400, 4500, 4700, 4710, 4800)
    val SUPERSEDED_CODES = setOf(4250, 4300)
    val BLOCKED_ABILITIES = setOf("dawnkisser", "moonkisser")

    const val DISCORD_OAUTH_URL =
        "https://discord.com/oauth2/authorize?client_id=1227719606223765687" +
        "&response_type=code" +
        "&redirect_uri=https%3A%2F%2Fmagicgarden.gg%2Foauth2%2Fredirect" +
        "&scope=identify+guilds.members.read+guilds"

    val PET_HUNGER_COSTS = mapOf(
        "worm" to 500, "snail" to 1000, "bee" to 1500, "chicken" to 3000,
        "bunny" to 750, "dragonfly" to 250, "pig" to 50000, "cow" to 25000,
        "turkey" to 500, "squirrel" to 15000, "turtle" to 100000, "goat" to 20000,
        "snowfox" to 14000, "stoat" to 10000, "whitecaribou" to 30000,
        "caribou" to 30000, "pony" to 4000, "horse" to 4000,
        "firehorse" to 200000, "butterfly" to 25000, "capybara" to 150000,
        "peacock" to 100000,
    )

    val RESTOCK_SECONDS = mapOf(
        "seed" to 300, "tool" to 600, "egg" to 900, "decor" to 3600,
    )

    val WEATHER_MAP = mapOf(
        "sunny" to "Clear Skies",
        "rain" to "Rain",
        "frost" to "Snow",
        "amber moon" to "Amber Moon",
        "dawn" to "Dawn",
    )

    const val PET_HUNGER_THRESHOLD = 5

    fun formatWeather(value: String?): String {
        if (value.isNullOrBlank()) return "Clear Skies"
        return WEATHER_MAP[value.trim().lowercase()] ?: value.trim()
    }

    fun isAbilityName(action: String?): Boolean {
        if (action.isNullOrBlank()) return false
        val trimmed = action.trim()
        // Always exclude blocked actions (MoonKisser/DawnKisser etc.) even when
        // the API list is loaded.
        if (trimmed.lowercase() in BLOCKED_ABILITIES) return false
        val abilities = com.mgafk.app.data.repository.MgApi.getAbilities()
        return if (abilities.isEmpty()) true else abilities.containsKey(trimmed)
    }

    fun fmtDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }
}
