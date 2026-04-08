package com.mgafk.app.data.websocket.state

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class Position(val x: Double, val y: Double)

/**
 * Rich player model merging room player data + game userSlot data.
 * Port of Websocket mg / state/models/player.js
 */
data class PlayerModel(
    // --- Room data (from roomState.players[]) ---
    val id: String,
    val name: String,
    val isConnected: Boolean,
    val discordAvatarUrl: String? = null,
    val cosmetic: JsonObject? = null,
    val emoteData: JsonObject? = null,
    val databaseUserId: String? = null,
    val guildId: String? = null,
    val secondsRemainingUntilChatEnabled: Int? = null,

    // --- Game data (from gameState.userSlots[]) ---
    val slotIndex: Int? = null,

    // Slot top-level
    val position: Position? = null,
    val lastActionEvent: JsonObject? = null,
    val petSlotInfos: JsonObject? = null,
    val customRestockInventories: JsonObject? = null,
    val hasBeenSupersededByAnotherRoom: Boolean = false,
    val lastSlotMachineInfo: JsonObject? = null,
    val selectedItemIndex: Int? = null,

    // Slot data
    val coins: Int = 0,
    val schemaVersion: String? = null,
    val inventory: JsonArray = EMPTY_ARRAY,
    val storages: JsonArray = EMPTY_ARRAY,
    val favoritedItemIds: List<String> = emptyList(),
    val garden: JsonObject? = null,
    val petSlots: JsonArray = EMPTY_ARRAY,
    val shopPurchases: JsonObject? = null,
    val customRestocks: JsonObject? = null,
    val journal: JsonObject? = null,
    val tasksCompleted: JsonArray = EMPTY_ARRAY,
    val stats: JsonObject? = null,
    val activityLogs: JsonArray = EMPTY_ARRAY,
) {
    // --- Inventory helpers ---

    fun getSeeds(): List<JsonObject> = filterInventory("Seed")
    fun getTools(): List<JsonObject> = filterInventory("Tool")
    fun getEggs(): List<JsonObject> = filterInventory("Egg")
    fun getPets(): List<JsonObject> = filterInventory("Pet")
    fun getPlants(): List<JsonObject> = filterInventory("Plant")
    fun getProduce(): List<JsonObject> = filterInventory("Produce")
    fun getDecor(): List<JsonObject> = filterInventory("Decor")

    private fun filterInventory(type: String): List<JsonObject> =
        inventory.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val itemType = obj["itemType"]?.jsonPrimitive?.contentOrNull
            if (itemType == type) obj else null
        }

    // --- Storage helpers ---

    fun getStorage(decorId: String): JsonObject? =
        storages.firstNotNullOfOrNull { el ->
            val obj = el as? JsonObject ?: return@firstNotNullOfOrNull null
            if (obj["decorId"]?.jsonPrimitive?.contentOrNull == decorId) obj else null
        }

    fun getStorageItems(decorId: String): JsonArray =
        getStorage(decorId)?.get("items") as? JsonArray ?: EMPTY_ARRAY

    fun getPetHutch(): JsonArray = getStorageItems("PetHutch")
    fun getSeedSilo(): JsonArray = getStorageItems("SeedSilo")
    fun getDecorShed(): JsonArray = getStorageItems("DecorShed")
    fun getFeedingTrough(): JsonArray = getStorageItems("FeedingTrough")

    // --- Garden helpers ---

    fun getGardenTiles(): JsonObject? = garden?.get("tileObjects") as? JsonObject
    fun getBoardwalkTiles(): JsonObject? = garden?.get("boardwalkTileObjects") as? JsonObject

    fun getGardenPlants(): List<GardenTile> = filterGardenTiles("plant")
    fun getGardenEggs(): List<GardenTile> = filterGardenTiles("egg")
    fun getGardenDecor(): List<GardenTile> = filterGardenTiles("decor")

    private fun filterGardenTiles(objectType: String): List<GardenTile> {
        val tiles = getGardenTiles() ?: return emptyList()
        return tiles.entries
            .filter { (_, v) ->
                (v as? JsonObject)?.get("objectType")?.jsonPrimitive?.contentOrNull == objectType
            }
            .map { (k, v) -> GardenTile(tileId = k.toIntOrNull() ?: 0, data = v.jsonObject) }
    }

    // --- Pet helpers ---

    fun getActivePets(): JsonArray = petSlots

    /** Typed active pet list */
    fun getActivePetInfos(): List<PetInfo> =
        petSlots.mapIndexedNotNull { index, el ->
            PetInfo.fromJson(el as? JsonObject ?: return@mapIndexedNotNull null, index)
        }

    fun getAllPets(): List<JsonObject> {
        val fromInventory = getPets()
        val fromHutch = getPetHutch().mapNotNull { it as? JsonObject }
        val fromSlots = petSlots.mapNotNull { it as? JsonObject }
        return fromInventory + fromHutch + fromSlots
    }

    companion object {
        private val EMPTY_ARRAY = JsonArray(emptyList())

        fun fromRoomPlayer(roomPlayer: JsonObject): PlayerModel {
            return PlayerModel(
                id = roomPlayer.string("id"),
                name = roomPlayer.string("name"),
                isConnected = roomPlayer["isConnected"]?.jsonPrimitive?.booleanOrNull ?: false,
                discordAvatarUrl = roomPlayer["discordAvatarUrl"]?.jsonPrimitive?.contentOrNull,
                cosmetic = roomPlayer["cosmetic"] as? JsonObject,
                emoteData = roomPlayer["emoteData"] as? JsonObject,
                databaseUserId = roomPlayer["databaseUserId"]?.jsonPrimitive?.contentOrNull,
                guildId = roomPlayer["guildId"]?.jsonPrimitive?.contentOrNull,
                secondsRemainingUntilChatEnabled = roomPlayer["secondsRemainingUntilChatEnabled"]?.jsonPrimitive?.intOrNull,
            )
        }

        fun applySlot(player: PlayerModel, slot: JsonObject, slotIndex: Int): PlayerModel {
            val data = slot["data"] as? JsonObject

            val inv = data?.get("inventory") as? JsonObject

            return player.copy(
                slotIndex = slotIndex,
                position = (slot["position"] as? JsonObject)?.let {
                    Position(
                        x = it["x"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        y = it["y"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    )
                },
                lastActionEvent = slot["lastActionEvent"] as? JsonObject,
                petSlotInfos = slot["petSlotInfos"] as? JsonObject,
                customRestockInventories = slot["customRestockInventories"] as? JsonObject,
                hasBeenSupersededByAnotherRoom = slot["hasBeenSupersededByAnotherRoom"]?.jsonPrimitive?.booleanOrNull ?: false,
                lastSlotMachineInfo = slot["lastSlotMachineInfo"] as? JsonObject,
                selectedItemIndex = slot["notAuthoritative_selectedItemIndex"]?.jsonPrimitive?.intOrNull,

                // Slot data fields
                schemaVersion = data?.get("schemaVersion")?.jsonPrimitive?.contentOrNull,
                coins = data?.get("coinsCount")?.jsonPrimitive?.intOrNull ?: 0,
                inventory = inv?.get("items") as? JsonArray ?: EMPTY_ARRAY,
                storages = inv?.get("storages") as? JsonArray ?: EMPTY_ARRAY,
                favoritedItemIds = (inv?.get("favoritedItemIds") as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                garden = data?.get("garden") as? JsonObject,
                petSlots = data?.get("petSlots") as? JsonArray ?: EMPTY_ARRAY,
                shopPurchases = data?.get("shopPurchases") as? JsonObject,
                customRestocks = data?.get("customRestocks") as? JsonObject,
                journal = data?.get("journal") as? JsonObject,
                tasksCompleted = data?.get("tasksCompleted") as? JsonArray ?: EMPTY_ARRAY,
                stats = data?.get("stats") as? JsonObject,
                activityLogs = data?.get("activityLogs") as? JsonArray ?: EMPTY_ARRAY,
            )
        }
    }
}

data class GardenTile(val tileId: Int, val data: JsonObject)

data class PetInfo(
    val id: String,
    val name: String,
    val species: String,
    val hunger: Double,
    val index: Int,
    val mutations: List<String>,
) {
    companion object {
        fun fromJson(obj: JsonObject, index: Int): PetInfo {
            val mutations = (obj["mutations"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            return PetInfo(
                id = obj.string("id"),
                name = obj.string("name"),
                species = obj["petSpecies"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                hunger = obj["hunger"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                index = index,
                mutations = mutations,
            )
        }
    }
}

// Extension helper
internal fun JsonObject.string(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
