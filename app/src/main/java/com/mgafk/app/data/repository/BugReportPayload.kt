package com.mgafk.app.data.repository

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.security.SecureRandom
import java.util.Base64
import java.util.Random

/**
 * Request bodies for the backend's bug report routes (aries-mod-backend,
 * docs/bug-reports.md, "Routes publiques"), and the client-side checks that
 * mirror its limits, so a report is refused here with a readable message
 * rather than by a 400 once it is on the wire.
 *
 * Plain Kotlin on purpose (no Android class): the JVM unit tests cover it.
 */

/** Report categories, spelled the way the backend expects them. */
enum class BugCategory(val wire: String, val label: String) {
    BUG("bug", "Bug"),
    CRASH("crash", "Crash"),
    VISUAL("visual", "Visual"),
    PERFORMANCE("performance", "Performance"),
    FEATURE("feature", "Feature request"),
    QUESTION("question", "Question"),
    OTHER("other", "Other"),
}

/** How often the problem happens. */
enum class BugFrequency(val wire: String, val label: String) {
    ALWAYS("always", "Always"),
    OFTEN("often", "Often"),
    SOMETIMES("sometimes", "Sometimes"),
    ONCE("once", "Once"),
    UNKNOWN("unknown", "Not sure"),
}

/** What the player typed in the form. Blank optional fields are left out of the request. */
data class BugReportDraft(
    val category: BugCategory = BugCategory.BUG,
    val title: String = "",
    val description: String = "",
    val steps: String = "",
    val expected: String = "",
    val actual: String = "",
    val frequency: BugFrequency = BugFrequency.UNKNOWN,
    val contact: String = "",
    /** Lets the backend freeze a copy of the player's synced state with the report. */
    val includeAccountState: Boolean = true,
)

/** A field the backend would refuse, named the way the backend names it, with a message for the player. */
data class BugReportFieldError(val field: String, val message: String)

object BugReportPayload {
    const val PROJECT = "mg-afk-app"
    const val MOD_NAME = "MG AFK App"
    /** A screenshot the player picked from their gallery (the other kinds are the web mods' captures). */
    const val ATTACHMENT_KIND_UPLOAD = "upload"

    const val TITLE_MIN = 3
    const val TITLE_MAX = 140
    const val DESCRIPTION_MIN = 10
    const val DESCRIPTION_MAX = 4000
    /** Steps, expected result and actual result, each. */
    const val DETAIL_MAX = 2000
    const val CONTACT_MAX = 100
    const val PLAYER_ID_MAX = 64
    const val PLAYER_NAME_MAX = 64
    const val COMMENT_MAX = 2000
    const val MAX_ATTACHMENTS = 3
    /** Hard limit per screenshot, in decoded bytes. The encoder aims well below it. */
    const val ATTACHMENT_MAX_BYTES = 750_000
    /** Report/token pairs /bug-reports/mine accepts in one call. */
    const val MINE_MAX = 50

    private const val HEX = "0123456789abcdef"
    private val FOLLOW_TOKEN_RE = Regex("^[0-9a-f]{64}$")
    private val WHITESPACE_RUN = Regex("\\s+")
    private val secureRandom = SecureRandom()

    /** The title the way the backend stores it: runs of whitespace collapsed, trimmed. */
    fun normalizeTitle(raw: String): String = raw.replace(WHITESPACE_RUN, " ").trim()

    /**
     * The first field the backend would refuse, or null when the draft can be sent.
     * Lengths are counted like the backend counts them (UTF-16 units, after trimming).
     */
    fun validate(draft: BugReportDraft): BugReportFieldError? {
        val title = normalizeTitle(draft.title)
        if (title.length < TITLE_MIN) {
            return BugReportFieldError("title", "The title needs at least $TITLE_MIN characters.")
        }
        if (title.length > TITLE_MAX) {
            return BugReportFieldError("title", "The title is limited to $TITLE_MAX characters.")
        }
        val description = draft.description.trim()
        if (description.length < DESCRIPTION_MIN) {
            return BugReportFieldError("description", "Describe the problem in at least $DESCRIPTION_MIN characters.")
        }
        if (description.length > DESCRIPTION_MAX) {
            return BugReportFieldError("description", "The description is limited to $DESCRIPTION_MAX characters.")
        }
        if (draft.steps.trim().length > DETAIL_MAX) {
            return BugReportFieldError("steps", "The steps are limited to $DETAIL_MAX characters.")
        }
        if (draft.expected.trim().length > DETAIL_MAX) {
            return BugReportFieldError("expected", "The expected result is limited to $DETAIL_MAX characters.")
        }
        if (draft.actual.trim().length > DETAIL_MAX) {
            return BugReportFieldError("actual", "The actual result is limited to $DETAIL_MAX characters.")
        }
        if (draft.contact.trim().length > CONTACT_MAX) {
            return BugReportFieldError("contact", "The contact is limited to $CONTACT_MAX characters.")
        }
        return null
    }

