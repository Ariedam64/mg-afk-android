package com.mgafk.app.data.repository

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Client-side redaction of what a bug report carries, the same two rules the
 * backend applies again on its side (aries-mod-backend, lib/bugReports/redact.ts):
 *
 *  1. an object key whose name looks like a secret has its value replaced by
 *     "[redacted]", at any depth;
 *  2. inside strings, JWTs, `Bearer xxx` and `?token=`-style URL parameters are
 *     masked.
 *
 * On top of that, the [mask] values (the sessions' own mc_jwt tokens) are masked
 * wherever they appear, so a token that leaked into a log line under some other
 * shape still never leaves the device.
 */
object BugReportRedaction {
    const val REDACTED = "[redacted]"
    private const val REDACTED_JWT = "[redacted-jwt]"
    private const val TRUNCATED = "[truncated]"
    private const val MAX_DEPTH = 12
    /** Shorter values would match ordinary text too easily to be masked blindly. */
    private const val MIN_MASK_LENGTH = 8

    /** Pieces of key names that designate a secret (name lowercased, without _ - or spaces). */
    private val SECRET_KEY_PARTS = listOf(
        "apikey",
        "token",
        "password",
        "passwd",
        "secret",
        "cookie",
        "session",
        "jwt",
        "bearer",
        "authorization",
        "credential",
        "privatekey",
    )
    private val KEY_SEPARATORS = Regex("[_\\-\\s]")
    /** "auth" alone, authToken, xauth, oauth... but not author. */
    private val AUTH_RE = Regex("auth(?!or)")

    private val JWT_RE = Regex("""\beyJ[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}""")
    private val BEARER_RE = Regex("""\bBearer\s+[A-Za-z0-9._~+/=-]{6,}""", RegexOption.IGNORE_CASE)
    private val URL_SECRET_PARAM_RE = Regex(
        """([?&#](?:api[_-]?key|apikey|token|access_token|auth|key|secret|password)=)[^&#\s"']+""",
        RegexOption.IGNORE_CASE,
    )

    fun isSecretKey(key: String): Boolean {
        val k = key.lowercase().replace(KEY_SEPARATORS, "")
        if (SECRET_KEY_PARTS.any { k.contains(it) }) return true
        return AUTH_RE.containsMatchIn(k)
    }

    /** [value] with the [mask] values, JWTs, bearer tokens and secret URL parameters masked. */
    fun redactString(value: String, mask: Collection<String> = emptyList()): String {
        var out = value
        for (secret in usableMask(mask)) {
            out = out.replace(secret, REDACTED)
        }
        return out
            .replace(JWT_RE, REDACTED_JWT)
            .replace(BEARER_RE, "Bearer $REDACTED")
            .replace(URL_SECRET_PARAM_RE, "\$1$REDACTED")
    }

    /** A redacted copy of any JSON value; depth is bounded like on the backend. */
    fun redact(element: JsonElement, mask: Collection<String> = emptyList()): JsonElement =
        redactAt(element, usableMask(mask), 0)

    /** [redact] for the usual case of a whole `context` object. */
    fun redactObject(obj: JsonObject, mask: Collection<String> = emptyList()): JsonObject =
        (redact(obj, mask) as? JsonObject) ?: JsonObject(emptyMap())

    private fun redactAt(element: JsonElement, mask: List<String>, depth: Int): JsonElement {
        if (element is JsonObject) {
            if (depth >= MAX_DEPTH) return JsonPrimitive(TRUNCATED)
            return JsonObject(
                element.mapValues { (key, value) ->
                    if (isSecretKey(key)) JsonPrimitive(REDACTED) else redactAt(value, mask, depth + 1)
                }
            )
        }
        if (element is JsonArray) {
            if (depth >= MAX_DEPTH) return JsonPrimitive(TRUNCATED)
            return JsonArray(element.map { redactAt(it, mask, depth + 1) })
        }
        if (element is JsonPrimitive && element.isString) {
            return JsonPrimitive(redactString(element.content, mask))
        }
        return element
    }

    /** Longest first, so a value that contains another one is masked whole. */
    private fun usableMask(mask: Collection<String>): List<String> =
        mask.filter { it.length >= MIN_MASK_LENGTH }.distinct().sortedByDescending { it.length }
}
