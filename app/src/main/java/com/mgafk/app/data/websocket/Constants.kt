package com.mgafk.app.data.websocket

object Constants {
    const val DEFAULT_HOST = "magicgarden.gg"
    const val DEFAULT_VERSION = "db34dc9"
    const val TEXT_PING_MS = 2000L
    const val APP_PING_MS = 2000L
    const val GAME_NAME = "Quinoa"
    /**
     * A complete desktop Chrome UA, consistent with the `platform="desktop"`
     * query parameter. The bare "Mozilla/5.0" we sent before also connected
     * fine, so this is about blending in, not a requirement.
     */
    const val DEFAULT_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36"
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

    /**
     * Fallback max-hunger values when the live game API isn't loaded yet.
     * The source of truth is `MgApi.getPets()[species].coinsToFullyReplenishHunger`
     * - use [maxHungerFor] which checks the API first.
     */
    val PET_HUNGER_COSTS = mapOf(
        "worm" to 500, "snail" to 1000, "bee" to 1500, "chicken" to 3000,
        "bunny" to 750, "dragonfly" to 250, "pig" to 50000, "cow" to 25000,
        "turkey" to 500, "squirrel" to 15000, "turtle" to 100000, "goat" to 20000,
        "snowfox" to 14000, "stoat" to 10000, "whitecaribou" to 30000,
        "caribou" to 30000, "pony" to 4000, "horse" to 25000,
        "firehorse" to 200000, "butterfly" to 25000, "capybara" to 150000,
        "peacock" to 100000, "sheep" to 250,
    )

    /**
     * Max hunger value for a given pet species. Prefers the live API
     * (`coinsToFullyReplenishHunger`) so the app stays correct as the game
     * adds new pets or tweaks values; falls back to [PET_HUNGER_COSTS] when
     * the API hasn't been fetched yet.
     */
    fun maxHungerFor(species: String): Int? {
        // WS payloads use PascalCase species ("Sheep", "WhiteCaribou"...) which
        // matches the API key. Try direct lookup, then case-insensitive
        // fallback for safety.
        val apiEntry = com.mgafk.app.data.repository.MgApi.findPet(species)
            ?: com.mgafk.app.data.repository.MgApi.getPets().entries
                .firstOrNull { it.key.equals(species, ignoreCase = true) }
                ?.value
        val apiValue = apiEntry?.coinsToFullyReplenishHunger
        if (apiValue != null && apiValue > 0) return apiValue
        return PET_HUNGER_COSTS[species.lowercase()]
    }

    val RESTOCK_SECONDS = mapOf(
        "seed" to 300, "tool" to 600, "egg" to 900, "decor" to 3600,
    )

    val WEATHER_MAP = mapOf(
        "sunny" to "Clear Skies",
        "rain" to "Rain",
        "frost" to "Snow",
        "ambermoon" to "Amber Moon",
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
