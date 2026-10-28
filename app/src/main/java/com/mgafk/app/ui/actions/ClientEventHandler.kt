package com.mgafk.app.ui.actions

import com.mgafk.app.data.AppLog
import com.mgafk.app.data.model.GardenEggSnapshot
import com.mgafk.app.data.model.GardenPlantSnapshot
import com.mgafk.app.data.model.InventoryCropsItem
import com.mgafk.app.data.model.InventoryDecorItem
import com.mgafk.app.data.model.InventoryEggItem
import com.mgafk.app.data.model.InventoryPetItem
import com.mgafk.app.data.model.InventoryPlantItem
import com.mgafk.app.data.model.InventoryProduceItem
import com.mgafk.app.data.model.InventorySeedItem
import com.mgafk.app.data.model.InventorySnapshot
import com.mgafk.app.data.model.InventoryToolItem
import com.mgafk.app.data.model.PetSnapshot
import com.mgafk.app.data.model.SessionStatus
import com.mgafk.app.data.model.ShopSnapshot
import com.mgafk.app.data.repository.CropSize
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.data.websocket.ClientEvent
import com.mgafk.app.service.cancelResumeNotification
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A shop deadline may shift later by a hair on its own; only a jump past this is a restock. */
private const val RESTOCK_COUNTDOWN_JITTER_SEC = 30

/**
 * Turns what a session's [com.mgafk.app.data.websocket.RoomClient] reports into session state,
 * and fires the alerts that depend on it.
 *
 * A server update also confirms the optimistic changes the action classes made, so the
 * matching rollbacks are cancelled here. [collectStateNow] is the ViewModel's, run as soon as
 * a session connects.
 */
