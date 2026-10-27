package com.mgafk.app.data.repository

import com.mgafk.app.data.model.WsLog
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.text.SimpleDateFormat
import java.util.Locale

/** Device and app facts, read on the Android side and handed over as plain values. */
data class BugReportEnvironment(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    val appVersion: String,
    val appVersionCode: Int,
    val language: String,
    /** How long the app process has been running. */
    val uptimeSec: Long? = null,
)

/** Connection state of the active session, without anything that identifies the login. */
data class BugReportConnection(
    /** SessionStatus name: IDLE, CONNECTING, CONNECTED or ERROR. */
    val status: String,
    val roomId: String = "",
    val playersInRoom: Int = 0,
    val gameHost: String = "",
    val connectedForSec: Long? = null,
    val lastError: String = "",
    /** Sessions (game accounts) configured in the app, and how many are connected. */
    val accountsTotal: Int = 0,
    val accountsConnected: Int = 0,
)

/** One `context.logs` entry. [t] in epoch ms; [level] is error, warn, info or log. */
data class BugLogEntry(val t: Long?, val level: String, val msg: String, val source: String)

/**
 * What the app hands a bug report about the active session. MainViewModel builds it,
 * since it alone sees the sessions: a copy of a few fields, never the Session itself
 * (which holds the mc_jwt token).
 */
data class BugReportSnapshot(
    /** The player's stable id (Discord id), the one /collect-state stores the account state under. */
    val playerId: String? = null,
    val playerName: String? = null,
    val gameVersion: String? = null,
    val connection: BugReportConnection? = null,
    /** WebSocket and service logs, oldest first. */
    val logs: List<BugLogEntry> = emptyList(),
    /**
     * Values that must never leave the device (the sessions' tokens). They are not sent:
     * they only tell [BugReportRedaction] what to mask, should one surface in a log line.
     */
    val mask: List<String> = emptyList(),
)

/**
 * Builds the `context` object of a report: environment, game and connection state,
 * and the logs, redacted before they leave the device. Limits are the backend's:
 * at most 200 log entries, each message cut at 500 characters.
 */
object BugReportContext {
    const val MAX_LOG_ENTRIES = 200
    const val MAX_LOG_MESSAGE = 500
    /** Only the end of the crash log is sent: the most recent crashes. */
    const val CRASH_TAIL_CHARS = 8000
    const val MAX_CRASH_ENTRIES = CRASH_TAIL_CHARS / MAX_LOG_MESSAGE
    const val MAX_WS_ENTRIES = 100
    const val MAX_SERVICE_ENTRIES = 100
    const val SURFACE = "android"

    /** CrashLog starts every entry with "==== yyyy-MM-dd HH:mm:ss | process=... | thread=... ====". */
    private const val CRASH_HEADER = "==== "
    private const val CRASH_STAMP_PATTERN = "yyyy-MM-dd HH:mm:ss"
    private val LOG_LEVELS = setOf("error", "warn", "info", "log")

    fun logEntry(t: Long?, level: String, msg: String, source: String): BugLogEntry =
        BugLogEntry(
            t = t,
            level = if (level in LOG_LEVELS) level else "log",
            msg = BugReportPayload.clip(msg, MAX_LOG_MESSAGE),
            source = source,
        )

    /** WebSocket debug logs, which the app keeps newest first: the last ones, oldest first. */
    fun wsLogEntries(logs: List<WsLog>): List<BugLogEntry> =
        logs.take(MAX_WS_ENTRIES).asReversed().map {
            logEntry(it.timestamp, it.level, joinMessage(it.event, it.detail), "ws")
        }

    /** One AfkService log line (wake locks, service lifecycle). These carry no level. */
    fun serviceLogEntry(timestamp: Long, event: String, detail: String): BugLogEntry =
        logEntry(timestamp, "info", joinMessage(event, detail), "service")

