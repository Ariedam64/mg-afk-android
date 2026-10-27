package com.mgafk.app.ui

import com.mgafk.app.data.AppJson
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where a pet stands, read from its petSlotInfos entry.
 *
 * Both pet potions teleport onto the pet before being drunk, and give up in silence when its
 * position can't be read. The live game (1753) describes a pet's motion as
 * `{path: [{x,y}, ...], stepDurationMs, startedAtMs}`: no `at`, no `position`. So the app never
 * found the pet, and no potion was ever used.
 */
class PetSlotPositionTest {

    private fun parse(raw: String) = AppJson.default.parseToJsonElement(raw).jsonObject

    /** A pet whose walk is over stands on the last point of its path. */
    @Test fun `a finished walk puts the pet on the last point of its path`() {
        val info = parse(
            """{"motion":{"path":[{"x":3,"y":4},{"x":4,"y":4}],"stepDurationMs":500,"startedAtMs":0}}"""
        )

        assertEquals(4.0 to 4.0, petTileFromSlotInfo(info))
    }

    /** A pet at rest has a path of one point, as the game builds it: `{path:[e],stepDurationMs:0}`. */
    @Test fun `a resting pet stands on its single path point`() {
        val info = parse(
            """{"motion":{"path":[{"x":7,"y":2}],"stepDurationMs":0,"startedAtMs":1700000000000}}"""
        )

        assertEquals(7.0 to 2.0, petTileFromSlotInfo(info))
    }
}
