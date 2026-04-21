package com.mgafk.app.data.repository

import android.content.Context
import com.mgafk.app.data.AppJson
import com.mgafk.app.data.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Downloads the latest [Gemini userscript](https://github.com/Ariedam64/Gemini/releases/latest)
 * and keeps a local copy in `filesDir/gemini.user.js`.
 *
 * A successful fetch stores the release tag in SharedPreferences so subsequent
 * launches skip the network round-trip when already up-to-date. Offline behaviour
 * falls back to whatever is cached locally.
 */
object GeminiFetcher {

    private const val TAG = "GeminiFetcher"
    private const val RELEASES_URL = "https://api.github.com/repos/Ariedam64/Gemini/releases/latest"
    private const val PREFS = "gemini_fetcher"
    private const val KEY_TAG = "cached_tag"
    private const val SCRIPT_FILE = "gemini.user.js"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = AppJson.default

    /** Local file the userscript lives in once downloaded. */
    fun scriptFile(context: Context): File = File(context.filesDir, SCRIPT_FILE)

    /** Read the cached userscript (or null if nothing has ever been downloaded). */
    fun readCached(context: Context): String? {
        val file = scriptFile(context)
        return if (file.exists() && file.length() > 0) file.readText() else null
    }

    /**
     * Fetch the latest release and write it locally if the tag changed.
     * Returns the userscript contents (freshly fetched or already cached).
     * Returns null only if no cache exists AND the network request failed.
     */
    suspend fun fetchLatest(context: Context): String? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cachedTag = prefs.getString(KEY_TAG, null)

        try {
            val metaReq = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .build()
            val metaRes = client.newCall(metaReq).execute()
            if (!metaRes.isSuccessful) {
                AppLog.w(TAG, "releases/latest HTTP ${metaRes.code} — using cache if any")
                return@withContext readCached(context)
            }
            val body = metaRes.body?.string()
                ?: run {
                    AppLog.w(TAG, "releases/latest empty body — using cache if any")
                    return@withContext readCached(context)
                }

            val root = json.parseToJsonElement(body) as? JsonObject
                ?: return@withContext readCached(context)
            val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull
            val assets = root["assets"] as? JsonArray
            val downloadUrl = assets
                ?.mapNotNull { it as? JsonObject }
                ?.firstOrNull { (it["name"]?.jsonPrimitive?.contentOrNull ?: "").endsWith(".user.js") }
                ?.get("browser_download_url")?.jsonPrimitive?.contentOrNull

            if (tag == null || downloadUrl == null) {
                AppLog.w(TAG, "releases/latest missing tag or asset — using cache if any")
                return@withContext readCached(context)
            }

            val file = scriptFile(context)
            if (tag == cachedTag && file.exists() && file.length() > 0) {
                AppLog.d(TAG, "Gemini $tag already cached")
                return@withContext file.readText()
            }

            val dlReq = Request.Builder().url(downloadUrl).build()
            val dlRes = client.newCall(dlReq).execute()
            if (!dlRes.isSuccessful) {
                AppLog.w(TAG, "asset HTTP ${dlRes.code} — using cache if any")
                return@withContext readCached(context)
            }
            val script = dlRes.body?.string()
                ?: return@withContext readCached(context)

            file.writeText(script)
            prefs.edit().putString(KEY_TAG, tag).apply()
            AppLog.d(TAG, "Downloaded Gemini $tag (${script.length} chars)")
            script
        } catch (e: Exception) {
            AppLog.w(TAG, "fetchLatest failed: ${e.message} — using cache if any")
            readCached(context)
        }
    }
}
