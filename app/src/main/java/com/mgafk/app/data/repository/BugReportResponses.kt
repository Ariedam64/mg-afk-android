package com.mgafk.app.data.repository

import com.mgafk.app.data.AppJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** A report sent from this device: what /bug-reports/mine needs to find it again. */
@Serializable
data class StoredBugReport(
    val id: Long,
    val followToken: String,
    val ref: String = "",
    val title: String = "",
    val createdAtMs: Long = 0L,
)

/** Answer of POST /bug-reports. [created] is false on a 200: this follow token had already created it. */
data class BugReportCreated(
    val id: Long,
    val ref: String,
    val followToken: String,
    val status: String,
    val attachmentsReceived: Int,
    val created: Boolean,
)

data class BugReportComment(
    /** "reporter" (the player) or "admin". */
    val author: String,
    val message: String,
    val createdAt: String?,
)

/** One report as /bug-reports/mine returns it. Dates are ISO 8601 strings. */
data class MyBugReport(
    val id: Long,
    val ref: String,
    val title: String,
    val status: String,
    val statusLabel: String,
    val modVersion: String?,
    val fixedInVersion: String?,
    val publicReply: String?,
    val publicReplyAt: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val comments: List<BugReportComment>,
    val canComment: Boolean,
)

/** Which call failed, for the wording of its error. */
enum class BugReportCall { SUBMIT, ATTACHMENT, MINE, COMMENT }

/**
 * A failed call. [code] is the backend's `error` (invalid, duplicate, rate_limited,
 * not_found, closed...), or "network", "timeout", "unreadable", "http".
 * [message] is meant for the player.
 */
data class BugReportError(
    val code: String,
    val message: String,
    val httpStatus: Int = 0,
    val field: String? = null,
    val retryAfterSec: Long? = null,
    val ref: String? = null,
) {
    /** Whether sending the same request again later can succeed. */
    val retryable: Boolean
        get() = code == "network" || code == "timeout" || code == "rate_limited" || httpStatus >= 500
}

sealed class BugReportResult<out T> {
    data class Ok<out T>(val value: T) : BugReportResult<T>()
    data class Failed(val error: BugReportError) : BugReportResult<Nothing>()
}

/** Reading the backend's answers, and turning its errors into messages for the player. */
object BugReportResponses {

    fun parseCreated(httpStatus: Int, body: String?, sentToken: String): BugReportCreated? {
        val obj = parseObject(body) ?: return null
        val id = obj.longAt("id") ?: return null
        return BugReportCreated(
            id = id,
            ref = obj.stringAt("ref") ?: "BR-$id",
            // Rendered once by the backend: it is the one we sent, unless it generated its own.
            followToken = obj.stringAt("followToken")?.takeIf { BugReportPayload.isFollowToken(it) } ?: sentToken,
            status = obj.stringAt("status") ?: "new",
            attachmentsReceived = obj.intAt("attachmentsReceived") ?: 0,
            created = httpStatus == 201,
        )
    }

    fun parseMine(body: String?): List<MyBugReport>? {
        val obj = parseObject(body) ?: return null
        val reports = obj["reports"] as? JsonArray ?: return null
        return reports.mapNotNull { el ->
            val r = el as? JsonObject ?: return@mapNotNull null
            val id = r.longAt("id") ?: return@mapNotNull null
            val status = r.stringAt("status") ?: "new"
            val comments = (r["comments"] as? JsonArray)?.mapNotNull { c ->
                val comment = c as? JsonObject ?: return@mapNotNull null
                val message = comment.stringAt("message") ?: return@mapNotNull null
                BugReportComment(
                    author = comment.stringAt("author") ?: "reporter",
                    message = message,
                    createdAt = comment.stringAt("createdAt"),
                )
            } ?: emptyList()
            MyBugReport(
                id = id,
                ref = r.stringAt("ref") ?: "BR-$id",
                title = r.stringAt("title").orEmpty(),
                status = status,
                statusLabel = r.stringAt("statusLabel") ?: status,
                modVersion = r.stringAt("modVersion"),
                fixedInVersion = r.stringAt("fixedInVersion"),
                publicReply = r.stringAt("publicReply")?.takeIf { it.isNotBlank() },
                publicReplyAt = r.stringAt("publicReplyAt"),
                createdAt = r.stringAt("createdAt"),
                updatedAt = r.stringAt("updatedAt"),
                comments = comments,
                canComment = r.booleanAt("canComment") ?: false,
            )
        }
    }

