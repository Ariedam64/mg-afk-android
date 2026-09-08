package com.mgafk.app.data.repository

import com.mgafk.app.data.model.CrystalEffects
import com.mgafk.app.data.model.CrystalShard
import com.mgafk.app.data.model.CrystalType
import com.mgafk.app.data.model.InventoryToolItem
import com.mgafk.app.data.model.OwnedShard
import com.mgafk.app.data.model.PlacedCrystal

/**
 * The rules a crystal follows in the garden, ported from the game's reducer.
 *
 * A shard planted from the inventory runs for four hours. Fusing another shard into a standing
 * crystal tops it up, never past twelve hours, and the fused shard is always consumed whole:
 * anything that will not fit under that ceiling is lost, which is what [wastedSeconds] is for.
 */
object Crystals {

    /** What a shard straight out of the shop is worth once planted. */
    const val FRESH_SHARD_SECONDS = 14_400

    /** The ceiling fusing cannot push a crystal past. */
    const val MAX_ACTIVE_SECONDS = 43_200

    private const val HUNGER_RATE_MULTIPLIER = 0.9
    private const val XP_RATE_MULTIPLIER = 1.1
    private const val STRENGTH_BONUS = 10

    /** How much of a [sourceSeconds] shard a crystal at [targetSeconds] actually gains. */
    fun mergeGainSeconds(targetSeconds: Int, sourceSeconds: Int): Int =
        (minOf(MAX_ACTIVE_SECONDS, targetSeconds + sourceSeconds) - targetSeconds).coerceAtLeast(0)

    /** The part of the fused shard that the ceiling throws away. */
    fun wastedSeconds(targetSeconds: Int, sourceSeconds: Int): Int =
        (sourceSeconds - mergeGainSeconds(targetSeconds, sourceSeconds)).coerceAtLeast(0)

    fun isAtMaxLifespan(seconds: Int): Boolean = seconds >= MAX_ACTIVE_SECONDS

    /**
     * Whether another [type] still fits in a garden holding [placed].
     *
     * Only crystals still running count, so a burnt-out one frees its slot even though it is
     * still standing on its tile.
     */
    fun canPlace(type: CrystalType, placed: List<PlacedCrystal>): Boolean =
        placed.count { it.type == type && it.isActive } < type.activeLimit

    /**
     * What [placed] does to the garden's pets. Each kind is a switch rather than a stack: a
     * second crystal of the same kind buys time, not a bigger effect.
     */
    /**
     * The [type] shards [tools] holds, each with what it is worth.
     *
     * A stack collapses to a single entry: its shards are interchangeable, and the game takes
     * them one at a time by tool id anyway.
     */
    fun ownedShards(type: CrystalType, tools: List<InventoryToolItem>): List<OwnedShard> {
        val mine = tools.filter { it.toolId == type.toolId && it.quantity > 0 }
        return mine.map { tool ->
            val seconds = tool.remainingActiveSeconds
            if (tool.id != null && seconds != null) {
                OwnedShard(type, CrystalShard.Used(tool.id), seconds, fromStack = false)
            } else {
                OwnedShard(type, CrystalShard.Stacked(type.toolId), FRESH_SHARD_SECONDS, fromStack = true)
            }
        }.distinct()
    }

    /**
     * Which shard to plant on an empty tile: the smallest fragment first.
     *
     * A tile takes whatever it is given, so spending the leftovers first keeps the full shards
     * for the fuses where their size actually buys something.
     */
    fun chooseForPlace(shards: List<OwnedShard>): OwnedShard? = shards.minByOrNull { it.seconds }

    /**
     * Which shard to fuse into a crystal at [targetSeconds]: waste as little as possible, and
     * among the shards that waste the same, buy the most time.
     *
     * The shard is consumed whole, so a full one poured into a nearly full crystal is mostly
     * thrown away. Null when nothing is worth spending, which includes a crystal already at
     * the ceiling.
     */
    fun chooseForFuse(shards: List<OwnedShard>, targetSeconds: Int): OwnedShard? = shards
        .filter { mergeGainSeconds(targetSeconds, it.seconds) > 0 }
        .minWithOrNull(
            compareBy<OwnedShard> { wastedSeconds(targetSeconds, it.seconds) }
                .thenByDescending { mergeGainSeconds(targetSeconds, it.seconds) },
        )

    fun effects(placed: List<PlacedCrystal>): CrystalEffects {
        val active = placed.filter { it.isActive }.map { it.type }.toSet()
        return CrystalEffects(
            hungerRateMultiplier = if (CrystalType.Hunger in active) HUNGER_RATE_MULTIPLIER else 1.0,
            xpRateMultiplier = if (CrystalType.XP in active) XP_RATE_MULTIPLIER else 1.0,
            strengthBonus = if (CrystalType.Strength in active) STRENGTH_BONUS else 0,
        )
    }
}
