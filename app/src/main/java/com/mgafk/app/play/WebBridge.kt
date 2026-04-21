package com.mgafk.app.play

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.mgafk.app.data.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.mgafk.app.data.AppJson
import java.util.concurrent.TimeUnit

/**
 * Native HTTP bridge exposed to the [PlayActivity] WebView so the Gemini
 * userscript can fire cross-origin requests without being blocked by browser
 * CORS — same role as Tampermonkey's `GM_xmlhttpRequest`.
 *
 * JS side calls `MgAfkBridge.httpRequest(method, url, headersJson, body, callbackId)`
 * synchronously, which dispatches an OkHttp call on the IO dispatcher and, on
 * completion, invokes `window.__MgAfkBridge.callback(callbackId, resultJson)` on
 * the main thread.
 */
class WebBridge(
    private val webView: WebView,
    private val scope: CoroutineScope,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = AppJson.default

    @JavascriptInterface
    fun httpRequest(
        method: String?,
        url: String?,
        headersJson: String?,
        body: String?,
        callbackId: String?,
        responseType: String?,
    ) {
        if (url.isNullOrBlank() || callbackId.isNullOrBlank()) return
        val verb = (method ?: "GET").uppercase()
        // Binary types ship the body as base64 alongside an empty `body` text
        // field; everything else uses the utf-8 text body directly.
        val isBinary = responseType == "blob" || responseType == "arraybuffer"

        scope.launch(Dispatchers.IO) {
            val resultJson = try {
                val headers = parseHeaders(headersJson)
                val contentType = headers.entries.firstOrNull { it.key.equals("Content-Type", true) }
                    ?.value?.toMediaTypeOrNull()
                val reqBody = when {
                    verb == "GET" || verb == "HEAD" -> null
                    body == null -> ByteArray(0).toRequestBody(contentType)
                    else -> body.toRequestBody(contentType)
                }
                val req = Request.Builder().url(url).method(verb, reqBody).apply {
                    headers.forEach { (k, v) -> if (k.isNotBlank()) header(k, v) }
                }.build()
                val res = client.newCall(req).execute()
                val bytes = res.body?.bytes() ?: ByteArray(0)
                buildJsonObject {
                    put("status", res.code)
                    put("statusText", res.message)
                    put("finalUrl", res.request.url.toString())
                    val headerLines = res.headers.joinToString("\n") { "${it.first}: ${it.second}" }
                    put("headers", headerLines)
                    if (isBinary) {
                        put("body", "")
                        put("bodyB64", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                    } else {
                        put("body", String(bytes, Charsets.UTF_8))
                    }
                }.toString()
            } catch (e: Exception) {
                AppLog.w("WebBridge", "httpRequest failed for $url: ${e.message}")
                buildJsonObject {
                    put("error", e.message ?: "unknown error")
                }.toString()
            }

            withContext(Dispatchers.Main) {
                // Use JSON-stringified callback id and pre-built JSON object; we
                // pass them as JS literals (resultJson is already valid JSON).
                val js = "window.__MgAfkBridge && window.__MgAfkBridge.callback(${jsonString(callbackId)}, $resultJson);"
                webView.evaluateJavascript(js, null)
            }
        }
    }

    private fun parseHeaders(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = json.parseToJsonElement(raw) as? JsonObject ?: return emptyMap()
            obj.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull ?: v.toString() }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** JSON-quote a single string (without leaning on a full encoder). */
    private fun jsonString(value: String): String = JsonPrimitive(value).toString()
}