class ClientEventHandler(
    private val ctx: SessionContext,
    private val shopActions: ShopActions,
    private val petActions: PetActions,
    private val gardenActions: GardenActions,
    private val storageActions: StorageActions,
    private val collectStateNow: (String) -> Unit,
) {
    fun handle(sessionId: String, event: ClientEvent) {
        when (event) {
            is ClientEvent.StatusChanged -> {
                val logDetail = buildString {
                    if (event.code != null) append("code=${event.code}")
                    if (event.message.isNotBlank()) {
                        if (isNotEmpty()) append(" ")
                        append(event.message)
                    }
                    if (event.retry > 0) append(" retry=${event.retry}/${event.maxRetries}")
                    if (event.retryInMs > 0) append(" delay=${event.retryInMs}ms")
                }
                val wsLog = com.mgafk.app.data.model.WsLog(
                    timestamp = System.currentTimeMillis(),
                    level = if (event.status == SessionStatus.ERROR) "error" else "info",
                    event = "status → ${event.status.name}",
                    detail = logDetail,
                )
                val previousSession = ctx.session(sessionId)
                val wasConnected = previousSession?.connected == true
                ctx.updateSession(sessionId) {
                    it.copy(
                        status = event.status,
                        connected = event.status == SessionStatus.CONNECTED,
                        busy = event.status == SessionStatus.CONNECTING,
                        error = event.message,
                        playerId = event.playerId.ifBlank { it.playerId },
                        room = event.room.ifBlank { it.room },
                        connectedAt = if (event.status == SessionStatus.CONNECTED) System.currentTimeMillis() else 0,
                        wsLogs = (listOf(wsLog) + it.wsLogs).take(100),
                    )
                }
                if (event.status == SessionStatus.CONNECTED) {
                    // A session is live again (user tap, auto-reconnect, or WS
                    // self-reconnect): clear any stale "tap to resume" notif.
                    cancelResumeNotification(ctx.app)
                    // Report online immediately rather than waiting for the
                    // next minute tick, so the backend marks the player online.
                    collectStateNow(sessionId)
                }
                // Disconnect / reconnect notifications
                if (ctx.state.value.settings.notifyOnDisconnect && previousSession != null) {
                    val name = previousSession.name
                    if (wasConnected && event.status != SessionStatus.CONNECTED) {
                        ctx.alertNotifier.notifyDisconnect(name, event.code, event.message.ifBlank { "Connection lost" })
                    } else if (event.status == SessionStatus.CONNECTED) {
                        ctx.alertNotifier.cancelDisconnectNotification(name)
                    }
                }
            }
            is ClientEvent.PlayersChanged -> {
                ctx.updateSession(sessionId) { it.copy(players = event.count) }
            }
            is ClientEvent.UptimeChanged -> { /* computed locally in UI */ }
            is ClientEvent.AbilityLogged -> {
                val current = ctx.session(sessionId)
                val isDuplicate = current?.logs?.any { existing ->
                    existing.timestamp == event.log.timestamp && existing.action == event.log.action
                } == true
                if (!isDuplicate) {
                    ctx.updateSession(sessionId) {
                        it.copy(logs = (listOf(event.log) + it.logs).take(200))
                    }
                    // Notify if this ability proc is enabled in the alert config.
                    ctx.alertNotifier.checkAbilityProc(event.log, ctx.state.value.alerts)
                }
            }
            is ClientEvent.LiveStatusChanged -> {
                val previousSession = ctx.session(sessionId)
                val previousWeather = previousSession?.weather.orEmpty()
                val newPets = event.pets.map { pet ->
                    PetSnapshot(
                        id = pet.id,
                        name = pet.name,
                        species = pet.species,
                        hunger = pet.hunger,
                        index = pet.index,
                        mutations = pet.mutations,
                        xp = pet.xp,
                        targetScale = pet.targetScale,
                        abilities = pet.abilities,
                    )
                }
                ctx.updateSession(sessionId) {
                    it.copy(
                        playerName = event.playerName,
                        roomId = event.roomId,
                        hostPlayerId = event.hostPlayerId,
                        weather = event.weather,
                        pets = newPets,
                    )
                }
                // Fire alert checks (alarm items auto-batch within 300ms)
                val alerts = ctx.state.value.alerts
                ctx.alertNotifier.checkWeather(event.weather, previousWeather, alerts)
                ctx.alertNotifier.checkPetHunger(sessionId, newPets, alerts)
            }
            is ClientEvent.PetTeamsChanged -> {
                ctx.updateSession(sessionId) { it.copy(petTeams = event.teams) }
            }
            is ClientEvent.GardenChanged -> {
                val newGarden = mutableListOf<GardenPlantSnapshot>()
                for (tile in event.plants) {
                    val data = tile.data
                    val slots = data["slots"] as? JsonArray ?: continue
                    for ((index, slotEl) in slots.withIndex()) {
                        val slot = slotEl as? JsonObject ?: continue
                        // Prefer the slot's own slotId - that's what the server
                        // expects back in HarvestCrop.slotsIndex and
                        // CropCleanser.growSlotIdx. Fall back to the array
                        // position only if the field is missing.
                        val slotId = slot["slotId"]?.jsonPrimitive?.intOrNull ?: index
                        val species = slot["species"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val mutations = mutableListOf<String>()
                        (slot["mutations"] as? JsonArray)?.forEach { m ->
                            val name = m.jsonPrimitive.contentOrNull
                            if (!name.isNullOrBlank()) mutations.add(name)
                        }
                        newGarden += GardenPlantSnapshot(
                            tileId = tile.tileId,
                            slotIndex = slotId,
                            species = species,
                            size = CropSize.clamp(slot["size"]?.jsonPrimitive?.doubleOrNull ?: 0.0),
                            mutations = mutations,
                            startTime = slot["startTime"]?.jsonPrimitive?.longOrNull ?: 0L,
                            endTime = slot["endTime"]?.jsonPrimitive?.longOrNull ?: 0L,
                            preserved = slot["preserved"]?.jsonPrimitive?.booleanOrNull == true,
                        )
                    }
                }
                // Server confirmed garden change - cancel pending plant/water rollbacks
                gardenActions.onGardenConfirmed(sessionId)
                val freeTiles = ctx.clients[sessionId]?.let { gardenActions.computeFreePlantTileCount(it) } ?: 0
                ctx.updateSession(sessionId) { it.copy(garden = newGarden, freePlantTiles = freeTiles) }
            }
            is ClientEvent.CrystalsChanged -> {
                // Not persisted: the server reports it again on every reconnect, and it changes
                // as often as a crystal burns down.
                ctx.updateSessionInMemory(sessionId) {
                    it.copy(
                        crystals = event.crystals,
                        occupiedTiles = event.occupiedTiles,
                        crystalsReadAtMs = System.currentTimeMillis(),
                    )
                }
            }
            is ClientEvent.InventoryChanged -> {
                val seeds = mutableListOf<InventorySeedItem>()
                val eggs = mutableListOf<InventoryEggItem>()
                val produce = mutableListOf<InventoryProduceItem>()
                val plants = mutableListOf<InventoryPlantItem>()
                val pets = mutableListOf<InventoryPetItem>()
                val tools = mutableListOf<InventoryToolItem>()
                val decors = mutableListOf<InventoryDecorItem>()

                for (el in event.items) {
                    val obj = el as? JsonObject ?: continue
                    when (obj["itemType"]?.jsonPrimitive?.contentOrNull) {
                        "Seed" -> seeds.add(InventorySeedItem(
                            species = obj["species"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            quantity = obj["quantity"]?.jsonPrimitive?.intOrNull ?: 1,
                        ))
                        "Egg" -> eggs.add(InventoryEggItem(
                            eggId = obj["eggId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            quantity = obj["quantity"]?.jsonPrimitive?.intOrNull ?: 1,
                        ))
                        "Produce" -> produce.add(InventoryProduceItem(
                            id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            species = obj["species"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            size = CropSize.clamp(obj["size"]?.jsonPrimitive?.doubleOrNull ?: 0.0),
                            mutations = (obj["mutations"] as? JsonArray)
                                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                                ?.filter { it.isNotBlank() } ?: emptyList(),
                        ))
                        "Plant" -> {
                            val slotsArray = obj["slots"] as? JsonArray
                            val plantSpecies = obj["species"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            var plantPrice = 0L
                            val parsedSlots = mutableListOf<com.mgafk.app.data.model.InventoryPlantSlot>()
                            slotsArray?.forEach { slotEl ->
                                val slot = slotEl as? JsonObject ?: return@forEach
                                val slotSpecies = slot["species"]?.jsonPrimitive?.contentOrNull
                                    ?: plantSpecies
                                val size = CropSize.clamp(slot["size"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                                val muts = (slot["mutations"] as? JsonArray)
                                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                                    ?.filter { it.isNotBlank() } ?: emptyList()
                                plantPrice += PriceCalculator.calculateCropSellPrice(slotSpecies, size, muts) ?: 0L
                                parsedSlots.add(com.mgafk.app.data.model.InventoryPlantSlot(
                                    species = slotSpecies,
                                    size = size,
                                    mutations = muts,
                                ))
                            }
                            plants.add(InventoryPlantItem(
                                id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                species = plantSpecies,
                                growSlots = slotsArray?.size ?: 0,
                                totalPrice = plantPrice,
                                slots = parsedSlots,
                            ))
                        }
                        "Pet" -> pets.add(InventoryPetItem(
                            id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            petSpecies = obj["petSpecies"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            name = obj["name"]?.jsonPrimitive?.contentOrNull,
                            xp = obj["xp"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                            targetScale = obj["targetScale"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                            mutations = (obj["mutations"] as? JsonArray)
                                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                                ?.filter { it.isNotBlank() } ?: emptyList(),
                            abilities = (obj["abilities"] as? JsonArray)
                                ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                            sourceEggId = obj["sourceEggId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        ))
                        "Tool" -> tools.add(InventoryToolItem(
                            toolId = obj["toolId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            quantity = obj["quantity"]?.jsonPrimitive?.intOrNull ?: 1,
                            id = obj["id"]?.jsonPrimitive?.contentOrNull,
                            remainingActiveSeconds = obj["remainingActiveSeconds"]?.jsonPrimitive?.intOrNull,
                        ))
                        "Decor" -> decors.add(InventoryDecorItem(
                            decorId = obj["decorId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            quantity = obj["quantity"]?.jsonPrimitive?.intOrNull ?: 1,
                        ))
                    }
                }
                // Parse storages separately. Seed each capacity from the session's last
                // known value (not the base default) - some InventoryChanged events only
                // carry the storages that actually changed (e.g. DecorShed is rarely
                // touched vs. the constantly-churning Silo/Hutch), so a storage missing
                // from *this* event's list must keep its previously known capacity
                // instead of being reported back down to base.
                val existingSession = ctx.session(sessionId)
                val siloSeeds = mutableListOf<InventorySeedItem>()
                val shedDecors = mutableListOf<InventoryDecorItem>()
                val hutchPets = mutableListOf<InventoryPetItem>()
                val troughCrops = mutableListOf<InventoryCropsItem>()
                val shackTools = mutableListOf<InventoryToolItem>()
                var hutchCapacitySlots = existingSession?.hutchCapacitySlots ?: PriceCalculator.HUTCH_BASE_CAPACITY
                var siloCapacitySlots = existingSession?.siloCapacitySlots ?: PriceCalculator.SILO_BASE_CAPACITY
                var decorShedCapacitySlots = existingSession?.decorShedCapacitySlots ?: PriceCalculator.DECOR_SHED_BASE_CAPACITY
                var toolShackCapacitySlots = existingSession?.toolShackCapacitySlots ?: PriceCalculator.TOOL_SHACK_BASE_CAPACITY
                // Owned storages never go away - only grow this set, don't reset it, so a
                // partial event doesn't make an already-purchased storage look unowned.
                val availableStorages = existingSession?.availableStorages?.toMutableSet() ?: mutableSetOf()

                for (storageEl in event.storages) {
                    val storage = storageEl as? JsonObject ?: continue
                    val storageId = storage["decorId"]?.jsonPrimitive?.contentOrNull ?: continue
                    availableStorages.add(storageId)
                    // Game now sends the capacity directly as "capacitySlots";
                    // fall back to the legacy "capacityLevel" tier if absent.
                    val slots = storage["capacitySlots"]?.jsonPrimitive?.intOrNull
                    val legacyLevel = storage["capacityLevel"]?.jsonPrimitive?.intOrNull ?: 0
                    when (storageId) {
                        "PetHutch" -> hutchCapacitySlots =
                            slots ?: PriceCalculator.calculateHutchCapacity(legacyLevel)
                        "SeedSilo" -> siloCapacitySlots =
                            slots ?: PriceCalculator.calculateSiloCapacity(legacyLevel)
                        "DecorShed" -> decorShedCapacitySlots =
                            slots ?: PriceCalculator.calculateDecorShedCapacity(legacyLevel)
                        "ToolShack" -> toolShackCapacitySlots =
                            slots ?: PriceCalculator.calculateToolShackCapacity(legacyLevel)
                    }
                    val storageItems = storage["items"] as? JsonArray ?: continue
                    for (el in storageItems) {
                        val obj = el as? JsonObject ?: continue
                        when (storageId) {
                            "SeedSilo" -> siloSeeds.add(InventorySeedItem(
                                species = obj["species"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                quantity = obj["quantity"]?.jsonPrimitive?.intOrNull ?: 1,
                            ))
                            "DecorShed" -> shedDecors.add(InventoryDecorItem(
                                decorId = obj["decorId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                quantity = obj["quantity"]?.jsonPrimitive?.intOrNull ?: 1,
                            ))
                            "PetHutch" -> hutchPets.add(InventoryPetItem(
                                id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                petSpecies = obj["petSpecies"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                name = obj["name"]?.jsonPrimitive?.contentOrNull,
                                xp = obj["xp"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                                targetScale = obj["targetScale"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                                mutations = (obj["mutations"] as? JsonArray)
                                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                                    ?.filter { it.isNotBlank() } ?: emptyList(),
                                abilities = (obj["abilities"] as? JsonArray)
                                    ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                                sourceEggId = obj["sourceEggId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            ))
                            "ToolShack" -> shackTools.add(InventoryToolItem(
                                toolId = obj["toolId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                quantity = obj["quantity"]?.jsonPrimitive?.intOrNull ?: 1,
                            ))
                            "FeedingTrough" -> troughCrops.add(InventoryCropsItem(
                                id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                species = obj["species"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                size = CropSize.clamp(obj["size"]?.jsonPrimitive?.doubleOrNull ?: 0.0),
                                mutations = (obj["mutations"] as? JsonArray)
                                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                                    ?.filter { it.isNotBlank() } ?: emptyList(),
                            ))
                        }
                    }
                }
                // Server confirmed - cancel any pending rollbacks
                petActions.onInventoryConfirmed()
                gardenActions.onInventoryConfirmed(sessionId)

                // Detect newly hatched pet
                val previousPetIds = gardenActions.takePreHatchPetIds(sessionId)
                val allNewPets = pets + hutchPets
                val hatchedPet = if (previousPetIds != null) {
                    allNewPets.firstOrNull { it.id !in previousPetIds }
                } else null

                AppLog.d(LOG_TAG, "[Storage] availableStorages=$availableStorages hutch=$hutchCapacitySlots silo=$siloCapacitySlots decorShed=$decorShedCapacitySlots toolShack=$toolShackCapacitySlots")

                ctx.updateSession(sessionId) {
                    it.copy(
                        inventory = InventorySnapshot(seeds, eggs, produce, plants, pets, tools, decors),
                        seedSilo = siloSeeds,
                        decorShed = shedDecors,
                        petHutch = hutchPets,
                        feedingTrough = troughCrops,
                        toolShack = shackTools,
                        favoritedItemIds = event.favoritedItemIds.toSet(),
                        lastHatchedPet = hatchedPet ?: it.lastHatchedPet,
                        magicDust = event.magicDust,
                        hutchCapacitySlots = hutchCapacitySlots,
                        siloCapacitySlots = siloCapacitySlots,
                        decorShedCapacitySlots = decorShedCapacitySlots,
                        toolShackCapacitySlots = toolShackCapacitySlots,
                        availableStorages = availableStorages,
                    )
                }
                petActions.scheduleTroughAlertCheck(sessionId)
                storageActions.runAutoStock(sessionId, seeds, decors, tools, siloSeeds, shedDecors, shackTools, availableStorages)
            }
            is ClientEvent.EggsChanged -> {
                val newEggs = event.eggs.map { tile ->
                    val data = tile.data
                    GardenEggSnapshot(
                        tileId = tile.tileId,
                        eggId = data["eggId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        plantedAt = data["plantedAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                        maturedAt = data["maturedAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                    )
                }
                // Server confirmed egg change - cancel pending hatch/grow rollbacks
                gardenActions.onEggsConfirmed(sessionId)
                val freeTiles = ctx.clients[sessionId]?.let { gardenActions.computeFreePlantTileCount(it) } ?: 0
                ctx.updateSession(sessionId) { it.copy(gardenEggs = newEggs, freePlantTiles = freeTiles) }
            }
            is ClientEvent.ShopsChanged -> {
                val previousShops = ctx.session(sessionId)?.shops.orEmpty()
                val purchases = event.shopPurchases
                // Detect shop restock: the deadline only ever moves LATER when the shop rolled a
                // new stock. Right then shopPurchases may be stale (its reset arrives in a separate
                // patch), and the alert dedup has to start a fresh cycle.
                val restockedTypes = event.shops.filter { shop ->
                    val prevShop = previousShops.find { it.type == shop.type } ?: return@filter false
                    shop.deadlineMs > prevShop.deadlineMs + RESTOCK_COUNTDOWN_JITTER_SEC * 1000L
                }.map { it.type }.toSet()
                val newShops = event.shops.map { shop ->
                    val initialStocks = shop.getItemStocks()
                    val purchaseMap = if (shop.type in restockedTypes) null
                        else shop.purchasesThisRestock(purchases?.get(shop.type) as? JsonObject)
                    val remainingStocks = initialStocks.mapValues { (name, initial) ->
                        val bought = purchaseMap?.get(name)?.jsonPrimitive?.intOrNull ?: 0
                        maxOf(0, initial - bought)
                    }
                    ShopSnapshot(
                        type = shop.type,
                        itemNames = shop.getItemNames(),
                        itemStocks = remainingStocks,
                        initialStocks = initialStocks,
                        restockId = shop.restockId,
                        deadlineMs = shop.deadlineMs,
                    )
                }
                // Server confirmed - cancel any pending rollback jobs for this session
                shopActions.cancelPendingPurchases(sessionId)
                ctx.updateSession(sessionId) { it.copy(shops = newShops) }
                // Only check alerts when actual items changed, not just the restock timer. A
                // restock counts as a change even when it rolled the same items: the stock behind
                // them is new, so it has to alert again.
                val oldItems = previousShops.associate { it.type to it.itemNames }
                val newItems = newShops.associate { it.type to it.itemNames }
                if (oldItems != newItems || restockedTypes.isNotEmpty()) {
                    ctx.alertNotifier.checkShopItems(newShops, ctx.state.value.alerts, restockedTypes)
                }
            }
            is ClientEvent.ChatChanged -> {
                ctx.updateSession(sessionId) { it.copy(chatMessages = event.messages) }
            }
            is ClientEvent.PlayersListChanged -> {
                ctx.updateSession(sessionId) { it.copy(playersList = event.players) }
            }
            is ClientEvent.DebugLog -> {
                ctx.updateSession(sessionId) {
                    val entry = com.mgafk.app.data.model.WsLog(
                        timestamp = System.currentTimeMillis(),
                        level = event.level,
                        event = event.message,
                        detail = event.detail,
                    )
                    it.copy(wsLogs = (listOf(entry) + it.wsLogs).take(100))
                }
            }
        }
    }
}
