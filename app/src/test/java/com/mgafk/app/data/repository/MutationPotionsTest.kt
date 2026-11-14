package com.mgafk.app.data.repository

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** BR-51: the mutation a potion grants, from MG API's catalogue or the fallback table. */
class MutationPotionsTest {

    @Test fun `grantedMutation is read from the catalogue entry`() {
        assertEquals(
            "Ambershine",
            MgApi.parseGrantedMutation(JsonObject(mapOf("grantedMutation" to JsonPrimitive("Ambershine")))),
        )
    }

    @Test fun `no, blank or null grantedMutation reads as null`() {
        assertNull(MgApi.parseGrantedMutation(JsonObject(emptyMap())))
        assertNull(MgApi.parseGrantedMutation(JsonObject(mapOf("grantedMutation" to JsonPrimitive("")))))
        assertNull(MgApi.parseGrantedMutation(JsonObject(mapOf("grantedMutation" to JsonNull))))
        assertNull(MgApi.parseGrantedMutation(null))
    }

    @Test fun `the fallback table applies when the catalogue is empty`() {
        assertEquals("Ambershine", MutationPotions.grantedMutation("AmberlitPotion") { null })
        assertNull(MutationPotions.grantedMutation("XPPotion") { null })
    }

    @Test fun `the catalogue wins over the fallback table`() {
        val entry = MgApi.GameEntry(id = "GoldPotion", name = "Gold Potion", sprite = null, grantedMutation = "Golden")
        assertEquals("Golden", MutationPotions.grantedMutation("GoldPotion") { entry })
    }
}
