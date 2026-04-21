package com.mgafk.app.data.repository

import com.mgafk.app.data.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import com.mgafk.app.data.AppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
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

    private val json = AppJson.default

    // ---- Thread-safe cache ----

    private val cache = ConcurrentHashMap<String, LinkedHashMap<String, GameEntry>>()
    private val mutationsCache = ConcurrentHashMap<String, MutationEntry>()
    private val plantSpriteMetaCache = ConcurrentHashMap<String, SpriteMetadata>()

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
        val maturitySellPrice: Double? = null,
        val color: String? = null,
        val diet: List<String> = emptyList(),
        // Plant-only visual data (from `/data/plants`)
        val plantSprite: String? = null,
        val plantSlotOffsets: List<SlotOffset> = emptyList(),
        val plantBaseTileScale: Double? = null,
        val plantTileTransformOrigin: String? = null,
        val cropBaseTileScale: Double? = null,
        val cropTransformOrigin: String? = null,
        // Egg-only: weight of each pet species that can hatch from this egg
        val faunaSpawnWeights: Map<String, Double> = emptyMap(),
        // Decor-only (PetHutch): capacity upgrade tiers
        val upgrades: List<DecorUpgrade> = emptyList(),
    ) {
        val rarityIndex: Int get() = RARITY_ORDER.indexOf(rarity).let { if (it < 0) RARITY_ORDER.size else it }
    }

    /** Normalized slot offset from the plant data (x/y in tile units, rotation in degrees). */
    data class SlotOffset(val x: Double, val y: Double, val rotation: Double)

    /** Decor upgrade tier (used by PetHutch). */
    data class DecorUpgrade(
        val targetLevel: Int,
        val dustCost: Long,
        val capacityBonus: Int,
    )

    /** Atlas metadata for a sprite: source canvas size and anchor point (fractions 0..1). */
    data class SpriteMetadata(
        val sourceWidth: Int,
        val sourceHeight: Int,
        val anchorX: Double,
        val anchorY: Double,
    )

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
                        AppLog.d(TAG, "Loaded $cat: ${data.size} entries")
                    } catch (e: Exception) {
                        AppLog.e(TAG, "Failed to load $cat: ${e.message}")
                        // Retry once
                        try {
                            val data = fetchCategory(cat)
                            cache[cat] = data
                            AppLog.d(TAG, "Retry OK $cat: ${data.size} entries")
                        } catch (e2: Exception) {
                            AppLog.e(TAG, "Retry also failed for $cat: ${e2.message}")
                        }
                    }
                }
            }
            // Fetch mutations separately (different structure)
            val mutJob = async(Dispatchers.IO) {
                try {
                    val data = fetchMutations()
                    mutationsCache.putAll(data)
                    AppLog.d(TAG, "Loaded mutations: ${data.size} entries")
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to load mutations: ${e.message}")
                    try {
                        val data = fetchMutations()
                        mutationsCache.putAll(data)
                        AppLog.d(TAG, "Retry OK mutations: ${data.size} entries")
                    } catch (e2: Exception) {
                        AppLog.e(TAG, "Retry also failed for mutations: ${e2.message}")
                    }
                }
            }
            // Fetch plant sprite metadata (sourceSize + anchor) for accurate crop
            // placement in PlantCompositeSprite.
            val spriteMetaJob = async(Dispatchers.IO) {
                try {
                    val data = fetchPlantSpriteMetadata()
                    plantSpriteMetaCache.putAll(data)
                    AppLog.d(TAG, "Loaded plant sprite metadata: ${data.size} entries")
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to load plant sprite metadata: ${e.message}")
                }
            }
            jobs.forEach { it.await() }
            mutJob.await()
            spriteMetaJob.await()
            isReady = true
            AppLog.d(TAG, "All preloaded. Cache keys: ${cache.keys}")
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

    /** Atlas metadata for a plant sprite (keyed by filename without `.png`). */
    fun getPlantSpriteMetadata(spriteName: String): SpriteMetadata? =
        plantSpriteMetaCache[spriteName]

    fun spriteUrl(category: String, name: String): String =
        "$BASE_URL/assets/sprites/$category/$name.png"

    /** Shortcut for UI sprites: `sprites/ui/{name}.png`. */
    fun uiSpriteUrl(name: String): String = spriteUrl("ui", name)

    /** URL for a plant sprite (e.g. "Carrot", "Starweaver"). */
    fun plantSpriteUrl(name: String): String = spriteUrl("plants", name)

    val coinBagUrl: String get() = uiSpriteUrl("CoinBag")
    val lockSpriteUrl: String get() = uiSpriteUrl("Locked")
    val unlockSpriteUrl: String get() = uiSpriteUrl("Unlocked")
    val magicDustUrl: String get() = spriteUrl("items", "MagicDust")

    private val MUTATION_SPRITE_ALIAS = mapOf("Ambershine" to "Amberlit")

    /** URL for a mutation sprite (`ui/Mutation{Name}.png`). */
    fun mutationSpriteUrl(mutation: String): String {
        val name = MUTATION_SPRITE_ALIAS[mutation] ?: mutation
        return uiSpriteUrl("Mutation$name")
    }

    /**
     * URL for a composed sprite: base sprite + mutation layers rendered server-side.
     * `key` is the atlas key (e.g. `sprite/pet/Bunny`, `sprite/plant/MoonCelestialCrop`).
     * Returns `null` if `mutations` is empty — callers should fall back to the plain sprite URL.
     */
    fun composedSpriteUrl(key: String, mutations: List<String>): String? {
        if (mutations.isEmpty()) return null
        val encodedKey = URLEncoder.encode(key, "UTF-8")
        val encodedMuts = mutations.joinToString(",") { URLEncoder.encode(it, "UTF-8") }
        return "$BASE_URL/assets/sprites/composed?key=$encodedKey&mutations=$encodedMuts"
    }

    /**
     * URL for a pet sprite. The sprite filename is read from `GameEntry.sprite`
     * loaded from the API (e.g. `.../pets/Bunny.png` → `Bunny`), so the composed
     * key matches what the server knows. When mutations are non-empty the composed
     * endpoint is used, otherwise the plain sprite URL is returned.
     * Returns `null` if the pet is not in the cache yet.
     */
    fun petSpriteUrl(species: String, mutations: List<String> = emptyList()): String? {
        val baseUrl = getPets()[species]?.sprite ?: return null
        if (mutations.isEmpty()) return baseUrl
        val spriteName = spriteNameFromUrl(baseUrl) ?: return baseUrl
        return composedSpriteUrl("sprite/pet/$spriteName", mutations) ?: baseUrl
    }

    /**
     * URL for a crop sprite. Reads the sprite filename from `GameEntry.cropSprite`
     * (e.g. `.../plants/MoonCelestialCrop.png` → `MoonCelestialCrop`) so the composed
     * key matches the atlas. Returns `null` if the plant is not in the cache yet.
     */
    fun cropSpriteUrl(species: String, mutations: List<String> = emptyList()): String? {
        val baseUrl = getPlants()[species]?.cropSprite ?: return null
        if (mutations.isEmpty()) return baseUrl
        val spriteName = spriteNameFromUrl(baseUrl) ?: return baseUrl
        return composedSpriteUrl("sprite/plant/$spriteName", mutations) ?: baseUrl
    }

    /** Extract the sprite filename (without `.png` or query string) from a sprite URL. */
    private fun spriteNameFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val file = url.substringAfterLast('/').substringBefore('?')
        return file.removeSuffix(".png").ifBlank { null }
    }

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
        plantSpriteMetaCache.clear()
        isReady = false
    }

    // ---- Internal ----

    private fun fetchPlantSpriteMetadata(): Map<String, SpriteMetadata> {
        val request = Request.Builder()
            .url("$BASE_URL/assets/sprite-data?cat=plants&full=1")
            .header("Accept", "application/json")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code} for sprite-data?cat=plants")
        }
        val body = response.body?.string()
            ?: throw Exception("Empty body for sprite-data?cat=plants")
        val root = json.parseToJsonElement(body) as? JsonObject
            ?: throw Exception("Invalid JSON for sprite-data?cat=plants")

        val categories = root["categories"] as? JsonArray ?: return emptyMap()
        val result = mutableMapOf<String, SpriteMetadata>()
        for (catElement in categories) {
            val catObj = catElement as? JsonObject ?: continue
            val items = catObj["items"] as? JsonArray ?: continue
            for (itemElement in items) {
                val item = itemElement as? JsonObject ?: continue
                val name = item["name"]?.jsonPrimitive?.contentOrNull ?: continue
                val sourceSize = item["sourceSize"] as? JsonObject ?: continue
                val anchor = item["anchor"] as? JsonObject ?: continue
                val w = sourceSize["w"]?.jsonPrimitive?.intOrNull ?: continue
                val h = sourceSize["h"]?.jsonPrimitive?.intOrNull ?: continue
                val ax = anchor["x"]?.jsonPrimitive?.doubleOrNull ?: 0.5
                val ay = anchor["y"]?.jsonPrimitive?.doubleOrNull ?: 0.5
                result[name] = SpriteMetadata(w, h, ax, ay)
            }
        }
        return result
    }

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
                val slotOffsets = (plantObj?.get("slotOffsets") as? JsonArray)
                    ?.mapNotNull { el ->
                        val o = el as? JsonObject ?: return@mapNotNull null
                        SlotOffset(
                            x = o["x"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                            y = o["y"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                            rotation = o["rotation"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        )
                    } ?: emptyList()
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
                    plantSprite = plantObj?.get("sprite")?.jsonPrimitive?.contentOrNull,
                    plantSlotOffsets = slotOffsets,
                    plantBaseTileScale = plantObj?.get("baseTileScale")?.jsonPrimitive?.doubleOrNull,
                    plantTileTransformOrigin = plantObj?.get("tileTransformOrigin")?.jsonPrimitive?.contentOrNull,
                    cropBaseTileScale = cropObj?.get("baseTileScale")?.jsonPrimitive?.doubleOrNull,
                    cropTransformOrigin = cropObj?.get("transformOrigin")?.jsonPrimitive?.contentOrNull,
                )
            } else {
                val dietArray = (obj?.get("diet") as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?: emptyList()
                val faunaWeights = if (category == "eggs") {
                    (obj?.get("faunaSpawnWeights") as? JsonObject)
                        ?.mapValues { (it.value as? JsonPrimitive)?.doubleOrNull ?: 0.0 }
                        ?: emptyMap()
                } else emptyMap()
                val upgrades = if (category == "decors") {
                    (obj?.get("upgrades") as? JsonArray)
                        ?.mapNotNull { el ->
                            val o = el as? JsonObject ?: return@mapNotNull null
                            val level = o["targetLevel"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                            val cost = (o["cost"] as? JsonObject)
                                ?.get("dustQuantity")?.jsonPrimitive?.doubleOrNull?.toLong() ?: 0L
                            val bonus = o["capacityBonus"]?.jsonPrimitive?.intOrNull ?: 0
                            DecorUpgrade(level, cost, bonus)
                        } ?: emptyList()
                } else emptyList()
                result[id] = GameEntry(
                    id = id,
                    name = obj?.get("name")?.jsonPrimitive?.contentOrNull ?: id,
                    sprite = obj?.get("sprite")?.jsonPrimitive?.contentOrNull,
                    rarity = obj?.get("rarity")?.jsonPrimitive?.contentOrNull,
                    maxScale = obj?.get("maxScale")?.jsonPrimitive?.doubleOrNull,
                    hoursToMature = obj?.get("hoursToMature")?.jsonPrimitive?.doubleOrNull,
                    maturitySellPrice = obj?.get("maturitySellPrice")?.jsonPrimitive?.doubleOrNull,
                    color = obj?.get("color")?.jsonPrimitive?.contentOrNull,
                    diet = dietArray,
                    faunaSpawnWeights = faunaWeights,
                    upgrades = upgrades,
                )
            }
        }
        return result
    }
}
