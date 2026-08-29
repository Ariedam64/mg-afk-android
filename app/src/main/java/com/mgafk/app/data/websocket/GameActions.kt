package com.mgafk.app.data.websocket

import com.mgafk.app.data.repository.MgApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.util.UUID

/**
 * All game actions that can be sent via WebSocket.
 * Port of Websocket mg / actions.js (51+ actions)
 *
 * Usage:
 *   val actions = GameActions { text -> webSocket.send(text) }
 *   actions.chat("Hello!")
 *   actions.move(100.0, 200.0)
 */
class GameActions(
    private val sendFn: (String) -> Unit,
    private val sequencer: CommandSequencer = CommandSequencer(),
) {

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

    /**
     * Send a Quinoa action the way the game itself sends it: inside the
     * `QuinoaCommand` envelope, unless it is one of the [RAW_MESSAGE_TYPES]
     * the game still sends flat.
     *
     * The envelope is what feeds the server's prediction/rollback system, and
     * the flat form is on its way out - but only for the actions the game has
     * already migrated. Wrapping one it has not is a bet that the server
     * registered it as a command, and a losing bet fails silently.
     */
    private fun game(type: String, params: JsonObject = EMPTY_OBJ) =
        if (type in RAW_MESSAGE_TYPES) send(GAME_SCOPE, type, params)
        else quinoaCommand(type, params)

    /**
     * Wrap [type] in the envelope the server expects:
     * `{scopePath, type: "QuinoaCommand", requestId, commandSequence, command}`.
     *
     * Sending a command flat gets it rejected with
     * `{"type":"QuinoaCommandResult","commandType":"unknown","ok":false,"code":"invalid_message"}`
     * - the server can't even parse the command out, so the action silently
     * does nothing.
     *
     * See [CommandSequencer] for why the sequence number matters.
     */
    private fun quinoaCommand(type: String, params: JsonObject = EMPTY_OBJ) {
        val msg = buildJsonObject {
            put("scopePath", buildJsonArray { GAME_SCOPE.forEach { add(JsonPrimitive(it)) } })
            put("type", JsonPrimitive(COMMAND_ENVELOPE))
            put("requestId", JsonPrimitive(UUID.randomUUID().toString()))
            put("commandSequence", JsonPrimitive(sequencer.next()))
            put("command", buildJsonObject {
                put("type", JsonPrimitive(type))
                for ((k, v) in params) {
                    put(k, v)
                }
            })
        }
        sendFn(msg.toString())
    }

    // =====================
    // Session / Heartbeat
    // =====================

    fun ping(id: Long = System.currentTimeMillis()) =
        game("Ping", obj("id" to JsonPrimitive(id)))

    // The web client names this field `gameName`, not `gameId`.
    fun setSelectedGame(gameName: String = GAME) =
        room("SetSelectedGame", obj("gameName" to JsonPrimitive(gameName)))

    fun voteForGame(gameName: String = GAME) =
        room("VoteForGame", obj("gameName" to JsonPrimitive(gameName)))

    // Room-scoped, and it names the game to restart in `name` (not `gameName`).
    fun restartGame(gameName: String = GAME) =
        room("RestartGame", obj("name" to JsonPrimitive(gameName)))

    fun checkWeatherStatus() = game("CheckWeatherStatus")

    // =====================
    // Social / Chat
    // =====================

    fun chat(message: String) =
        room("Chat", obj("message" to JsonPrimitive(message)))

    fun emote(emoteType: String) =
        room("Emote", obj("emoteType" to JsonPrimitive(emoteType)))

    /** Throws a coin in the wishing well. [itemId] wishes for that specific item; the game
     * omits it entirely for an untargeted wish. */
    fun wish(itemId: String? = null) =
        game("Wish", if (itemId == null) EMPTY_OBJ else obj("itemId" to JsonPrimitive(itemId)))

    fun kickPlayer(targetPlayerId: String) =
        room("KickPlayer", obj("targetPlayerId" to JsonPrimitive(targetPlayerId)))

    fun setPlayerData(name: String? = null, cosmetic: JsonElement? = null) {
        val params = buildJsonObject {
            if (name != null) put("name", JsonPrimitive(name))
            if (cosmetic != null) put("cosmetic", cosmetic)
        }
        room("SetPlayerData", params)
    }

    fun usurpHost() = room("UsurpHost")

    fun markChatRead(seq: Int) =
        room("MarkChatRead", obj("seq" to JsonPrimitive(seq)))

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

    /**
     * Buy one shop item. Replaces the old PurchaseSeed / PurchaseTool /
     * PurchaseEgg / PurchaseDecor messages with the unified PurchaseShopItem
     * the game now uses since the v2.x server update.
     *
     * `shop` is the lowercase shop key from the ShopSnapshot ("seed", "tool",
     * "egg", "decor", "dawn", …) - passed through as-is.
     *
     * The item's `itemType` is derived from where the id appears in MgApi
     * data, NOT from the shop key. The "tool" shop now mixes Tool entries
     * (WateringCan, Shovel) with Decor entries (SeedSilo, FeedingTrough),
     * so we can't infer itemType from the shop name anymore.
     */
    fun purchaseShopItem(shop: String, itemId: String) {
        val itemType = when {
            MgApi.getPlants().containsKey(itemId) -> "Seed"
            MgApi.getItems().containsKey(itemId) -> "Tool"
            MgApi.getEggs().containsKey(itemId) -> "Egg"
            MgApi.getDecors().containsKey(itemId) -> "Decor"
            else -> return
        }
        val idField = when (itemType) {
            "Seed" -> "species"
            "Tool" -> "toolId"
            "Egg" -> "eggId"
            "Decor" -> "decorId"
            else -> return
        }
        val params = buildJsonObject {
            put("shop", JsonPrimitive(shop))
            put("item", buildJsonObject {
                put("itemType", JsonPrimitive(itemType))
                put(idField, JsonPrimitive(itemId))
            })
        }
        game("PurchaseShopItem", params)
    }

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

    /**
     * Pots the plant standing on [slot], moving it into the inventory as a Plant item.
     *
     * [plantItemId] is the id that new inventory item will carry: the client mints it and the
     * server honours it, which is what lets the caller reference the pot right away (to plant
     * it back with [plantGardenPlant], say) instead of waiting for the inventory patch. The
     * server rejects a PotPlant without it.
     */
    fun potPlant(slot: Int, plantItemId: String = UUID.randomUUID().toString()) =
        game("PotPlant", obj(
            "slot" to JsonPrimitive(slot),
            "plantItemId" to JsonPrimitive(plantItemId),
        ))

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

    /** Turns the harvested crop [itemId] into a preserve at the Preservation Station. */
    fun preserve(itemId: String, growSlotIdx: Int) =
        game("Preserve", obj("itemId" to JsonPrimitive(itemId), "growSlotIdx" to JsonPrimitive(growSlotIdx)))

    /** Puts a harvested crop on display on a garden or boardwalk tile. */
    fun displayCrop(tileType: String, localTileIndex: Int, itemId: String) =
        game("DisplayCrop", obj(
            "tileType" to JsonPrimitive(tileType),
            "localTileIndex" to JsonPrimitive(localTileIndex),
            "itemId" to JsonPrimitive(itemId),
        ))

    fun pickupDisplayedCrop(tileType: String, localTileIndex: Int) =
        game("PickupDisplayedCrop", obj(
            "tileType" to JsonPrimitive(tileType),
            "localTileIndex" to JsonPrimitive(localTileIndex),
        ))

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

    /** Consumes one Replenish Potion to fully restore [petItemId]'s hunger. Requires the
     * player to be standing on the pet's tile (see [teleport]). */
    fun useReplenishPotion(petItemId: String) =
        game("ReplenishPotion", obj("petItemId" to JsonPrimitive(petItemId)))

    fun sellPet(itemId: String) =
        game("SellPet", obj("itemId" to JsonPrimitive(itemId)))

    /** Mounts [petItemId] - required before [dawnCapture] (or any other rideable ability). */
    fun ridePet(petItemId: String) =
        game("RidePet", obj("petItemId" to JsonPrimitive(petItemId)))

    fun dismountPet() = game("DismountPet")

    /** Triggers the Ostrich's Dawn Capture ability at [x]/[y] (tile-grid coords). Requires
     * riding [petItemId] and being off cooldown. */
    fun dawnCapture(petItemId: String, x: Double, y: Double) =
        game("DawnCapture", obj(
            "petItemId" to JsonPrimitive(petItemId),
            "position" to position(x, y),
        ))

    /** Consumes one XP Potion to level [petItemId] up. */
    fun xpPotion(petItemId: String) =
        game("XPPotion", obj("petItemId" to JsonPrimitive(petItemId)))

    /** Triggers the Thundercharger's ability at [x]/[y] (tile-grid coords). Requires riding
     * [petItemId] and being off cooldown. */
    fun thundercharge(petItemId: String, x: Double, y: Double) =
        game("Thundercharge", obj(
            "petItemId" to JsonPrimitive(petItemId),
            "position" to position(x, y),
        ))

    /** Asks nearby pets to greet the player at [x]/[y]. */
    fun requestPetGreet(x: Double, y: Double) =
        game("RequestPetGreet", obj("position" to position(x, y)))

    fun equipPetCosmetic(petItemId: String, slotCategory: String, cosmeticId: String) =
        game("EquipPetCosmetic", obj(
            "petItemId" to JsonPrimitive(petItemId),
            "slotCategory" to JsonPrimitive(slotCategory),
            "cosmeticId" to JsonPrimitive(cosmeticId),
        ))

    fun upgradePetHutch() = game("UpgradePetHutch")

    fun upgradeSeedSilo() = game("UpgradeSeedSilo")

    fun upgradeDecorShed() = game("UpgradeDecorShed")

    fun upgradeToolShack() = game("UpgradeToolShack")

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

    fun growEgg(slot: Int, eggId: String) =
        game("GrowEgg", obj("slot" to JsonPrimitive(slot), "eggId" to JsonPrimitive(eggId)))

    fun hatchEgg(slot: Int) =
        game("HatchEgg", obj("slot" to JsonPrimitive(slot)))

    // =====================
    // Pet teams
    // =====================

    /** [isCreate] tells the server this is a brand new team rather than an edit of [teamId]. */
    fun savePetTeam(teamId: String, name: String, petIds: List<String>, isCreate: Boolean) =
        game("SavePetTeam", obj(
            "teamId" to JsonPrimitive(teamId),
            "isCreate" to JsonPrimitive(isCreate),
            "name" to JsonPrimitive(name),
            "petIds" to buildJsonArray { petIds.forEach { add(JsonPrimitive(it)) } },
        ))

    fun applyPetTeam(teamId: String) =
        game("ApplyPetTeam", obj("teamId" to JsonPrimitive(teamId)))

    fun deletePetTeam(teamId: String) =
        game("DeletePetTeam", obj("teamId" to JsonPrimitive(teamId)))

    fun movePetTeam(movePetTeamId: String, toPetTeamIndex: Int) =
        game("MovePetTeam", obj(
            "movePetTeamId" to JsonPrimitive(movePetTeamId),
            "toPetTeamIndex" to JsonPrimitive(toPetTeamIndex),
        ))

    fun setPetTeamEmblem(teamId: String, emblem: String) =
        game("SetPetTeamEmblem", obj("teamId" to JsonPrimitive(teamId), "emblem" to JsonPrimitive(emblem)))

    // =====================
    // Inventory / Storage
    // =====================

    fun moveInventoryItem(moveItemId: String, toInventoryIndex: Int) =
        game("MoveInventoryItem", obj("moveItemId" to JsonPrimitive(moveItemId), "toInventoryIndex" to JsonPrimitive(toInventoryIndex)))

    fun setSelectedItem(itemIndex: Int) =
        game("SetSelectedItem", obj("itemIndex" to JsonPrimitive(itemIndex)))

    fun toggleLockItem(itemId: String) =
        game("ToggleLockItem", obj("itemId" to JsonPrimitive(itemId)))

    // Both act on whatever the player is currently holding or standing on, so
    // the game sends them without any parameter - an extra field would only get
    // the command rejected as malformed.
    fun dropObject() = game("DropObject")

    fun pickupObject() = game("PickupObject")

    // toStorageIndex and quantity are both optional on the wire: the game leaves the index out
    // when the item goes to the end of the storage, and only sends a quantity when moving part
    // of a stack.
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

    /**
     * Exchanges an inventory item for a stored one in a single action, which keeps both
     * capacities unchanged (unlike a retrieve followed by a put).
     *
     * [draggedQuantity] splits a stack: the game only sends it when the player drags part of
     * one, and always alongside [draggedFromInventory] (which side the drag started on).
     */
    fun swapItemWithStorage(
        storageId: String,
        inventoryItemId: String,
        storageItemId: String,
        toStorageIndex: Int? = null,
        toInventoryIndex: Int? = null,
        draggedQuantity: Int? = null,
        draggedFromInventory: Boolean = false,
    ) {
        val params = buildJsonObject {
            put("storageId", JsonPrimitive(storageId))
            put("inventoryItemId", JsonPrimitive(inventoryItemId))
            put("storageItemId", JsonPrimitive(storageItemId))
            if (toStorageIndex != null) put("toStorageIndex", JsonPrimitive(toStorageIndex))
            if (toInventoryIndex != null) put("toInventoryIndex", JsonPrimitive(toInventoryIndex))
            if (draggedQuantity != null) {
                put("draggedQuantity", JsonPrimitive(draggedQuantity))
                put("draggedFromInventory", JsonPrimitive(draggedFromInventory))
            }
        }
        game("SwapItemWithStorage", params)
    }

    fun logItems() = game("LogItems")

    // =====================
    // Misc
    // =====================

    fun throwSnowball() = game("ThrowSnowball")

    fun checkFriendBonus() = game("CheckFriendBonus")

    fun quinoaTutorialSkipped() = game("QuinoaTutorialSkipped")

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

        /** Envelope `type` for the commands that go through [quinoaCommand]. */
        private const val COMMAND_ENVELOPE = "QuinoaCommand"

        /**
         * Quinoa messages the game still sends flat, as of client version 1029.
         *
         * `Ping` answers Pong and `PlayerPosition` feeds the movement snapshot channel, so
         * those two are not commands by nature; the rest simply have not been migrated yet.
         * The game is moving them across one release at a time, so re-derive this list from
         * the bundle when a newly muted action shows up: the flat sender is
         * `sendMessage({scopePath:["Room","Quinoa"], ...msg})`, the command sender wraps the
         * message in a `QuinoaCommand` envelope.
         */
        private val RAW_MESSAGE_TYPES = setOf(
            "Ping",
            "PlayerPosition",
            "Teleport",
            "SetSelectedItem",
            "CheckWeatherStatus",
            "CheckFriendBonus",
            "ThrowSnowball",
            "QuinoaTutorialSkipped",
            "RequestPetGreet",
            "DropObject",
            "PickupObject",
            "UpgradePetHutch",
            "UpgradeSeedSilo",
            "UpgradeDecorShed",
        )

        private fun obj(vararg pairs: Pair<String, JsonElement>): JsonObject =
            JsonObject(mapOf(*pairs))
    }
}