    /**
     * The end of the crash log as `error` entries: its last [CRASH_TAIL_CHARS]
     * characters, split per crash and then into 500-character messages. When there is
     * more than [MAX_CRASH_ENTRIES] of it, the most recent crashes win, each one kept
     * from its header down (the exception and its top frames matter most).
     */
    fun crashLogEntries(crashLog: String): List<BugLogEntry> {
        if (crashLog.isBlank()) return emptyList()
        val blocks = splitCrashBlocks(crashLog.takeLast(CRASH_TAIL_CHARS))
        val kept = mutableListOf<List<BugLogEntry>>()
        var count = 0
        for (block in blocks.asReversed()) {
            val room = MAX_CRASH_ENTRIES - count
            if (room <= 0) break
            val stamp = crashStamp(block)
            val entries = block.chunked(MAX_LOG_MESSAGE).take(room).map { logEntry(stamp, "error", it, "crash") }
            kept.add(0, entries)
            count += entries.size
        }
        return kept.flatten()
    }

    /**
     * Crash entries first (they are the ones that matter, and the backend keeps the
     * tail of the list), then the most recent other entries by time, 200 at most.
     */
    fun mergeLogs(crash: List<BugLogEntry>, others: List<BugLogEntry>): List<BugLogEntry> {
        val keptCrash = crash.takeLast(MAX_LOG_ENTRIES)
        val room = MAX_LOG_ENTRIES - keptCrash.size
        val recent = others.sortedBy { it.t ?: 0L }.takeLast(room)
        return keptCrash + recent
    }

    /** The whole `context` object, redacted. */
    fun build(env: BugReportEnvironment, snapshot: BugReportSnapshot, crashLog: String): JsonObject {
        val logs = mergeLogs(crashLogEntries(crashLog), snapshot.logs)
        val raw = buildJsonObject {
            putJsonObject("environment") {
                put("surface", SURFACE)
                put("manufacturer", env.manufacturer)
                put("model", env.model)
                put("androidVersion", env.androidVersion)
                put("sdkInt", env.sdkInt)
                put("appVersion", env.appVersion)
                put("appVersionCode", env.appVersionCode)
                put("language", env.language)
                env.uptimeSec?.let { put("uptimeSec", it) }
            }
            val connection = snapshot.connection
            if (connection != null) {
                putJsonObject("game") {
                    if (connection.roomId.isNotBlank()) put("roomId", connection.roomId)
                    put("playersInRoom", connection.playersInRoom)
                    snapshot.gameVersion?.takeIf { it.isNotBlank() }?.let { put("gameVersion", it) }
                    if (connection.gameHost.isNotBlank()) put("host", connection.gameHost)
                }
                putJsonObject("connection") {
                    put("status", connection.status)
                    connection.connectedForSec?.let { put("connectedForSec", it) }
                    if (connection.lastError.isNotBlank()) {
                        put("lastError", BugReportPayload.clip(connection.lastError, MAX_LOG_MESSAGE))
                    }
                    put("accountsTotal", connection.accountsTotal)
                    put("accountsConnected", connection.accountsConnected)
                }
            }
            putJsonArray("logs") {
                logs.forEach { entry ->
                    addJsonObject {
                        put("t", entry.t)
                        put("level", entry.level)
                        put("msg", entry.msg)
                        put("source", entry.source)
                    }
                }
            }
        }
        return BugReportRedaction.redactObject(raw, snapshot.mask)
    }

    private fun joinMessage(event: String, detail: String): String =
        if (detail.isBlank()) event else "$event: $detail"

    /** The crash log cut into one string per crash, each starting at its header. */
    private fun splitCrashBlocks(text: String): List<String> {
        val blocks = mutableListOf<String>()
        val current = StringBuilder()
        for (line in text.lines()) {
            if (line.startsWith(CRASH_HEADER) && current.isNotBlank()) {
                blocks.add(current.toString().trimEnd())
                current.setLength(0)
            }
            current.append(line).append('\n')
        }
        if (current.isNotBlank()) blocks.add(current.toString().trimEnd())
        return blocks
    }

    /** When a crash was recorded, read back from its header (device local time, like the writer). */
    private fun crashStamp(block: String): Long? {
        if (!block.startsWith(CRASH_HEADER)) return null
        val stamp = block.removePrefix(CRASH_HEADER).substringBefore(" |").trim()
        return try {
            SimpleDateFormat(CRASH_STAMP_PATTERN, Locale.US).parse(stamp)?.time
        } catch (_: Exception) {
            null
        }
    }
}
