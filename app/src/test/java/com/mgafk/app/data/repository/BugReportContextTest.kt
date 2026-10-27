package com.mgafk.app.data.repository

import com.mgafk.app.data.model.WsLog
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The automatic `context` of a report: what it says about the device and the
 * session, the backend's log limits, and no session token ever.
 */
class BugReportContextTest {

    private val env = BugReportEnvironment(
        manufacturer = "Google",
        model = "Pixel 8",
        androidVersion = "15",
        sdkInt = 35,
        appVersion = "2.4.33",
        appVersionCode = 77,
        language = "fr-FR",
        uptimeSec = 3600L,
    )

    private fun crash(stamp: String, body: String): String =
        "==== $stamp | process=main | thread=main ====\n$body\n"

    @Test
    fun `environment says android and describes the device`() {
        val context = BugReportContext.build(env, BugReportSnapshot(), crashLog = "")
        val environment = context.getValue("environment").jsonObject

        assertEquals("android", environment.getValue("surface").jsonPrimitive.content)
        assertEquals("Google", environment.getValue("manufacturer").jsonPrimitive.content)
        assertEquals("Pixel 8", environment.getValue("model").jsonPrimitive.content)
        assertEquals("15", environment.getValue("androidVersion").jsonPrimitive.content)
        assertEquals(35, environment.getValue("sdkInt").jsonPrimitive.int)
        assertEquals("2.4.33", environment.getValue("appVersion").jsonPrimitive.content)
        assertEquals("fr-FR", environment.getValue("language").jsonPrimitive.content)
        assertEquals(3600L, environment.getValue("uptimeSec").jsonPrimitive.long)
        assertFalse(context.containsKey("connection"))
        assertFalse(context.containsKey("game"))
        assertEquals(0, context.getValue("logs").jsonArray.size)
    }

    @Test
    fun `game and connection state survive the redaction`() {
        val snapshot = BugReportSnapshot(
            gameVersion = "1727",
            connection = BugReportConnection(
                status = "CONNECTED",
                roomId = "ABCD",
                playersInRoom = 4,
                gameHost = "magicgarden.gg",
                connectedForSec = 120L,
                accountsTotal = 2,
                accountsConnected = 1,
            ),
        )

        val context = BugReportContext.build(env, snapshot, crashLog = "")

        val game = context.getValue("game").jsonObject
        assertEquals("ABCD", game.getValue("roomId").jsonPrimitive.content)
        assertEquals(4, game.getValue("playersInRoom").jsonPrimitive.int)
        assertEquals("1727", game.getValue("gameVersion").jsonPrimitive.content)
        assertEquals("magicgarden.gg", game.getValue("host").jsonPrimitive.content)
        val connection = context.getValue("connection").jsonObject
        assertEquals("CONNECTED", connection.getValue("status").jsonPrimitive.content)
        assertEquals(120L, connection.getValue("connectedForSec").jsonPrimitive.long)
        assertEquals(2, connection.getValue("accountsTotal").jsonPrimitive.int)
        assertEquals(1, connection.getValue("accountsConnected").jsonPrimitive.int)
        assertFalse(connection.containsKey("lastError"))
    }

    @Test
    fun `a session token never reaches the context`() {
        val token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJlLXZhbHVl"
        val snapshot = BugReportSnapshot(
            connection = BugReportConnection(status = "ERROR", lastError = "Invalid cookie mc_jwt=$token"),
            logs = listOf(BugReportContext.logEntry(1L, "error", "ws error: Cookie: mc_jwt=$token", "ws")),
            mask = listOf(token, "mc_jwt=$token"),
        )

        val json = BugReportContext.build(env, snapshot, crashLog = "boom $token").toString()

        assertFalse(json.contains(token))
        assertFalse(json.contains("c2lnbmF0dXJlLXZhbHVl"))
    }

