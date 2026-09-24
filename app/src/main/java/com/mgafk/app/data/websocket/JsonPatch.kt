package com.mgafk.app.data.websocket

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Minimal RFC 6902 JSON Pointer patch implementation, over kotlinx.serialization trees.
 *
 * Every screen in the app is a view of the state these patches maintain, so an operation applied
 * wrongly does not fail loudly: it quietly shows the player something the game does not agree
 * with, until a reconnect rebuilds the state from scratch.
 */
object JsonPatch {

    /** The RFC's "one past the end" token, used to append to an array. */
    private const val APPEND = "-"

    fun decodePointer(path: String): List<String> =
        path.split("/").drop(1).map { it.replace("~1", "/").replace("~0", "~") }

    /** An empty segment is a key named "", not index zero. */
    private fun isIndex(value: String): Boolean = value.isNotEmpty() && value.all { it.isDigit() }

    private fun addressesArray(key: String): Boolean = isIndex(key) || key == APPEND

    /**
     * Apply a single patch operation to a JsonElement tree.
     * Returns the new root (since JsonElement is immutable).
     */
    fun applyPatch(
        target: JsonElement,
        path: String,
        value: JsonElement?,
        op: String?,
    ): JsonElement {
        val segments = decodePointer(path)
        if (segments.isEmpty()) return value ?: target

        // Skip leading "data" segment like the JS version
        val startIdx = if (segments.firstOrNull() == "data") 1 else 0
        val segs = segments.subList(startIdx, segments.size)
        if (segs.isEmpty()) return value ?: target

        return applyAtPath(target, segs, 0, value, op)
    }

    private fun applyAtPath(
        current: JsonElement,
        segments: List<String>,
        index: Int,
        value: JsonElement?,
        op: String?,
    ): JsonElement {
        val key = segments[index]

        if (index == segments.lastIndex) {
            return when {
                op == "remove" && current is JsonObject -> buildJsonObject {
                    current.forEach { (k, v) -> if (k != key) put(k, v) }
                }

                op == "remove" && current is JsonArray && isIndex(key) -> {
                    val n = key.toInt()
                    buildJsonArray { current.forEachIndexed { i, v -> if (i != n) add(v) } }
                }

                current is JsonArray && addressesArray(key) ->
                    writeIntoArray(current, key, value, op)

                current is JsonObject -> buildJsonObject {
                    current.forEach { (k, v) -> put(k, v) }
                    if (value != null) put(key, value)
                }

                else -> current
            }
        }

        val nextKey = segments[index + 1]
        return when (current) {
            is JsonObject -> {
                val child = current[key] ?: emptyChildFor(nextKey)
                val updated = applyAtPath(child, segments, index + 1, value, op)
                buildJsonObject {
                    current.forEach { (k, v) -> put(k, v) }
                    put(key, updated)
                }
            }

            is JsonArray -> {
                if (!isIndex(key)) return current
                val n = key.toInt()
                val child = current.getOrNull(n) ?: emptyChildFor(nextKey)
                val updated = applyAtPath(child, segments, index + 1, value, op)
                buildJsonArray {
                    current.forEachIndexed { i, v -> if (i != n) add(v) else add(updated) }
                    // Past the end: append rather than pad the gap with nulls, which would
                    // leave holes every reader downstream then has to survive.
                    if (n >= current.size) add(updated)
                }
            }

            else -> current
        }
    }

    /**
     * Writes [value] into [array] at [key].
     *
     * An add inserts and shifts the rest along, as the RFC says: treating it as a replace drops
     * whatever sat at that index, which is how a restocked shop can lose an item until the next
     * reconnect. A replace overwrites in place. Either way an index past the end appends, since
     * the alternative is a hole.
     */
    private fun writeIntoArray(
        array: JsonArray,
        key: String,
        value: JsonElement?,
        op: String?,
    ): JsonElement {
        if (value == null) return array
        val target = if (key == APPEND) array.size else key.toInt()
        val inserting = op == "add" || key == APPEND || target >= array.size
        return buildJsonArray {
            array.forEachIndexed { i, existing ->
                if (i == target) {
                    add(value)
                    if (inserting) add(existing)
                } else {
                    add(existing)
                }
            }
            if (target >= array.size) add(value)
        }
    }

    /** A missing child is an array when the next step indexes into it, an object otherwise. */
    private fun emptyChildFor(nextKey: String): JsonElement =
        if (addressesArray(nextKey)) JsonArray(emptyList()) else JsonObject(emptyMap())
}
