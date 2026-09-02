package com.mgafk.app.data.websocket

import java.security.SecureRandom
import java.util.UUID

object IdGenerator {
    private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private const val LOWER_ALPHA = "abcdefghijklmnopqrstuvwxyz"
    private val random = SecureRandom()

    fun generatePlayerId(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        val id = bytes.joinToString("") { byte ->
            BASE58_ALPHABET[(byte.toInt() and 0xFF) % BASE58_ALPHABET.length].toString()
        }
        return "p_$id"
    }

    fun generateRoomId(): String {
        val bytes = ByteArray(10)
        random.nextBytes(bytes)
        return bytes.joinToString("") { byte ->
            LOWER_ALPHA[(byte.toInt() and 0xFF) % LOWER_ALPHA.length].toString()
        }
    }

    /**
     * Identifies one client "document" (a tab, for the web client) to the
     * server. Stable for a whole session, regenerated for each new one.
     */
    fun generateDocumentId(): String = UUID.randomUUID().toString()

    fun normalizeCookie(cookie: String?): String {
        val trimmed = cookie?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.contains("mc_jwt")) trimmed else "mc_jwt=$trimmed"
    }
}
