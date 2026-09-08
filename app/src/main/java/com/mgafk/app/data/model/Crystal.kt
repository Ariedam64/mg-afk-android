package com.mgafk.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * The two tile maps a garden is made of. A crystal can sit on either, unlike a plant.
 *
 * The names are the wire values the game expects in `tileType`.
 */
@Serializable
enum class GardenTileType {
    Dirt,
    Boardwalk,
}

/** Which tile map a garden cell belongs to, and its index within that map. */
@Serializable
data class GardenTileRef(val tileType: GardenTileType, val index: Int)

/**
 * A crystal kind, as the game models it: an inventory shard ([toolId]) that becomes a placed
 * crystal ([id], the wire value of `crystalType`) once planted.
 *
 * Only what drives an action is named here. Display names, sprites and rarities come from
 * MgApi, keyed by [toolId].
 */
@Serializable
enum class CrystalType(
    val id: String,
    val toolId: String,
    /** Weather mutation this crystal blocks around itself, or null for the pet crystals. */
    val blockedMutation: String?,
    /** How many of this kind can be active in one garden at a time. */
    val activeLimit: Int,
) {
    RainWard("RainWard", "RainWardShard", "Wet", 4),
    SnowWard("SnowWard", "SnowWardShard", "Chilled", 4),
    ThunderWard("ThunderWard", "ThunderWardShard", "Thunderstruck", 4),
    Hunger("Hunger", "HungerShard", null, 1),
    XP("XP", "XPShard", null, 1),
    Strength("Strength", "StrengthShard", null, 1);

    val isWard: Boolean get() = blockedMutation != null

    companion object {
        fun fromId(id: String?): CrystalType? = entries.find { it.id == id }

        fun fromToolId(toolId: String?): CrystalType? = entries.find { it.toolId == toolId }
    }
}

/**
 * How a shard is named when it is planted, which depends on where it comes from.
 *
 * A shard bought from a shop sits in a stack and is addressed by its tool id. One that was
 * picked back up carries its own remaining time, so it is a unique item with an id of its own.
 */
sealed interface CrystalShard {

    /** One shard out of a stack, worth [Crystals.FRESH_SHARD_SECONDS] when planted. */
    data class Stacked(val toolId: String) : CrystalShard

    /** A shard picked back up, which kept the time it had left. */
    data class Used(val itemId: String) : CrystalShard

    /** The wire shape the game expects in `PlaceCrystal.item`. */
    fun toJson(): JsonObject = buildJsonObject {
        put("itemType", JsonPrimitive(TOOL_ITEM_TYPE))
        when (this@CrystalShard) {
            is Stacked -> put("toolId", JsonPrimitive(toolId))
            is Used -> put("itemId", JsonPrimitive(itemId))
        }
    }

    companion object {
        private const val TOOL_ITEM_TYPE = "Tool"
    }
}

/**
 * A shard the player owns and could spend, with what it is worth once planted.
 *
 * [fromStack] shards are interchangeable; the others were picked back up and each carries the
 * time it had left, which is what makes choosing between them worth thinking about.
 */
data class OwnedShard(
    val type: CrystalType,
    val ref: CrystalShard,
    val seconds: Int,
    val fromStack: Boolean,
)

/** A crystal standing in the garden, on whichever of the two tile maps holds it. */
@Serializable
data class PlacedCrystal(
    val type: CrystalType,
    val tileType: GardenTileType,
    val localTileIndex: Int,
    /**
     * Seconds of effect left, as the server last reported them. It only counts down while the
     * player is in the room, so a client-side countdown has to freeze when the session drops.
     */
    val remainingSeconds: Int,
) {
    val isActive: Boolean get() = remainingSeconds > 0
}

/** What the crystals standing in a garden do to its pets, all effects combined. */
data class CrystalEffects(
    val hungerRateMultiplier: Double = 1.0,
    val xpRateMultiplier: Double = 1.0,
    val strengthBonus: Int = 0,
) {
    val hasAny: Boolean get() = this != CrystalEffects()
}
