package com.mgafk.app.data

import kotlinx.serialization.json.Json

/**
 * Shared Json configurations used across the data layer.
 * Avoids duplicating Json { ... } blocks in every repository/client.
 */
object AppJson {

    /** Default config: lenient parsing, unknown keys ignored. */
    val default: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Config for persistence: encodes default values so stored JSON is always complete. */
    val storage: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
