package com.mgafk.app.data.repository

import android.content.Context
import com.mgafk.app.data.AppJson
import kotlinx.serialization.builtins.ListSerializer

/**
 * The reports sent from this device, with the follow token the backend handed back
 * (it keeps only its hash, so losing it here loses the follow-up). Kept in their own
 * SharedPreferences file, newest first, capped at what /bug-reports/mine accepts.
 *
 * Blocking reads and writes of a small file: call off the main thread.
 */
class BugReportStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = AppJson.storage
    private val serializer = ListSerializer(StoredBugReport.serializer())

    /** Newest first. */
    fun load(): List<StoredBugReport> {
        val raw = prefs.getString(KEY_REPORTS, null) ?: return emptyList()
        return try {
            json.decodeFromString(serializer, raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Adds or refreshes a report (matched by id), putting it first. */
    fun add(report: StoredBugReport) {
        val list = (listOf(report) + load().filter { it.id != report.id }).take(MAX_STORED)
        prefs.edit().putString(KEY_REPORTS, json.encodeToString(serializer, list)).apply()
    }

    private companion object {
        const val PREFS = "mgafk_bug_reports"
        const val KEY_REPORTS = "reports"
        const val MAX_STORED = BugReportPayload.MINE_MAX
    }
}