    /** A non-2xx answer as an error. [retryAfterHeader] is the Retry-After header, if any. */
    fun error(call: BugReportCall, httpStatus: Int, body: String?, retryAfterHeader: String? = null): BugReportError {
        val obj = parseObject(body)
        val code = obj?.stringAt("error") ?: "http"
        val field = obj?.stringAt("field")
        val retryAfter = obj?.longAt("retryAfter") ?: retryAfterHeader?.trim()?.toLongOrNull()
        val ref = obj?.stringAt("ref")
        val message = when {
            httpStatus == 429 || code == "rate_limited" ->
                if (retryAfter != null && retryAfter > 0) {
                    "Too many requests for now. Try again in ${formatWait(retryAfter)}."
                } else {
                    "Too many requests for now. Try again later."
                }
            code == "duplicate" ->
                if (ref != null) {
                    "You already sent this report a few minutes ago ($ref)."
                } else {
                    "You already sent this report a few minutes ago."
                }
            code == "invalid" -> "The server refused ${fieldLabel(field)}. Check it and try again."
            httpStatus == 413 || code == "too_large" ->
                if (call == BugReportCall.ATTACHMENT) {
                    "This screenshot is too large for the server."
                } else {
                    "The report is too large. Shorten the text and try again."
                }
            code == "attachment_window_closed" ->
                "Screenshots can only be added in the 30 minutes after a report is sent."
            code == "too_many_attachments" ->
                "This report already has its ${BugReportPayload.MAX_ATTACHMENTS} screenshots."
            code == "closed" -> "This report is closed and no longer takes comments."
            httpStatus == 404 || code == "not_found" -> "This report was not found on the server."
            httpStatus >= 500 -> "The server had a problem (HTTP $httpStatus). Try again later."
            else -> "The request failed (HTTP $httpStatus)."
        }
        return BugReportError(
            code = code,
            message = message,
            httpStatus = httpStatus,
            field = field,
            retryAfterSec = retryAfter,
            ref = ref,
        )
    }

    fun networkError(): BugReportError =
        BugReportError("network", "Could not reach the server. Check your connection and try again.")

    /**
     * No answer in time. For a submission the server may still have created the report,
     * which is why a retry reuses the same follow token: it gets that report back.
     */
    fun timeoutError(call: BugReportCall): BugReportError =
        if (call == BugReportCall.SUBMIT) {
            BugReportError("timeout", "The server did not answer in time. Tap Send again: the report will not be sent twice.")
        } else {
            BugReportError("timeout", "The server did not answer in time. Try again.")
        }

    /** A 2xx whose body could not be read. */
    fun unreadable(httpStatus: Int): BugReportError =
        BugReportError("unreadable", "The server sent an answer the app could not read (HTTP $httpStatus).", httpStatus)

    /** "5 seconds", "2 minutes", "1 hour": always rounded up. */
    fun formatWait(seconds: Long): String = when {
        seconds < 60 -> if (seconds == 1L) "1 second" else "$seconds seconds"
        seconds < 3600 -> {
            val minutes = (seconds + 59) / 60
            if (minutes == 1L) "1 minute" else "$minutes minutes"
        }
        else -> {
            val hours = (seconds + 3599) / 3600
            if (hours == 1L) "1 hour" else "$hours hours"
        }
    }

    private fun fieldLabel(field: String?): String = when (field) {
        null -> "the request"
        "title" -> "the title"
        "description" -> "the description"
        "steps" -> "the steps"
        "expected" -> "the expected result"
        "actual" -> "the actual result"
        "contact" -> "the contact"
        "playerId" -> "the player id"
        "playerName" -> "the player name"
        "context" -> "the automatic diagnostics"
        "data", "mime", "width", "height" -> "the screenshot"
        "message" -> "the message"
        else -> "the field \"$field\""
    }

    private fun parseObject(body: String?): JsonObject? {
        if (body.isNullOrBlank()) return null
        return try {
            AppJson.default.parseToJsonElement(body) as? JsonObject
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.stringAt(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.longAt(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.intAt(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.booleanAt(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
}
