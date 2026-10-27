package com.mgafk.app.data.repository

import com.mgafk.app.data.AppJson
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Answers and errors of the backend's bug report routes, shaped as docs/bug-reports.md shows them. */
class BugReportResponsesTest {

    private val token = "b".repeat(64)

    @Test
    fun `a 201 creates the report`() {
        val body = """{"id":42,"ref":"BR-42","followToken":"$token","status":"new","attachmentsReceived":0}"""

        val created = BugReportResponses.parseCreated(201, body, sentToken = token)

        assertNotNull(created)
        assertEquals(42L, created!!.id)
        assertEquals("BR-42", created.ref)
        assertEquals(token, created.followToken)
        assertEquals(0, created.attachmentsReceived)
        assertTrue(created.created)
    }

    @Test
    fun `a 200 returns the report this token already created, with its screenshots count`() {
        val body = """{"id":42,"ref":"BR-42","followToken":"$token","status":"triaged","attachmentsReceived":2}"""

        val again = BugReportResponses.parseCreated(200, body, sentToken = token)!!

        assertFalse(again.created)
        assertEquals(2, again.attachmentsReceived)
        assertEquals("triaged", again.status)
    }

    @Test
    fun `an unreadable answer gives null`() {
        assertNull(BugReportResponses.parseCreated(201, "<html>", token))
        assertNull(BugReportResponses.parseCreated(201, """{"ref":"BR-1"}""", token))
        assertNull(BugReportResponses.parseCreated(201, null, token))
    }

    @Test
    fun `my reports are read with their reply and comments`() {
        val body = """
            {"reports":[{"id":42,"ref":"BR-42","project":"mg-afk-app","title":"Hotkeys","status":"needs_info",
              "statusLabel":"Need more info","modVersion":"2.4.33","fixedInVersion":null,"publicReply":"Which team?",
              "publicReplyAt":"2026-10-24T10:00:00.000Z","createdAt":"2026-10-23T12:00:00.000Z",
              "updatedAt":"2026-10-24T10:00:00.000Z",
              "comments":[{"author":"reporter","message":"Since today","createdAt":"2026-10-23T12:05:00.000Z"},
                          {"author":"admin","message":"Which team?","createdAt":"2026-10-24T10:00:00.000Z"}],
              "canComment":true}]}
        """.trimIndent()

        val reports = BugReportResponses.parseMine(body)!!

        assertEquals(1, reports.size)
        val report = reports[0]
        assertEquals(42L, report.id)
        assertEquals("BR-42", report.ref)
        assertEquals("Hotkeys", report.title)
        assertEquals("needs_info", report.status)
        assertEquals("Need more info", report.statusLabel)
        assertNull(report.fixedInVersion)
        assertEquals("Which team?", report.publicReply)
        assertEquals(listOf("reporter", "admin"), report.comments.map { it.author })
        assertEquals("Since today", report.comments[0].message)
        assertTrue(report.canComment)
    }

    @Test
    fun `an empty mine answer is an empty list`() {
        assertEquals(0, BugReportResponses.parseMine("""{"reports":[]}""")!!.size)
    }

    @Test
    fun `rate limiting says how long to wait`() {
        val error = BugReportResponses.error(BugReportCall.SUBMIT, 429, """{"error":"rate_limited","retryAfter":120}""")

        assertEquals("rate_limited", error.code)
        assertEquals(120L, error.retryAfterSec)
        assertTrue(error.message.contains("2 minutes"))
        assertTrue(error.retryable)
    }

    @Test
    fun `the retry-after header is the fallback`() {
        val error = BugReportResponses.error(BugReportCall.COMMENT, 429, null, retryAfterHeader = "5")

        assertEquals(5L, error.retryAfterSec)
        assertTrue(error.message.contains("5 seconds"))
    }

    @Test
    fun `an invalid field is named`() {
        val error = BugReportResponses.error(BugReportCall.SUBMIT, 400, """{"error":"invalid","field":"title"}""")

        assertEquals("invalid", error.code)
        assertEquals("title", error.field)
        assertTrue(error.message.contains("the title"))
        assertFalse(error.retryable)
    }

    @Test
    fun `a duplicate points to the first report`() {
        val error = BugReportResponses.error(BugReportCall.SUBMIT, 409, """{"error":"duplicate","id":41,"ref":"BR-41"}""")

        assertEquals("duplicate", error.code)
        assertEquals("BR-41", error.ref)
        assertTrue(error.message.contains("BR-41"))
    }

    @Test
    fun `a closed report refuses comments`() {
        val error = BugReportResponses.error(BugReportCall.COMMENT, 409, """{"error":"closed","status":"wont_fix"}""")

        assertEquals("closed", error.code)
        assertFalse(error.retryable)
    }

    @Test
    fun `server errors and network failures can be retried, refusals cannot`() {
        assertTrue(BugReportResponses.error(BugReportCall.SUBMIT, 502, "Bad gateway").retryable)
        assertTrue(BugReportResponses.networkError().retryable)
        assertTrue(BugReportResponses.timeoutError(BugReportCall.SUBMIT).retryable)
        assertFalse(
            BugReportResponses.error(BugReportCall.ATTACHMENT, 409, """{"error":"attachment_window_closed"}""").retryable
        )
        assertFalse(BugReportResponses.error(BugReportCall.ATTACHMENT, 404, """{"error":"not_found"}""").retryable)
    }

    @Test
    fun `waits are rounded up to readable units`() {
        assertEquals("1 second", BugReportResponses.formatWait(1))
        assertEquals("45 seconds", BugReportResponses.formatWait(45))
        assertEquals("1 minute", BugReportResponses.formatWait(60))
        assertEquals("2 minutes", BugReportResponses.formatWait(61))
        assertEquals("1 hour", BugReportResponses.formatWait(3600))
        assertEquals("2 hours", BugReportResponses.formatWait(3601))
    }

    @Test
    fun `stored reports survive a round trip through json`() {
        val list = listOf(StoredBugReport(id = 42, followToken = token, ref = "BR-42", title = "Hotkeys", createdAtMs = 1L))
        val serializer = ListSerializer(StoredBugReport.serializer())

        val raw = AppJson.storage.encodeToString(serializer, list)

        assertEquals(list, AppJson.storage.decodeFromString(serializer, raw))
    }
}
