package com.mgafk.app.data.websocket.state

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.floor

/**
 * Where an equipped pet stands, from its entry in the player's petSlotInfos. A potion or a
 * ride needs the player on that tile.
 *
 * The game has used three shapes: `{ motion: { path, startedAtMs, stepDurationMs } }` since
 * build 1650, `{ motion: { at } }` before it, and `{ position }` before that. Reading only
 * `motion.at` found no tile once 1650 shipped, so no potion was ever drunk.
 */
object PetTile {

    /** The pet's tile at [nowMs], or null when its entry has none of the known shapes. */
    fun of(info: JsonObject?, nowMs: Long): Pair<Double, Double>? {
        if (info == null) return null
        return motionTile(info["motion"] as? JsonObject, nowMs) ?: point(info["position"])
    }

    /**
     * The tile a motion has the pet on at [nowMs], as the game's client works it out: one step
     * of the path every stepDurationMs from startedAtMs, resting on the last tile once the walk
     * is over.
     */
    fun motionTile(motion: JsonObject?, nowMs: Long): Pair<Double, Double>? {
        if (motion == null) return null
        val path = motion["path"] as? JsonArray
        if (path == null || path.isEmpty()) return point(motion["at"])
        val steps = path.size - 1
        val stepMs = number(motion["stepDurationMs"]) ?: 0.0
        val startedAtMs = number(motion["startedAtMs"]) ?: 0.0
        val elapsed = maxOf(0.0, nowMs.toDouble() - startedAtMs)
        val step = if (stepMs <= 0.0) steps else minOf(steps.toDouble(), floor(elapsed / stepMs)).toInt()
        return point(path[step])
    }

    private fun point(element: JsonElement?): Pair<Double, Double>? {
        val obj = element as? JsonObject ?: return null
        val x = number(obj["x"]) ?: return null
        val y = number(obj["y"]) ?: return null
        return x to y
    }

    private fun number(element: JsonElement?): Double? = (element as? JsonPrimitive)?.doubleOrNull
}
