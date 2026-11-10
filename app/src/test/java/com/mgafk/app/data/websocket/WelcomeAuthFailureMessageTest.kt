package com.mgafk.app.data.websocket

import com.mgafk.app.data.AppJson
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BR-37: when the game drops the Discord code (OAuth state mismatch) it signs the
 * player in as a guest, and the server accepts the socket. The Welcome then holds
 * our player without discordUserId or databaseUserId, and the app ended the
 * session with 4800 "Invalid mc_jwt cookie.", which sends players looking at the
 * wrong thing: the cookie was valid, it just belonged to a guest account.
 */
class WelcomeAuthFailureMessageTest {

    private val json = AppJson.default

    private fun player(raw: String) = json.parseToJsonElement(raw).jsonObject

    @Test fun `a guest player gets a message saying it is a guest session and to reconnect Discord`() {
        val guest = player("""{"id":"p1","name":"Guest1234","discordUserId":null}""")

        val message = welcomeAuthFailureMessage(guest)

        assertNotNull(message)
        assertNotEquals("Invalid mc_jwt cookie.", message)
        assertTrue(
            "message should say the session is a guest account: $message",
            message!!.contains("guest", ignoreCase = true),
        )
        assertTrue(
            "message should tell the player to reconnect Discord: $message",
            message.contains("Discord", ignoreCase = true),
        )
    }

    @Test fun `a player with a discordUserId is accepted`() {
        val me = player("""{"id":"p1","discordUserId":"123456789"}""")

        assertNull(welcomeAuthFailureMessage(me))
        assertEquals("discordUserId", welcomeIdentityKey(me))
    }

    @Test fun `a player with only the older databaseUserId is accepted`() {
        val me = player("""{"id":"p1","databaseUserId":"42"}""")

        assertNull(welcomeAuthFailureMessage(me))
        assertEquals("databaseUserId", welcomeIdentityKey(me))
    }

    @Test fun `a Welcome without our player does not fail auth`() {
        assertNull(welcomeAuthFailureMessage(null))
    }
}
