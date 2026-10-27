package com.mgafk.app.data.websocket.state

import com.mgafk.app.data.AppJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A potion or a ride needs the player on the pet's tile, read from the pet's motion. Since
 * build 1650 that motion is a path walked one step at a time, with no `at` any more.
 */
class PetTileTest {

    private fun obj(raw: String): JsonObject = AppJson.default.parseToJsonElement(raw).jsonObject

    private val walk = obj(
        """{"path":[{"x":1,"y":1},{"x":2,"y":1},{"x":3,"y":1}],"startedAtMs":1000,"stepDurationMs":500}"""
    )

    @Test fun `follows the path one step every stepDurationMs`() {
        assertEquals(1.0 to 1.0, PetTile.motionTile(walk, 1000))
        assertEquals(1.0 to 1.0, PetTile.motionTile(walk, 1499))
        assertEquals(2.0 to 1.0, PetTile.motionTile(walk, 1500))
    }

    @Test fun `rests on the last tile once the walk is over`() {
        assertEquals(3.0 to 1.0, PetTile.motionTile(walk, 60_000))
        assertEquals(
            7.0 to 4.0,
            PetTile.motionTile(obj("""{"path":[{"x":7,"y":4}],"startedAtMs":0,"stepDurationMs":0}"""), 5),
        )
    }

    @Test fun `a walk that has not started yet stands on its first tile`() {
        assertEquals(1.0 to 1.0, PetTile.motionTile(walk, 0))
    }

    @Test fun `still reads the motion of earlier builds`() {
        assertEquals(5.0 to 6.0, PetTile.motionTile(obj("""{"kind":"idle","at":{"x":5,"y":6}}"""), 0))
        assertEquals(8.0 to 9.0, PetTile.of(obj("""{"position":{"x":8,"y":9}}"""), 0))
    }

    @Test fun `the motion wins over an older position`() {
        assertEquals(
            3.0 to 1.0,
            PetTile.of(obj("""{"motion":{"path":[{"x":3,"y":1}],"startedAtMs":0,"stepDurationMs":500},"position":{"x":8,"y":9}}"""), 10_000),
        )
    }

    @Test fun `knows no tile without a motion`() {
        assertNull(PetTile.motionTile(null, 0))
        assertNull(PetTile.motionTile(obj("""{"path":[]}"""), 0))
        assertNull(PetTile.of(null, 0))
        assertNull(PetTile.of(obj("""{"slot":2}"""), 0))
    }
}
