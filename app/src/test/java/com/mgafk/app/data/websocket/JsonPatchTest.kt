package com.mgafk.app.data.websocket

import com.mgafk.app.data.AppJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every screen in the app is a view of the state these patches maintain, so an operation this
 * applies wrongly does not fail loudly: it quietly shows the player something the game does not
 * agree with, until a reconnect rebuilds the state from scratch.
 */
class JsonPatchTest {

    private fun json(raw: String): JsonElement = AppJson.default.parseToJsonElement(raw)

    private fun apply(target: String, path: String, value: String?, op: String?): String =
        JsonPatch.applyPatch(json(target), path, value?.let(::json), op).toString()

    private fun shops(vararg items: String) =
        """{"shops":{"tool":{"items":[${items.joinToString(",")}]}}}"""

    private fun item(id: String) = """{"id":"$id"}"""

    // ── Adding to an array ──

    /**
     * RFC 6902 says an add at an index inserts and shifts the rest along. Treating it as a
     * replace drops whatever sat there, which is how a restocked shop lost an item that only
     * came back on reconnect.
     */
    @Test fun `add at an index inserts rather than overwrites`() {
        val result = apply(
            target = shops(item("WateringCan"), item("Shovel")),
            path = "/shops/tool/items/1",
            value = item("PlanterPot"),
            op = "add",
        )

        assertEquals(shops(item("WateringCan"), item("PlanterPot"), item("Shovel")), result)
    }

    @Test fun `add at the end of an array appends`() {
        val result = apply(
            target = shops(item("WateringCan")),
            path = "/shops/tool/items/1",
            value = item("Shovel"),
            op = "add",
        )

        assertEquals(shops(item("WateringCan"), item("Shovel")), result)
    }

    /** The RFC's append token, which the activity log grows by. */
    @Test fun `a dash appends to the end`() {
        val result = apply(
            target = """{"activityLogs":[{"timestamp":1}]}""",
            path = "/activityLogs/-",
            value = """{"timestamp":2}""",
            op = "add",
        )

        assertEquals("""{"activityLogs":[{"timestamp":1},{"timestamp":2}]}""", result)
    }

    @Test fun `a dash appends to an array that does not exist yet`() {
        val result = apply(
            target = """{"player":{}}""",
            path = "/player/activityLogs/-",
            value = """{"timestamp":1}""",
            op = "add",
        )

        assertEquals("""{"player":{"activityLogs":[{"timestamp":1}]}}""", result)
    }

    // ── Replacing ──

    @Test fun `replace at an index overwrites just that element`() {
        val result = apply(
            target = shops(item("WateringCan"), item("Shovel")),
            path = "/shops/tool/items/1",
            value = item("PlanterPot"),
            op = "replace",
        )

        assertEquals(shops(item("WateringCan"), item("PlanterPot")), result)
    }

    @Test fun `replace sets a field on an object`() {
        val result = apply(
            target = """{"shops":{"tool":{"secondsUntilRestock":90}}}""",
            path = "/shops/tool/secondsUntilRestock",
            value = "42",
            op = "replace",
        )

        assertEquals("""{"shops":{"tool":{"secondsUntilRestock":42}}}""", result)
    }

    @Test fun `replace of a whole array swaps it out`() {
        val result = apply(
            target = shops(item("WateringCan"), item("Shovel")),
            path = "/shops/tool/items",
            value = """[${item("PlanterPot")}]""",
            op = "replace",
        )

        assertEquals(shops(item("PlanterPot")), result)
    }

    // ── Removing ──

    @Test fun `remove takes an element out and closes the gap`() {
        val result = apply(
            target = shops(item("WateringCan"), item("PlanterPot"), item("Shovel")),
            path = "/shops/tool/items/1",
            value = null,
            op = "remove",
        )

        assertEquals(shops(item("WateringCan"), item("Shovel")), result)
    }

    @Test fun `remove drops a field from an object`() {
        val result = apply(
            target = """{"a":1,"b":2}""",
            path = "/b",
            value = null,
            op = "remove",
        )

        assertEquals("""{"a":1}""", result)
    }

    // ── Paths the server can send that must not corrupt the tree ──

    /**
     * An index past the end used to pad the array with JSON nulls, leaving holes that every
     * reader downstream then had to survive. Appending keeps the array readable.
     */
    @Test fun `an index past the end does not punch holes`() {
        val result = apply(
            target = shops(item("WateringCan")),
            path = "/shops/tool/items/4",
            value = item("Shovel"),
            op = "replace",
        )

        assertEquals(shops(item("WateringCan"), item("Shovel")), result)
    }

    /** A trailing slash yields an empty segment, which must not be read as index zero. */
    @Test fun `an empty segment is not an index`() {
        val result = apply(
            target = """{"a":{"b":1}}""",
            path = "/a/",
            value = "2",
            op = "replace",
        )

        assertEquals("""{"a":{"b":1,"":2}}""", result)
    }

    @Test fun `the leading data segment is skipped`() {
        val result = apply(
            target = """{"weather":"Rain"}""",
            path = "/data/weather",
            value = "\"Snow\"",
            op = "replace",
        )

        assertEquals("""{"weather":"Snow"}""", result)
    }

    @Test fun `an unknown path builds the objects it needs`() {
        val result = apply(
            target = """{}""",
            path = "/a/b/c",
            value = "1",
            op = "replace",
        )

        assertEquals("""{"a":{"b":{"c":1}}}""", result)
    }
}
