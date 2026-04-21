package com.mgafk.app.data.websocket

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * All game actions that can be sent via WebSocket.
 * Port of Websocket mg / actions.js (51+ actions)
 *
 * Usage:
 *   val actions = GameActions { text -> webSocket.send(text) }
 *   actions.chat("Hello!")
 *   actions.move(100.0, 200.0)
 */
class GameActions(private val sendFn: (String) -> Unit) {

    private fun send(scopePath: List<String>, type: String, params: JsonObject = EMPTY_OBJ) {
        val msg = buildJsonObject {
            put("scopePath", buildJsonArray { scopePath.forEach { add(JsonPrimitive(it)) } })
            put("type", JsonPrimitive(type))
            for ((k, v) in params) {
                put(k, v)
            }
        }
        sendFn(msg.toString())
    }

    private fun room(type: String, params: JsonObject = EMPTY_OBJ) =
        send(ROOM_SCOPE, type, params)

    private fun game(type: String, params: JsonObject = EMPTY_OBJ) =
        send(GAME_SCOPE, type, params)

    // =====================
    // Session / Heartbeat
    // =====================

    fun ping(id: Long = System.currentTimeMillis()) =
        game("Ping", obj("id" to JsonPrimitive(id)))

    fun setSelectedGame(gameId: String = GAME) =
        room("SetSelectedGame", obj("gameId" to JsonPrimitive(gameId)))

    fun voteForGame(gameId: String = GAME) =
        room("VoteForGame", obj("gameId" to JsonPrimitive(gameId)))

    fun restartGame() = room("RestartGame")

    fun checkWeatherStatus() = game("CheckWeatherStatus")

    // =====================
    // Social / Chat
    // =====================

    fun chat(message: String) =
        room("Chat", obj("message" to JsonPrimitive(message)))

    fun emote(emoteType: String) =
        room("Emote", obj("emoteType" to JsonPrimitive(emoteType)))

    fun wish(itemId: String) =
        game("Wish", obj("itemId" to JsonPrimitive(itemId)))

    fun kickPlayer(playerId: String) =
        room("KickPlayer", obj("playerId" to JsonPrimitive(playerId)))

    fun setPlayerData(name: String? = null, cosmetic: JsonElement? = null) {
        val params = buildJsonObject {
            if (name != null) put("name", JsonPrimitive(name))
            if (cosmetic != null) put("cosmetic", cosmetic)
        }
        room("SetPlayerData", params)
    }

    fun usurpHost() = game("UsurpHost")

    fun reportSpeakingStart() = game("ReportSpeakingStart")

    // =====================
    // Movement
    // =====================

    fun move(x: Double, y: Double) =
        game("PlayerPosition", obj("position" to position(x, y)))

    fun teleport(x: Double, y: Double) =
        game("Teleport", obj("position" to position(x, y)))

    // =====================
    // Shop / Purchases
    // =====================

    fun purchaseSeed(species: String) =
        game("PurchaseSeed", obj("species" to JsonPrimitive(species)))

    fun purchaseTool(toolId: String) =
        game("PurchaseTool", obj("toolId" to JsonPrimitive(toolId)))

    fun purchaseEgg(eggId: String) =
        game("PurchaseEgg", obj("eggId" to JsonPrimitive(eggId)))

    fun purchaseDecor(decorId: String) =
        game("PurchaseDecor", obj("decorId" to JsonPrimitive(decorId)))

    // =====================
    // Garden / Crops
    // =====================

    fun plantSeed(slot: Int, species: String) =
        game("PlantSeed", obj("slot" to JsonPrimitive(slot), "species" to JsonPrimitive(species)))

    fun waterPlant(slot: Int) =
        game("WaterPlant", obj("slot" to JsonPrimitive(slot)))

    fun harvestCrop(slot: Int, slotsIndex: Int? = null) {
        val params = buildJsonObject {
            put("slot", JsonPrimitive(slot))
            if (slotsIndex != null) put("slotsIndex", JsonPrimitive(slotsIndex))
        }
        game("HarvestCrop", params)
    }

    fun sellAllCrops() = game("SellAllCrops")

    fun plantGardenPlant(slot: Int, itemId: String) =
        game("PlantGardenPlant", obj("slot" to JsonPrimitive(slot), "itemId" to JsonPrimitive(itemId)))

    fun potPlant(slot: Int) =
        game("PotPlant", obj("slot" to JsonPrimitive(slot)))

    fun mutationPotion(tileObjectIdx: Int, growSlotIdx: Int, mutation: String) =
        game("MutationPotion", obj(
            "tileObjectIdx" to JsonPrimitive(tileObjectIdx),
            "growSlotIdx" to JsonPrimitive(growSlotIdx),
            "mutation" to JsonPrimitive(mutation),
        ))

    fun cropCleanser(tileObjectIdx: Int, growSlotIdx: Int) =
        game("CropCleanser", obj(
            "tileObjectIdx" to JsonPrimitive(tileObjectIdx),
            "growSlotIdx" to JsonPrimitive(growSlotIdx),
        ))

    fun removeGardenObject(slot: Int, slotType: String) =
        game("RemoveGardenObject", obj("slot" to JsonPrimitive(slot), "slotType" to JsonPrimitive(slotType)))

    // =====================
    // Decor
    // =====================

    fun placeDecor(decorId: String, tileType: String, localTileIndex: Int, rotation: Int? = null) {
        val params = buildJsonObject {
            put("decorId", JsonPrimitive(decorId))
            put("tileType", JsonPrimitive(tileType))
            put("localTileIndex", JsonPrimitive(localTileIndex))
            if (rotation != null) put("rotation", JsonPrimitive(rotation))
        }
        game("PlaceDecor", params)
    }

