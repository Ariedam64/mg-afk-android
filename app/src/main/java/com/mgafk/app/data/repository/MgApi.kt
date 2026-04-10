package com.mgafk.app.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Client for https://mg-api.ariedam.fr
 *
 * Sprites: GET /assets/sprites/{category}/{name}.png
 */
object MgApi {

    private const val TAG = "MgApi"
    private const val BASE_URL = "https://mg-api.ariedam.fr"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ---- Thread-safe cache ----

    private val cache = ConcurrentHashMap<String, LinkedHashMap<String, GameEntry>>()
    private val mutationsCache = ConcurrentHashMap<String, MutationEntry>()

    /** Rarity tiers in game order (lowest -> highest) */
    val RARITY_ORDER = listOf("Common", "Uncommon", "Rare", "Legendary", "Mythic", "Divine", "Celestial")

    data class GameEntry(
        val id: String,
        val name: String,
        val sprite: String?,
        val rarity: String? = null,
        val cropSprite: String? = null,
        val maxScale: Double? = null,
        val baseSellPrice: Double? = null,
        val hoursToMature: Double? = null,
        val color: String? = null,
        val diet: List<String> = emptyList(),
    ) {
        val rarityIndex: Int get() = RARITY_ORDER.indexOf(rarity).let { if (it < 0) RARITY_ORDER.size else it }
    }

    data class MutationEntry(
        val name: String,
        val coinMultiplier: Double,
        val sprite: String? = null,
    )

    // ---- Public API ----

    @Volatile
    var isReady = false
        private set

    /**
     * Preload all categories in parallel. Call once at app startup.
     * After this completes, all get*() calls return instantly from cache.
     */
    suspend fun preloadAll() {
        val categories = listOf("pets", "items", "plants", "decors", "eggs", "weathers", "abilities")
        coroutineScope {
            val jobs = categories.map { cat ->
                async(Dispatchers.IO) {
                    try {
                        val data = fetchCategory(cat)
                        cache[cat] = data
                        Log.d(TAG, "Loaded $cat: ${data.size} entries")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load $cat: ${e.message}")
                        // Retry once
                        try {
                            val data = fetchCategory(cat)
                            cache[cat] = data
                            Log.d(TAG, "Retry OK $cat: ${data.size} entries")
                        } catch (e2: Exception) {
                            Log.e(TAG, "Retry also failed for $cat: ${e2.message}")
                        }
                    }
                }
            }
            // Fetch mutations separately (different structure)
            val mutJob = async(Dispatchers.IO) {
                try {
                    val data = fetchMutations()
                    mutationsCache.putAll(data)
                    Log.d(TAG, "Loaded mutations: ${data.size} entries")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load mutations: ${e.message}")
                    try {
                        val data = fetchMutations()
                        mutationsCache.putAll(data)
                        Log.d(TAG, "Retry OK mutations: ${data.size} entries")
                    } catch (e2: Exception) {
                        Log.e(TAG, "Retry also failed for mutations: ${e2.message}")
                    }
                }
            }
            jobs.forEach { it.await() }
            mutJob.await()
            isReady = true
            Log.d(TAG, "All preloaded. Cache keys: ${cache.keys}")
        }
    }

    fun getPets(): Map<String, GameEntry> = cache["pets"] ?: emptyMap()
    fun getItems(): Map<String, GameEntry> = cache["items"] ?: emptyMap()
    fun getPlants(): Map<String, GameEntry> = cache["plants"] ?: emptyMap()
    fun getDecors(): Map<String, GameEntry> = cache["decors"] ?: emptyMap()
    fun getEggs(): Map<String, GameEntry> = cache["eggs"] ?: emptyMap()
    fun getWeathers(): Map<String, GameEntry> = cache["weathers"] ?: emptyMap()
    fun getAbilities(): Map<String, GameEntry> = cache["abilities"] ?: emptyMap()
    fun getMutations(): Map<String, MutationEntry> = mutationsCache

    fun spriteUrl(category: String, name: String): String =
        "$BASE_URL/assets/sprites/$category/$name.png"

    /** Look up pet entry by species id. */
    fun findPet(speciesId: String): GameEntry? = getPets()[speciesId]

    /** Look up a full GameEntry for an item/seed/tool/egg/decor id. */
    fun findItem(itemId: String): GameEntry? {
        getPlants()[itemId]?.let { return it }
        getItems()[itemId]?.let { return it }
        getEggs()[itemId]?.let { return it }
        getDecors()[itemId]?.let { return it }
        // Case-insensitive fallback
        for (getter in listOf(::getPlants, ::getItems, ::getEggs, ::getDecors)) {
            val match = getter().entries.find { it.key.equals(itemId, ignoreCase = true) }
            if (match != null) return match.value
        }
        return null
    }

