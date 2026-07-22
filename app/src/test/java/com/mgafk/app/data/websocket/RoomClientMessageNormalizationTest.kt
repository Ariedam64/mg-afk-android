package com.mgafk.app.data.websocket

import com.mgafk.app.data.AppJson
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomClientMessageNormalizationTest {

    private val json = AppJson.default

    @Test fun `PartialState passes through unchanged`() {
        val raw = """{"type":"PartialState","patches":[{"op":"replace","path":"/data/x","value":1}]}"""
        val msg = json.parseToJsonElement(raw).jsonObject

        val result = normalizeIncomingMessage(msg)

        assertEquals(msg, result)
    }

    @Test fun `RoomFrame is rewritten into the PartialState shape`() {
        val raw = """
            {"type":"RoomFrame","state":{"patches":[{"op":"replace","path":"/data/x","value":1}]}}
        """.trimIndent()
        val msg = json.parseToJsonElement(raw).jsonObject

        val result = normalizeIncomingMessage(msg)

        assertEquals("PartialState", result["type"]?.jsonPrimitive?.contentOrNull)
        val patches = result["patches"]?.jsonArray
        assertEquals(1, patches?.size)
        assertEquals("/data/x", patches?.get(0)?.jsonObject?.get("path")?.jsonPrimitive?.contentOrNull)
        assertNull(result["state"])
    }

    @Test fun `RoomFrame without patches passes through unchanged`() {
        val raw = """{"type":"RoomFrame","state":{}}"""
        val msg = json.parseToJsonElement(raw).jsonObject

        val result = normalizeIncomingMessage(msg)

        assertEquals(msg, result)
    }

    @Test fun `unrelated message types pass through unchanged`() {
        val raw = """{"type":"Welcome","fullState":{}}"""
        val msg = json.parseToJsonElement(raw).jsonObject

        val result = normalizeIncomingMessage(msg)

        assertEquals(msg, result)
    }
}
