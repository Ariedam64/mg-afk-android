package com.mgafk.app.data.repository

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Same rules as the backend's lib/bugReports/redact.ts, which redacts again on its
 * side: secret-looking keys anywhere, JWTs, bearer tokens and URL secrets in strings.
 */
class BugReportRedactionTest {

    private val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJlLXZhbHVl"

    @Test
    fun `secret looking keys are redacted at any depth`() {
        val input = buildJsonObject {
            put("api_key", "k1")
            put("apiKey", "k2")
            put("token", "t")
            put("mc_jwt", "j")
            put("Authorization", "Bearer abc")
            put("password", "p")
            put("cookie", "c")
            put("sessionToken", "s")
            put("auth", "a")
            put("author", "Romann")
            putJsonObject("nested") {
                putJsonArray("list") {
                    addJsonObject {
                        put("refreshToken", "r")
                        put("keep", "ok")
                    }
                }
            }
        }

        val out = BugReportRedaction.redact(input).jsonObject

        listOf("api_key", "apiKey", "token", "mc_jwt", "Authorization", "password", "cookie", "sessionToken", "auth")
            .forEach { key -> assertEquals(key, "[redacted]", out.getValue(key).jsonPrimitive.content) }
        assertEquals("Romann", out.getValue("author").jsonPrimitive.content)
        val item = out.getValue("nested").jsonObject.getValue("list").jsonArray[0].jsonObject
        assertEquals("[redacted]", item.getValue("refreshToken").jsonPrimitive.content)
        assertEquals("ok", item.getValue("keep").jsonPrimitive.content)
    }

    @Test
    fun `author is not mistaken for auth`() {
        assertTrue(BugReportRedaction.isSecretKey("oauth"))
        assertTrue(BugReportRedaction.isSecretKey("x-auth"))
        assertFalse(BugReportRedaction.isSecretKey("author"))
        assertFalse(BugReportRedaction.isSecretKey("connection"))
    }

    @Test
    fun `jwts are masked inside strings`() {
        assertEquals("cookie [redacted-jwt] end", BugReportRedaction.redactString("cookie $jwt end"))
    }

    @Test
    fun `bearer tokens are masked inside strings`() {
        assertEquals(
            "Authorization: Bearer [redacted]",
            BugReportRedaction.redactString("Authorization: Bearer abcdef123456"),
        )
    }

    @Test
    fun `secret url parameters are masked, the others kept`() {
        assertEquals(
            "wss://host/ws?room=AB&token=[redacted]&v=2",
            BugReportRedaction.redactString("wss://host/ws?room=AB&token=s3cr3t-value&v=2"),
        )
    }

    @Test
    fun `known session tokens are masked wherever they appear`() {
        val sessionToken = "plain-session-token-123"

        assertEquals(
            "ws error: handshake with [redacted] failed",
            BugReportRedaction.redactString("ws error: handshake with $sessionToken failed", listOf(sessionToken)),
        )
    }

    @Test
    fun `short mask values are ignored`() {
        assertEquals("room ABC", BugReportRedaction.redactString("room ABC", listOf("ABC")))
    }

    @Test
    fun `strings nested in arrays are redacted too`() {
        val input = buildJsonObject {
            putJsonArray("logs") {
                addJsonObject { put("msg", "header $jwt") }
            }
        }

        val msg = BugReportRedaction.redact(input).jsonObject
            .getValue("logs").jsonArray[0].jsonObject
            .getValue("msg").jsonPrimitive.content
        assertEquals("header [redacted-jwt]", msg)
    }

    @Test
    fun `numbers, booleans and nulls are left alone`() {
        val input = buildJsonObject {
            put("count", 3)
            put("ok", true)
            put("none", JsonNull)
        }

        assertEquals(input, BugReportRedaction.redact(input))
    }

    @Test
    fun `nesting deeper than the backend keeps is truncated`() {
        var element: JsonElement = JsonPrimitive("leaf")
        repeat(20) { element = JsonArray(listOf(element)) }

        val out = BugReportRedaction.redact(element).toString()

        assertTrue(out.contains("[truncated]"))
        assertFalse(out.contains("leaf"))
    }
}