    /** Display name for an item id. */
    fun itemDisplayName(itemId: String): String = findItem(itemId)?.name ?: itemId

    /** Display name for an ability id. */
    fun abilityDisplayName(abilityId: String): String =
        getAbilities()[abilityId]?.name ?: abilityId

    /** Weather entry by API key. */
    fun weatherInfo(weatherKey: String): GameEntry? {
        getWeathers()[weatherKey]?.let { return it }
        return getWeathers().entries.find { it.key.equals(weatherKey, ignoreCase = true) }?.value
    }

    /** Clear all caches (call on version change) */
    fun clearCache() {
        cache.clear()
        mutationsCache.clear()
        isReady = false
    }

    // ---- Internal ----

    private fun fetchMutations(): Map<String, MutationEntry> {
        val request = Request.Builder()
            .url("$BASE_URL/DATA/mutations")
            .header("Accept", "application/json")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code} for /DATA/mutations")
        }
        val body = response.body?.string()
            ?: throw Exception("Empty body for /DATA/mutations")
        val root = json.parseToJsonElement(body) as? JsonObject
            ?: throw Exception("Invalid JSON for /DATA/mutations")

        val result = mutableMapOf<String, MutationEntry>()
        for ((id, element) in root) {
            val obj = element as? JsonObject ?: continue
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: id
            val coinMultiplier = obj["coinMultiplier"]?.jsonPrimitive?.doubleOrNull ?: 1.0
            val sprite = obj["sprite"]?.jsonPrimitive?.contentOrNull
            val entry = MutationEntry(name = name, coinMultiplier = coinMultiplier, sprite = sprite)
            // Key by both internal id (e.g. "Ambercharged") and display name (e.g. "Amberbound")
            result[id] = entry
            if (name != id) result[name] = entry
        }
        return result
    }

    private fun fetchCategory(category: String): LinkedHashMap<String, GameEntry> {
        val request = Request.Builder()
            .url("$BASE_URL/data/$category")
            .header("Accept", "application/json")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code} for /data/$category")
        }
        val body = response.body?.string()
            ?: throw Exception("Empty body for /data/$category")
        val root = json.parseToJsonElement(body) as? JsonObject
            ?: throw Exception("Invalid JSON for /data/$category")

        val result = LinkedHashMap<String, GameEntry>()
        for ((id, element) in root) {
            val obj = element as? JsonObject
            if (category == "plants") {
                // Plants have nested structure: { seed: { sprite, ... }, plant: { ... }, crop: { ... } }
                val seedObj = obj?.get("seed") as? JsonObject
                val plantObj = obj?.get("plant") as? JsonObject
                val cropObj = obj?.get("crop") as? JsonObject
                result[id] = GameEntry(
                    id = id,
                    name = seedObj?.get("name")?.jsonPrimitive?.contentOrNull
                        ?: plantObj?.get("name")?.jsonPrimitive?.contentOrNull
                        ?: id,
                    sprite = seedObj?.get("sprite")?.jsonPrimitive?.contentOrNull,
                    rarity = seedObj?.get("rarity")?.jsonPrimitive?.contentOrNull,
                    cropSprite = cropObj?.get("sprite")?.jsonPrimitive?.contentOrNull,
                    maxScale = cropObj?.get("maxScale")?.jsonPrimitive?.doubleOrNull,
                    baseSellPrice = cropObj?.get("baseSellPrice")?.jsonPrimitive?.doubleOrNull,
                )
            } else {
                val dietArray = (obj?.get("diet") as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?: emptyList()
                result[id] = GameEntry(
                    id = id,
                    name = obj?.get("name")?.jsonPrimitive?.contentOrNull ?: id,
                    sprite = obj?.get("sprite")?.jsonPrimitive?.contentOrNull,
                    rarity = obj?.get("rarity")?.jsonPrimitive?.contentOrNull,
                    maxScale = obj?.get("maxScale")?.jsonPrimitive?.doubleOrNull,
                    hoursToMature = obj?.get("hoursToMature")?.jsonPrimitive?.doubleOrNull,
                    color = obj?.get("color")?.jsonPrimitive?.contentOrNull,
                    diet = dietArray,
                )
            }
        }
        return result
    }
}