    fun pickupDecor(tileType: String, localTileIndex: Int) =
        game("PickupDecor", obj("tileType" to JsonPrimitive(tileType), "localTileIndex" to JsonPrimitive(localTileIndex)))

    // =====================
    // Pets
    // =====================

    fun placePet(itemId: String, position: JsonElement, tileType: String, localTileIndex: Int) =
        game("PlacePet", obj(
            "itemId" to JsonPrimitive(itemId),
            "position" to position,
            "tileType" to JsonPrimitive(tileType),
            "localTileIndex" to JsonPrimitive(localTileIndex),
        ))

    fun placePet(itemId: String, x: Double, y: Double, tileType: String, localTileIndex: Int) =
        placePet(itemId, position(x, y), tileType, localTileIndex)

    fun pickupPet(petId: String) =
        game("PickupPet", obj("petId" to JsonPrimitive(petId)))

    fun feedPet(petItemId: String, cropItemId: String) =
        game("FeedPet", obj("petItemId" to JsonPrimitive(petItemId), "cropItemId" to JsonPrimitive(cropItemId)))

    fun sellPet(itemId: String) =
        game("SellPet", obj("itemId" to JsonPrimitive(itemId)))

    fun upgradePetHutch() = game("UpgradePetHutch")

    fun namePet(petItemId: String, name: String) =
        game("NamePet", obj("petItemId" to JsonPrimitive(petItemId), "name" to JsonPrimitive(name)))

    fun swapPet(petSlotId: String, petInventoryId: String) =
        game("SwapPet", obj("petSlotId" to JsonPrimitive(petSlotId), "petInventoryId" to JsonPrimitive(petInventoryId)))

    fun swapPetFromStorage(petSlotId: String, storagePetId: String, storageId: String) =
        game("SwapPetFromStorage", obj(
            "petSlotId" to JsonPrimitive(petSlotId),
            "storagePetId" to JsonPrimitive(storagePetId),
            "storageId" to JsonPrimitive(storageId),
        ))

    fun movePetSlot(movePetSlotId: String, toPetSlotIndex: Int) =
        game("MovePetSlot", obj("movePetSlotId" to JsonPrimitive(movePetSlotId), "toPetSlotIndex" to JsonPrimitive(toPetSlotIndex)))

    fun petPositions(petPositions: JsonElement) =
        game("PetPositions", obj("petPositions" to petPositions))

    fun growEgg(slot: Int, eggId: String) =
        game("GrowEgg", obj("slot" to JsonPrimitive(slot), "eggId" to JsonPrimitive(eggId)))

    fun hatchEgg(slot: Int) =
        game("HatchEgg", obj("slot" to JsonPrimitive(slot)))

    // =====================
    // Inventory / Storage
    // =====================

    fun moveInventoryItem(moveItemId: String, toInventoryIndex: Int) =
        game("MoveInventoryItem", obj("moveItemId" to JsonPrimitive(moveItemId), "toInventoryIndex" to JsonPrimitive(toInventoryIndex)))

    fun setSelectedItem(itemIndex: Int) =
        game("SetSelectedItem", obj("itemIndex" to JsonPrimitive(itemIndex)))

    fun toggleLockItem(itemId: String) =
        game("ToggleLockItem", obj("itemId" to JsonPrimitive(itemId)))

    fun dropObject(slotIndex: Int) =
        game("DropObject", obj("slotIndex" to JsonPrimitive(slotIndex)))

    fun pickupObject(objectId: String) =
        game("PickupObject", obj("objectId" to JsonPrimitive(objectId)))

    fun putItemInStorage(itemId: String, storageId: String, toStorageIndex: Int? = null, quantity: Int? = null) {
        val params = buildJsonObject {
            put("itemId", JsonPrimitive(itemId))
            put("storageId", JsonPrimitive(storageId))
            if (toStorageIndex != null) put("toStorageIndex", JsonPrimitive(toStorageIndex))
            if (quantity != null) put("quantity", JsonPrimitive(quantity))
        }
        game("PutItemInStorage", params)
    }

    fun retrieveItemFromStorage(itemId: String, storageId: String, toInventoryIndex: Int? = null, quantity: Int? = null) {
        val params = buildJsonObject {
            put("itemId", JsonPrimitive(itemId))
            put("storageId", JsonPrimitive(storageId))
            if (toInventoryIndex != null) put("toInventoryIndex", JsonPrimitive(toInventoryIndex))
            if (quantity != null) put("quantity", JsonPrimitive(quantity))
        }
        game("RetrieveItemFromStorage", params)
    }

    fun moveStorageItem(itemId: String, storageId: String, toStorageIndex: Int) =
        game("MoveStorageItem", obj(
            "itemId" to JsonPrimitive(itemId),
            "storageId" to JsonPrimitive(storageId),
            "toStorageIndex" to JsonPrimitive(toStorageIndex),
        ))

    fun logItems() = game("LogItems")

    // =====================
    // Misc
    // =====================

    fun throwSnowball() = game("ThrowSnowball")

    fun checkFriendBonus() = game("CheckFriendBonus")

    // =====================
    // Helpers
    // =====================

    private fun position(x: Double, y: Double): JsonObject = buildJsonObject {
        put("x", JsonPrimitive(x))
        put("y", JsonPrimitive(y))
    }

    companion object {
        private const val GAME = Constants.GAME_NAME
        private val ROOM_SCOPE = listOf("Room")
        private val GAME_SCOPE = listOf("Room", GAME)
        private val EMPTY_OBJ = JsonObject(emptyMap())

        private fun obj(vararg pairs: Pair<String, JsonElement>): JsonObject =
            JsonObject(mapOf(*pairs))
    }
}