    /** Null when the comment can be sent, else why it cannot. */
    fun validateComment(message: String): String? {
        val trimmed = message.trim()
        return when {
            trimmed.isEmpty() -> "Write a message first."
            trimmed.length > COMMENT_MAX -> "Comments are limited to $COMMENT_MAX characters."
            else -> null
        }
    }

    /**
     * A new follow token: 32 random bytes as 64 lowercase hex characters, the exact
     * format the backend generates itself. It is also the idempotency key of the
     * submission, so it is created once per report and reused on every retry.
     */
    fun newFollowToken(random: Random = secureRandom): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val out = StringBuilder(64)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return out.toString()
    }

    fun isFollowToken(value: String): Boolean = FOLLOW_TOKEN_RE.matches(value)

    /**
     * Cuts [value] to [max] UTF-16 units without splitting a surrogate pair (an emoji
     * straddling the limit goes as a whole).
     */
    fun clip(value: String, max: Int): String {
        if (max <= 0) return ""
        if (value.length <= max) return value
        val cut = if (value[max - 1].isHighSurrogate()) max - 1 else max
        return value.substring(0, cut)
    }

    /**
     * The draft with the long free-text fields passed through [BugReportRedaction]: a
     * player who pastes a log line holding a token, or their own session token, does
     * not send it. Title and contact are left as typed.
     */
    fun redactDraft(draft: BugReportDraft, mask: Collection<String> = emptyList()): BugReportDraft =
        draft.copy(
            description = clip(BugReportRedaction.redactString(draft.description, mask), DESCRIPTION_MAX),
            steps = clip(BugReportRedaction.redactString(draft.steps, mask), DETAIL_MAX),
            expected = clip(BugReportRedaction.redactString(draft.expected, mask), DETAIL_MAX),
            actual = clip(BugReportRedaction.redactString(draft.actual, mask), DETAIL_MAX),
        )

    /** Body of POST /bug-reports. Optional values that are blank are omitted. */
    fun submitBody(
        draft: BugReportDraft,
        appVersion: String,
        followToken: String,
        attachmentsExpected: Int,
        playerId: String? = null,
        playerName: String? = null,
        gameVersion: String? = null,
        context: JsonObject? = null,
    ): JsonObject = buildJsonObject {
        put("project", PROJECT)
        put("modName", MOD_NAME)
        put("modVersion", appVersion)
        optional(gameVersion)?.let { put("gameVersion", it) }
        // A Discord id is ~18 digits; anything past the backend's limit is not one.
        optional(playerId)?.takeIf { it.length <= PLAYER_ID_MAX }?.let { put("playerId", it) }
        optional(playerName)?.let { put("playerName", clip(it, PLAYER_NAME_MAX)) }
        optional(draft.contact)?.let { put("contact", it) }
        put("category", draft.category.wire)
        put("title", normalizeTitle(draft.title))
        put("description", draft.description.trim())
        optional(draft.steps)?.let { put("steps", it) }
        optional(draft.expected)?.let { put("expected", it) }
        optional(draft.actual)?.let { put("actual", it) }
        put("frequency", draft.frequency.wire)
        put("includeAccountState", draft.includeAccountState)
        put("attachmentsExpected", attachmentsExpected.coerceIn(0, MAX_ATTACHMENTS))
        put("followToken", followToken)
        if (context != null) put("context", context)
    }

    /** Body of POST /bug-reports/:id/attachments. [data] is plain base64, without a `data:` prefix. */
    fun attachmentBody(
        followToken: String,
        mime: String,
        data: String,
        width: Int,
        height: Int,
        kind: String = ATTACHMENT_KIND_UPLOAD,
    ): JsonObject = buildJsonObject {
        put("followToken", followToken)
        put("mime", mime)
        put("data", data)
        if (width > 0) put("width", width)
        if (height > 0) put("height", height)
        put("kind", kind)
    }

    /** Standard base64 with padding and no line breaks, which is what the backend decodes. */
    fun encodeBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    /** Body of POST /bug-reports/mine, at most [MINE_MAX] pairs (the most recent first). */
    fun mineBody(reports: List<StoredBugReport>): JsonObject = buildJsonObject {
        putJsonArray("reports") {
            reports.take(MINE_MAX).forEach { report ->
                addJsonObject {
                    put("id", report.id)
                    put("followToken", report.followToken)
                }
            }
        }
    }

    /** Body of POST /bug-reports/:id/comments. */
    fun commentBody(followToken: String, message: String): JsonObject = buildJsonObject {
        put("followToken", followToken)
        put("message", message.trim())
    }

    private fun optional(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
}