    @Test
    fun `logs are capped at 200 entries of 500 characters, the most recent kept`() {
        val others = (1..300).map { BugReportContext.logEntry(it.toLong(), "info", "m$it " + "x".repeat(600), "ws") }

        val logs = BugReportContext.build(env, BugReportSnapshot(logs = others), crashLog = "")
            .getValue("logs").jsonArray

        assertEquals(200, logs.size)
        assertTrue(logs.all { it.jsonObject.getValue("msg").jsonPrimitive.content.length <= 500 })
        assertEquals(101L, logs.first().jsonObject.getValue("t").jsonPrimitive.long)
        assertEquals(300L, logs.last().jsonObject.getValue("t").jsonPrimitive.long)
    }

    @Test
    fun `log levels outside the contract become log`() {
        assertEquals("log", BugReportContext.logEntry(null, "debug", "x", "ws").level)
        assertEquals("warn", BugReportContext.logEntry(null, "warn", "x", "ws").level)
    }

    @Test
    fun `websocket logs are sent oldest first`() {
        val newestFirst = listOf(
            WsLog(timestamp = 3, level = "error", event = "ws error", detail = "boom"),
            WsLog(timestamp = 2, level = "info", event = "connected"),
            WsLog(timestamp = 1, level = "warn", event = "command rejected", detail = "type=x"),
        )

        val entries = BugReportContext.wsLogEntries(newestFirst)

        assertEquals(listOf(1L, 2L, 3L), entries.map { it.t })
        assertEquals("command rejected: type=x", entries[0].msg)
        assertEquals("connected", entries[1].msg)
        assertEquals("ws error: boom", entries[2].msg)
        assertEquals("error", entries[2].level)
        assertTrue(entries.all { it.source == "ws" })
    }

    @Test
    fun `crash log entries are errors, one per crash`() {
        val log = crash("2026-10-20 10:00:00", "java.lang.IllegalStateException: first\n\tat a.b(C.kt:1)") +
            crash("2026-10-21 11:00:00", "java.lang.NullPointerException: second\n\tat d.e(F.kt:2)")

        val entries = BugReportContext.crashLogEntries(log)

        assertEquals(2, entries.size)
        assertTrue(entries.all { it.level == "error" && it.source == "crash" })
        assertTrue(entries[0].msg.startsWith("==== 2026-10-20 10:00:00"))
        assertTrue(entries[0].msg.contains("IllegalStateException: first"))
        assertTrue(entries[1].msg.contains("NullPointerException: second"))
        assertNotNull(entries[1].t)
    }

    @Test
    fun `an empty crash log gives no entry`() {
        assertTrue(BugReportContext.crashLogEntries("").isEmpty())
        assertTrue(BugReportContext.crashLogEntries("  \n").isEmpty())
    }

    @Test
    fun `only the tail of a long crash log is sent`() {
        val old = crash("2026-01-01 00:00:00", "old crash\n" + "\tat old.frame(Old.kt:1)\n".repeat(600))
        val recent = crash("2026-10-21 11:00:00", "java.lang.RuntimeException: recent")

        val entries = BugReportContext.crashLogEntries(old + recent)

        assertTrue(entries.size <= BugReportContext.MAX_CRASH_ENTRIES)
        assertTrue(entries.all { it.msg.length <= BugReportContext.MAX_LOG_MESSAGE })
        assertTrue(entries.last().msg.contains("RuntimeException: recent"))
        assertFalse(entries.any { it.msg.contains("old crash") })
        // Cut mid-crash: that piece has no header to date it.
        assertNull(entries.first().t)
    }

    @Test
    fun `crash entries come first and stay when other logs overflow`() {
        val crashEntries = BugReportContext.crashLogEntries(
            crash("2026-10-21 11:00:00", "java.lang.RuntimeException: boom")
        )
        val others = (1..300).map { BugReportContext.logEntry(it.toLong(), "info", "line $it", "service") }

        val merged = BugReportContext.mergeLogs(crashEntries, others)

        assertEquals(BugReportContext.MAX_LOG_ENTRIES, merged.size)
        assertEquals("crash", merged.first().source)
        assertEquals("line 300", merged.last().msg)
    }
}
