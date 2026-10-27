package com.mgafk.app.data.repository

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import java.util.Random

/**
 * The request bodies follow the backend's contract (docs/bug-reports.md in
 * aries-mod-backend): its field names, its limits, blank optional fields left out.
 */
class BugReportPayloadTest {

    private val token = "a".repeat(64)

    private val draft = BugReportDraft(
        category = BugCategory.VISUAL,
        title = "  Pet   hotkeys \n do nothing ",
        description = "  Pressing 1 does not swap my team anymore.  ",
        frequency = BugFrequency.OFTEN,
    )

    private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

    // ---- Submit body ----

    @Test
    fun `submit body carries the contract fields`() {
        val body = BugReportPayload.submitBody(
            draft = draft,
            appVersion = "2.4.33",
            followToken = token,
            attachmentsExpected = 2,
        )

        assertEquals("mg-afk-app", body.text("project"))
        assertEquals("MG AFK App", body.text("modName"))
        assertEquals("2.4.33", body.text("modVersion"))
        assertEquals("visual", body.text("category"))
        assertEquals("Pet hotkeys do nothing", body.text("title"))
        assertEquals("Pressing 1 does not swap my team anymore.", body.text("description"))
        assertEquals("often", body.text("frequency"))
        assertTrue(body.getValue("includeAccountState").jsonPrimitive.boolean)
        assertEquals(2, body.getValue("attachmentsExpected").jsonPrimitive.int)
        assertEquals(token, body.text("followToken"))
    }

    @Test
    fun `blank optional fields are left out`() {
        val body = BugReportPayload.submitBody(
            draft = draft.copy(steps = "   ", expected = "", actual = "\n", contact = " "),
            appVersion = "2.4.33",
            followToken = token,
            attachmentsExpected = 0,
            playerId = "  ",
            playerName = "",
            gameVersion = null,
            context = null,
        )

        listOf("steps", "expected", "actual", "contact", "playerId", "playerName", "gameVersion", "context")
            .forEach { key -> assertFalse("$key should be omitted", body.containsKey(key)) }
    }

    @Test
    fun `optional fields are trimmed and the player is identified`() {
        val context = buildJsonObject { put("extra", "x") }
        val body = BugReportPayload.submitBody(
            draft = draft.copy(
                steps = " 1. Open Pets ",
                expected = "Team 1 is equipped ",
                actual = " Nothing happens",
                contact = " romann ",
                includeAccountState = false,
            ),
            appVersion = "2.4.33",
            followToken = token,
            attachmentsExpected = 1,
            playerId = "123456789012345678",
            playerName = "Romann",
            gameVersion = "1727",
            context = context,
        )

        assertEquals("1. Open Pets", body.text("steps"))
        assertEquals("Team 1 is equipped", body.text("expected"))
        assertEquals("Nothing happens", body.text("actual"))
        assertEquals("romann", body.text("contact"))
        assertEquals("123456789012345678", body.text("playerId"))
        assertEquals("Romann", body.text("playerName"))
        assertEquals("1727", body.text("gameVersion"))
        assertFalse(body.getValue("includeAccountState").jsonPrimitive.boolean)
        assertEquals(context, body["context"])
    }

    @Test
    fun `player name is clipped and an oversized player id is dropped`() {
        val body = BugReportPayload.submitBody(
            draft, "2.4.33", token, 0,
            playerId = "9".repeat(65),
            playerName = "n".repeat(80),
        )

        assertFalse(body.containsKey("playerId"))
        assertEquals(BugReportPayload.PLAYER_NAME_MAX, body.text("playerName").length)
    }

    @Test
    fun `attachments expected stays between 0 and 3`() {
        assertEquals(3, BugReportPayload.submitBody(draft, "v", token, 7).getValue("attachmentsExpected").jsonPrimitive.int)
        assertEquals(0, BugReportPayload.submitBody(draft, "v", token, -1).getValue("attachmentsExpected").jsonPrimitive.int)
    }

    // ---- Validation ----

    @Test
    fun `a complete draft is valid`() {
        assertNull(BugReportPayload.validate(draft))
    }

    @Test
    fun `title must be 3 to 140 characters once whitespace is collapsed`() {
        assertEquals("title", BugReportPayload.validate(draft.copy(title = "  ab  "))?.field)
        assertNull(BugReportPayload.validate(draft.copy(title = "a  b")))
        assertNull(BugReportPayload.validate(draft.copy(title = "x".repeat(140))))
        assertEquals("title", BugReportPayload.validate(draft.copy(title = "x".repeat(141)))?.field)
    }

    @Test
    fun `description must be 10 to 4000 characters`() {
        assertEquals("description", BugReportPayload.validate(draft.copy(description = "  too short "))?.field)
        assertNull(BugReportPayload.validate(draft.copy(description = "d".repeat(4000))))
        assertEquals("description", BugReportPayload.validate(draft.copy(description = "d".repeat(4001)))?.field)
    }

