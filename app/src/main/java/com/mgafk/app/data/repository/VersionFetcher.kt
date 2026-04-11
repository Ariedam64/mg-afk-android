package com.mgafk.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mgafk.app.data.AppJson
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

data class AppRelease(
    val tagName: String,
    val downloadUrl: String,
)

object VersionFetcher {
    private val client = OkHttpClient()
    private val json = AppJson.default

    suspend fun fetchGameVersion(host: String = "magicgarden.gg"): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://$host/platform/v1/version")
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty version response")
        val obj = json.parseToJsonElement(body).jsonObject
        obj["version"]?.jsonPrimitive?.content ?: throw Exception("No version field")
    }

    /**
     * Fetch the latest GitHub release for the app.
     * Returns null if the request fails (no crash on network error).
     */
    suspend fun fetchLatestRelease(): AppRelease? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/Ariedam64/mg-afk-android/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            val obj = json.parseToJsonElement(body).jsonObject
            val tag = obj["tag_name"]?.jsonPrimitive?.content ?: return@withContext null
            // Find the .apk asset download URL
            val assets = obj["assets"]?.jsonArray
            val apkAsset = assets?.firstOrNull { asset ->
                asset.jsonObject["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true
            }
            val downloadUrl = apkAsset?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content
                ?: obj["html_url"]?.jsonPrimitive?.content
                ?: "https://github.com/Ariedam64/mg-afk-android/releases/latest"
            AppRelease(tagName = tag, downloadUrl = downloadUrl)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Compare two semver strings (e.g. "v1.0.0" vs "1.0.0").
     * Returns true if [remote] is strictly newer than [current].
     */
    fun isNewer(current: String, remote: String): Boolean {
        val parse = { v: String ->
            v.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        }
        val cur = parse(current)
        val rem = parse(remote)
        for (i in 0 until maxOf(cur.size, rem.size)) {
            val c = cur.getOrElse(i) { 0 }
            val r = rem.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
