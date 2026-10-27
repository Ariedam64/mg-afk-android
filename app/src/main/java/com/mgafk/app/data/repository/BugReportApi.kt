package com.mgafk.app.data.repository

import com.mgafk.app.data.AppLog
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/**
 * Client for the backend's public bug report routes (aries-mod-backend,
 * docs/bug-reports.md). Bodies are always JSON. No Authorization header: the app
 * has no Aries API key, so its reports are marked unverified, which is expected.
 *
 * Every call is blocking: run it on an IO dispatcher.
 */
object BugReportApi {
    private const val TAG = "BugReportApi"
    private const val BASE_URL = "https://ariesmod-api.ariedam.fr"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // A screenshot is up to ~1 MB of base64 on a phone connection: a longer write timeout.
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** POST /bug-reports. 201 creates the report; 200 means [followToken] had already created it. */
    fun submit(body: JsonObject, followToken: String): BugReportResult<BugReportCreated> =
        post("/bug-reports", body, BugReportCall.SUBMIT) { code, text ->
            BugReportResponses.parseCreated(code, text, followToken)
        }

    /** POST /bug-reports/:id/attachments, one screenshot per request. A byte-identical resend answers 200. */
    fun uploadAttachment(reportId: Long, body: JsonObject): BugReportResult<Unit> =
        post("/bug-reports/$reportId/attachments", body, BugReportCall.ATTACHMENT) { _, _ -> Unit }

    /** POST /bug-reports/mine: status, public reply and comments of the reports we hold a token for. */
    fun fetchMine(body: JsonObject): BugReportResult<List<MyBugReport>> =
        post("/bug-reports/mine", body, BugReportCall.MINE) { _, text ->
            BugReportResponses.parseMine(text)
        }

    /** POST /bug-reports/:id/comments. */
    fun postComment(reportId: Long, body: JsonObject): BugReportResult<Unit> =
        post("/bug-reports/$reportId/comments", body, BugReportCall.COMMENT) { _, _ -> Unit }

    private fun <T> post(
        path: String,
        body: JsonObject,
        call: BugReportCall,
        parse: (Int, String?) -> T?,
    ): BugReportResult<T> {
        return try {
            val request = Request.Builder()
                .url(BASE_URL + path)
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string()
                if (response.isSuccessful) {
                    val value = parse(response.code, text)
                    if (value != null) {
                        BugReportResult.Ok(value)
                    } else {
                        AppLog.w(TAG, "Unreadable answer from $path (HTTP ${response.code})")
                        BugReportResult.Failed(BugReportResponses.unreadable(response.code))
                    }
                } else {
                    AppLog.w(TAG, "HTTP ${response.code} on $path")
                    BugReportResult.Failed(
                        BugReportResponses.error(call, response.code, text, response.header("Retry-After"))
                    )
                }
            }
        } catch (e: InterruptedIOException) {
            AppLog.w(TAG, "$path timed out: ${e.message}")
            BugReportResult.Failed(BugReportResponses.timeoutError(call))
        } catch (e: IOException) {
            AppLog.w(TAG, "$path failed: ${e.message}")
            BugReportResult.Failed(BugReportResponses.networkError())
        } catch (e: Exception) {
            AppLog.w(TAG, "$path failed: ${e.message}")
            BugReportResult.Failed(BugReportResponses.networkError())
        }
    }
}