    @Test
    fun `details and contact have their own limits`() {
        assertEquals("steps", BugReportPayload.validate(draft.copy(steps = "s".repeat(2001)))?.field)
        assertEquals("expected", BugReportPayload.validate(draft.copy(expected = "e".repeat(2001)))?.field)
        assertEquals("actual", BugReportPayload.validate(draft.copy(actual = "a".repeat(2001)))?.field)
        assertEquals("contact", BugReportPayload.validate(draft.copy(contact = "c".repeat(101)))?.field)
        assertNull(BugReportPayload.validate(draft.copy(steps = "s".repeat(2000), contact = "c".repeat(100))))
    }

    @Test
    fun `comments need 1 to 2000 characters`() {
        assertNotNull(BugReportPayload.validateComment("   "))
        assertNull(BugReportPayload.validateComment(" ok "))
        assertNull(BugReportPayload.validateComment("m".repeat(2000)))
        assertNotNull(BugReportPayload.validateComment("m".repeat(2001)))
    }

    // ---- Follow token ----

    @Test
    fun `follow token is 64 lowercase hex characters`() {
        val followToken = BugReportPayload.newFollowToken()

        assertEquals(64, followToken.length)
        assertTrue(followToken.matches(Regex("[0-9a-f]{64}")))
        assertTrue(BugReportPayload.isFollowToken(followToken))
    }

    @Test
    fun `follow tokens differ from one report to the next`() {
        assertNotEquals(BugReportPayload.newFollowToken(), BugReportPayload.newFollowToken())
    }

    @Test
    fun `follow token encodes each of its 32 bytes as two hex digits`() {
        val bytes = ByteArray(32).also { Random(42).nextBytes(it) }
        val expected = bytes.joinToString("") { Integer.toHexString(it.toInt() and 0xFF).padStart(2, '0') }

        assertEquals(expected, BugReportPayload.newFollowToken(Random(42)))
    }

    @Test
    fun `only 64 lowercase hex characters make a follow token`() {
        assertTrue(BugReportPayload.isFollowToken("0123456789abcdef".repeat(4)))
        assertFalse(BugReportPayload.isFollowToken("A".repeat(64)))
        assertFalse(BugReportPayload.isFollowToken("a".repeat(63)))
        assertFalse(BugReportPayload.isFollowToken("g".repeat(64)))
    }

    // ---- Other bodies ----

    @Test
    fun `attachment body is plain base64 of an upload`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0x10)
        val data = BugReportPayload.encodeBase64(bytes)
        val body = BugReportPayload.attachmentBody(token, "image/jpeg", data, 1280, 720)

        assertEquals("/9j/ABA=", data)
        assertEquals(token, body.text("followToken"))
        assertEquals("image/jpeg", body.text("mime"))
        assertEquals("upload", body.text("kind"))
        assertEquals(1280, body.getValue("width").jsonPrimitive.int)
        assertEquals(720, body.getValue("height").jsonPrimitive.int)
        assertTrue(Base64.getDecoder().decode(body.text("data")).contentEquals(bytes))
    }

    @Test
    fun `mine body sends id and token pairs, 50 at most`() {
        val stored = (1L..60L).map { StoredBugReport(id = it, followToken = token, ref = "BR-$it", title = "t") }
        val reports = BugReportPayload.mineBody(stored).getValue("reports").jsonArray

        assertEquals(50, reports.size)
        val first = reports[0].jsonObject
        assertEquals(setOf("id", "followToken"), first.keys)
        assertEquals(1L, first.getValue("id").jsonPrimitive.long)
        assertEquals(token, first.getValue("followToken").jsonPrimitive.content)
    }

    @Test
    fun `comment body trims the message`() {
        val body = BugReportPayload.commentBody(token, "  It also happens with team 2 \n")

        assertEquals(token, body.text("followToken"))
        assertEquals("It also happens with team 2", body.text("message"))
    }

    // ---- Text helpers ----

    @Test
    fun `clip never splits an emoji`() {
        val text = "ab😀"

        assertEquals("ab", BugReportPayload.clip(text, 3))
        assertEquals(text, BugReportPayload.clip(text, 4))
        assertEquals("a", BugReportPayload.clip("abc", 1))
    }

    @Test
    fun `free text loses a pasted session token`() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJlLXZhbHVl"
        val redacted = BugReportPayload.redactDraft(
            draft.copy(description = "my token is $jwt please help", steps = "cookie mc_jwt=abcdefghijkl"),
            mask = listOf("abcdefghijkl"),
        )

        assertEquals("my token is [redacted-jwt] please help", redacted.description)
        assertEquals("cookie mc_jwt=[redacted]", redacted.steps)
        assertEquals(draft.title, redacted.title)
    }
}
