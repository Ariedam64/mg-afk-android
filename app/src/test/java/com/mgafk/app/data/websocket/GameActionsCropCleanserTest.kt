package com.mgafk.app.data.websocket

import com.mgafk.app.data.AppJson
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * BR-67: game 2198 sends `CropCleanser {toolId, tileObjectIdx, growSlotIdx}` for every cleanser
 * (CropCleanser, HydroCleanser, LunarCleanser).
 */
class GameActionsCropCleanserTest {

    private val json = AppJson.default
    private val sent = mutableListOf<String>()
    private val actions = GameActions({ sent += it })

    private fun lastCommand() = json.parseToJsonElement(sent.last()).jsonObject["command"]!!.jsonObject

    @Test fun `the cleanser message carries the chosen tool`() {
        actions.cropCleanser(tileObjectIdx = 3, growSlotIdx = 1, toolId = "HydroCleanser")

        val command = lastCommand()
        assertEquals("CropCleanser", command["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals("HydroCleanser", command["toolId"]?.jsonPrimitive?.contentOrNull)
        assertEquals(3, command["tileObjectIdx"]?.jsonPrimitive?.intOrNull)
        assertEquals(1, command["growSlotIdx"]?.jsonPrimitive?.intOrNull)
    }
}
