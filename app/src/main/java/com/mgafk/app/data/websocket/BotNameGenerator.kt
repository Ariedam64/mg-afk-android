package com.mgafk.app.data.websocket

import com.mgafk.app.data.model.BotAvatar
import kotlin.random.Random

/**
 * Generates anonymous bot names + avatars mimicking the game's default
 * guest accounts ("Bold Papaya", "Quiet Lemon"...).
 */
object BotNameGenerator {
    private val ADJECTIVES = listOf(
        "Bold", "Quiet", "Sleepy", "Happy", "Lucky", "Brave", "Cosmic",
        "Fluffy", "Sneaky", "Grumpy", "Curious", "Witty", "Calm", "Eager",
        "Jolly", "Wise", "Lazy", "Tiny", "Mighty", "Gentle", "Spicy",
        "Mellow", "Plucky", "Swift", "Silly", "Stoic", "Quirky", "Wild",
        "Sunny", "Frosty",
    )

    private val NOUNS = listOf(
        "Papaya", "Lemon", "Carrot", "Onion", "Apple", "Beet", "Mushroom",
        "Coconut", "Tomato", "Pumpkin", "Banana", "Pepper", "Cabbage",
        "Lily", "Daisy", "Pear", "Peach", "Lychee", "Grape", "Cactus",
        "Bamboo", "Aloe", "Mango", "Plum", "Olive", "Cherry", "Fig",
        "Kiwi", "Radish", "Turnip",
    )

    private val COLORS = listOf(
        "Purple", "Red", "Blue", "Green", "Yellow", "Pink", "Orange", "White",
    )

    /** Generate a `(name, avatar)` pair, avoiding names already in [taken]. */
    fun next(taken: Set<String>): Pair<String, BotAvatar> {
        // Try ~30 times to dodge collisions before falling back to any name.
        var name = ""
        repeat(30) {
            val candidate = "${ADJECTIVES.random()} ${NOUNS.random()}"
            if (candidate !in taken) {
                name = candidate
                return@repeat
            }
        }
        if (name.isEmpty()) name = "${ADJECTIVES.random()} ${NOUNS.random()}"

        val avatar = BotAvatar(color = COLORS.random())
        return name to avatar
    }
}
