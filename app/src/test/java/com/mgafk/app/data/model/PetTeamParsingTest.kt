package com.mgafk.app.data.model

import com.mgafk.app.data.AppJson
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pet teams come from the game now, so parsing has to match the game's own schema:
 * `{id, name, members: [{petId, petSpecies, name}], emblem}` with 1..3 members and an emblem
 * discriminated on `type`.
 */
class PetTeamParsingTest {

    private fun parseArray(raw: String) =
        AppJson.default.parseToJsonElement(raw).jsonArray.mapNotNull { PetTeam.fromJson(it.jsonObject) }

    private fun parseOne(raw: String) =
        PetTeam.fromJson(AppJson.default.parseToJsonElement(raw).jsonObject)

    /** A verbatim slice of a real payload, so the shape is the server's and not our idea of it. */
    private val realPayload: String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("pet_teams_sample.json")) {
            "pet_teams_sample.json missing from test resources"
        }.bufferedReader().readText()

    @Test fun `parses the teams of a real payload`() {
        val teams = parseArray(realPayload)

        assertEquals(2, teams.size)
        val first = teams.first()
        assertEquals("eb99363f-c212-4f69-8a24-c0551a91e6fd", first.id)
        assertEquals("PetRfnd", first.name)
        assertEquals(3, first.members.size)
        assertEquals("26568a1d-d0a5-47c0-bfe2-751078caa8a0", first.members[0].petId)
        assertEquals("Peacock", first.members[0].petSpecies)
        // The game sends null when the pet was never renamed.
        assertNull(first.members[0].name)
        assertEquals(PetTeamEmblem.Letter(16), first.emblem)
    }

    @Test fun `keeps a name made of emoji intact`() {
        val emojiTeam = parseArray(realPayload).last()

        assertTrue("expected an emoji name, got '${emojiTeam.name}'", emojiTeam.name.isNotBlank())
        assertTrue(emojiTeam.name.any { it.code > 0xFF })
    }

    // The captured payload only ever uses number emblems, so the other three are spelled out
    // here from the game's schema.

    @Test fun `parses a pet emblem`() {
        val team = parseOne(
            """{"id":"t","name":"n","members":[{"petId":"p","petSpecies":"Peacock","name":null}],
               "emblem":{"type":"pet","petSpecies":"Peacock"}}"""
        )
        assertEquals(PetTeamEmblem.Pet("Peacock"), team?.emblem)
    }

    @Test fun `parses an icon emblem`() {
        val team = parseOne(
            """{"id":"t","name":"n","members":[{"petId":"p","petSpecies":"Bee","name":null}],
               "emblem":{"type":"icon","icon":"rainbow"}}"""
        )
        assertEquals(PetTeamEmblem.Icon("rainbow"), team?.emblem)
    }

    @Test fun `parses a cosmetic emblem`() {
        val team = parseOne(
            """{"id":"t","name":"n","members":[{"petId":"p","petSpecies":"Bee","name":null}],
               "emblem":{"type":"cosmetic","cosmetic":"TopHat"}}"""
        )
        assertEquals(PetTeamEmblem.Cosmetic("TopHat"), team?.emblem)
    }

    /** A later build adding a fifth emblem type must not take the whole team list down with it. */
    @Test fun `an unknown emblem type degrades instead of failing`() {
        val team = parseOne(
            """{"id":"t","name":"n","members":[{"petId":"p","petSpecies":"Bee","name":null}],
               "emblem":{"type":"hologram","hologram":"whatever"}}"""
        )
        assertEquals(PetTeamEmblem.Unknown, team?.emblem)
    }

    @Test fun `a team with a named member keeps that name`() {
        val team = parseOne(
            """{"id":"t","name":"n","members":[{"petId":"p","petSpecies":"Bee","name":"Buzz"}],
               "emblem":{"type":"number","number":3}}"""
        )
        assertEquals("Buzz", team?.members?.single()?.name)
    }

    @Test fun `a team without an id or without members is dropped`() {
        assertNull(parseOne("""{"name":"n","members":[],"emblem":{"type":"number","number":1}}"""))
        assertNull(
            parseOne("""{"id":"t","name":"n","members":[],"emblem":{"type":"number","number":1}}""")
        )
    }

    @Test fun `the letter emblem reads as A to Z`() {
        assertEquals("A", PetTeamEmblem.Letter(1).label)
        assertEquals("P", PetTeamEmblem.Letter(16).label)
        assertEquals("Z", PetTeamEmblem.Letter(26).label)
    }
}
