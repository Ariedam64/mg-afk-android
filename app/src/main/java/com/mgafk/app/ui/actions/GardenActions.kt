package com.mgafk.app.ui.actions

import com.mgafk.app.data.AppLog
import com.mgafk.app.data.model.CrystalType
import com.mgafk.app.data.model.GardenTileType
import com.mgafk.app.data.model.PlacedCrystal
import com.mgafk.app.data.repository.Crystals
import com.mgafk.app.data.repository.GardenTiles
import com.mgafk.app.data.websocket.RoomClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The garden: crops, eggs, potted plants and crystals.
 *
 * Most actions update the session at once and roll it back after 5s unless the server
 * confirms. The event handler reports those confirmations through the on...Confirmed
 * functions at the bottom.
 */
class GardenActions(private val ctx: SessionContext) {
    // ---- Garden ----

    fun harvestCrop(sessionId: String, slot: Int, slotIndex: Int) {
        ctx.actions(sessionId)?.harvestCrop(slot = slot, slotsIndex = slotIndex)
    }

    private val pendingCleanseJobs = mutableMapOf<String, Job>()

    /** Cleanse one mutation from a crop slot. Requires a CropCleanser tool. */
    fun cropCleanse(sessionId: String, tileObjectIdx: Int, growSlotIdx: Int) {
        val actions = ctx.actions(sessionId) ?: return
        val session = ctx.session(sessionId) ?: return
        val count = session.inventory.tools.find { it.toolId == "CropCleanser" }?.quantity ?: 0
        if (count <= 0) return

        // OPTIMISTIC: decrement CropCleanser quantity
        val previousInventory = session.inventory
        ctx.updateSession(sessionId) { s ->
            s.copy(
                inventory = s.inventory.copy(
                    tools = s.inventory.tools.mapNotNull { tool ->
                        if (tool.toolId == "CropCleanser") {
                            if (tool.quantity > 1) tool.copy(quantity = tool.quantity - 1) else null
                        } else tool
                    }
                ),
            )
        }

        actions.cropCleanser(tileObjectIdx = tileObjectIdx, growSlotIdx = growSlotIdx)

        // Rollback after 5s if server hasn't confirmed
        val key = "$sessionId:cleanse:$tileObjectIdx:$growSlotIdx"
        pendingCleanseJobs[key]?.cancel()
        pendingCleanseJobs[key] = ctx.scope.launch {
            delay(5000)
            ctx.updateSession(sessionId) { s -> s.copy(inventory = previousInventory) }
            pendingCleanseJobs.remove(key)
        }
    }

    private val pendingPotJobs = mutableMapOf<String, Job>()

    /** Pot a plant - moves it from garden to inventory. Requires a PlanterPot tool. */
    fun potPlant(sessionId: String, slot: Int) {
        val actions = ctx.actions(sessionId) ?: return
        val session = ctx.session(sessionId) ?: return
        val potCount = session.inventory.tools.find { it.toolId == "PlanterPot" }?.quantity ?: 0
        if (potCount <= 0) return

        // OPTIMISTIC: decrement PlanterPot quantity
        val previousInventory = session.inventory
        ctx.updateSession(sessionId) { s ->
            s.copy(
                inventory = s.inventory.copy(
                    tools = s.inventory.tools.mapNotNull { tool ->
                        if (tool.toolId == "PlanterPot") {
                            if (tool.quantity > 1) tool.copy(quantity = tool.quantity - 1) else null
                        } else tool
                    }
                ),
            )
        }

        actions.potPlant(slot = slot)

        // Rollback after 5s if server hasn't confirmed
        val key = "$sessionId:pot:$slot"
        pendingPotJobs[key]?.cancel()
        pendingPotJobs[key] = ctx.scope.launch {
            delay(5000)
            ctx.updateSession(sessionId) { s -> s.copy(inventory = previousInventory) }
            pendingPotJobs.remove(key)
        }
    }

    private val pendingWaterJobs = mutableMapOf<String, Job>()

    /** Water a plant with optimistic update (decrements WateringCan count). */
    fun waterPlant(sessionId: String, slot: Int) {
        val actions = ctx.actions(sessionId) ?: return
        val session = ctx.session(sessionId) ?: return
        val canCount = session.inventory.tools.find { it.toolId == "WateringCan" }?.quantity ?: 0
        if (canCount <= 0) return

        // OPTIMISTIC: decrement watering can quantity + reduce endTime by 5min on the tile
        val previousInventory = session.inventory
        val previousGarden = session.garden
        val waterReduceMs = 5 * 60 * 1000L // 5 minutes
        ctx.updateSession(sessionId) { s ->
            s.copy(
                inventory = s.inventory.copy(
                    tools = s.inventory.tools.mapNotNull { tool ->
                        if (tool.toolId == "WateringCan") {
                            if (tool.quantity > 1) tool.copy(quantity = tool.quantity - 1) else null
                        } else tool
                    }
                ),
                garden = s.garden.map { plant ->
                    if (plant.tileId == slot) {
                        plant.copy(endTime = (plant.endTime - waterReduceMs).coerceAtLeast(plant.startTime))
                    } else plant
                },
            )
        }

        actions.waterPlant(slot = slot)

        // Rollback after 5s if server hasn't confirmed
        val key = "$sessionId:water:$slot"
        pendingWaterJobs[key]?.cancel()
        pendingWaterJobs[key] = ctx.scope.launch {
            delay(5000)
            ctx.updateSession(sessionId) { s -> s.copy(inventory = previousInventory, garden = previousGarden) }
            pendingWaterJobs.remove(key)
        }
    }

    // Track pet IDs before hatch to detect the new pet in InventoryChanged
    private val preHatchPetIds = mutableMapOf<String, Set<String>>() // sessionId -> pet IDs before hatch
    private val pendingHatchJobs = mutableMapOf<String, Job>()

    /** Hatch a mature egg, optimistically remove it from garden eggs. */
    fun hatchEgg(sessionId: String, slot: Int) {
        val actions = ctx.actions(sessionId) ?: return
        val session = ctx.session(sessionId) ?: return

        // Save current pet IDs to detect the new one later
        val currentPetIds = (session.inventory.pets.map { it.id } + session.petHutch.map { it.id }).toSet()
        preHatchPetIds[sessionId] = currentPetIds

        // Save egg ID for the hatch animation
        val eggId = session.gardenEggs.find { it.tileId == slot }?.eggId.orEmpty()

        // OPTIMISTIC: remove egg from gardenEggs, store eggId for animation
        val previousEggs = session.gardenEggs
        ctx.updateSession(sessionId) { s ->
            s.copy(
                gardenEggs = s.gardenEggs.filter { it.tileId != slot },
                lastHatchedEggId = eggId,
            )
        }

        actions.hatchEgg(slot = slot)

        // Rollback after 5s if server hasn't confirmed
        val key = "$sessionId:hatch:$slot"
        pendingHatchJobs[key]?.cancel()
        pendingHatchJobs[key] = ctx.scope.launch {
            delay(5000)
            ctx.updateSession(sessionId) { s -> s.copy(gardenEggs = previousEggs) }
            pendingHatchJobs.remove(key)
            preHatchPetIds.remove(sessionId)
        }
    }

    private val pendingGrowEggJobs = mutableMapOf<String, Job>()

    /** Grow an egg on the first available dirt tile with optimistic update. */
    fun growEgg(sessionId: String, eggId: String) {
        val client = ctx.clients[sessionId] ?: return
        val session = ctx.session(sessionId) ?: return
        val freeSlot = findFirstFreePlantTile(client)
        if (freeSlot == null) {
            AppLog.w(LOG_TAG, "[GrowEgg] No free tile available")
            return
        }
        AppLog.d(LOG_TAG, "[GrowEgg] Growing $eggId on tile $freeSlot")

        // OPTIMISTIC: decrement egg quantity + decrement free tiles
        val previousInventory = session.inventory
        val previousFreeTiles = session.freePlantTiles
        ctx.updateSession(sessionId) { s ->
            s.copy(
                inventory = s.inventory.copy(
                    eggs = s.inventory.eggs.mapNotNull { egg ->
                        if (egg.eggId == eggId) {
                            if (egg.quantity > 1) egg.copy(quantity = egg.quantity - 1) else null
                        } else egg
                    }
                ),
                freePlantTiles = (s.freePlantTiles - 1).coerceAtLeast(0),
            )
        }

        client.actions.growEgg(slot = freeSlot, eggId = eggId)

        // Rollback after 5s if server hasn't confirmed
        val key = "$sessionId:growEgg:$eggId:$freeSlot"
        pendingGrowEggJobs[key]?.cancel()
        pendingGrowEggJobs[key] = ctx.scope.launch {
            delay(5000)
            ctx.updateSession(sessionId) { s ->
                s.copy(inventory = previousInventory, freePlantTiles = previousFreeTiles)
            }
            pendingGrowEggJobs.remove(key)
        }
    }

    /** Clear the last hatched pet result (called from UI after showing the popup). */
    fun clearHatchedPet(sessionId: String) {
        ctx.updateSession(sessionId) { it.copy(lastHatchedPet = null, lastHatchedEggId = "") }
    }

    private val pendingUnpotJobs = mutableMapOf<String, Job>()

    /**
     * Unpots [itemId] onto [tileId], or onto the first free tile when it is null (see
     * [com.mgafk.app.data.model.PlantPlacementMode]).
     */
    fun plantGardenPlant(sessionId: String, itemId: String, tileId: Int? = null) {
        val client = ctx.clients[sessionId] ?: return
        val session = ctx.session(sessionId) ?: return
        val freeSlot = tileId ?: findFirstFreePlantTile(client)
        if (freeSlot == null) {
            AppLog.w(LOG_TAG, "[PlantGardenPlant] No free tile available")
            return
        }
        AppLog.d(LOG_TAG, "[PlantGardenPlant] Planting $itemId on tile $freeSlot")

        // OPTIMISTIC: remove plant from inventory + decrement free tiles
        val previousInventory = session.inventory
        val previousFreeTiles = session.freePlantTiles
        ctx.updateSession(sessionId) { s ->
            s.copy(
                inventory = s.inventory.copy(
                    plants = s.inventory.plants.filter { it.id != itemId }
                ),
                freePlantTiles = (s.freePlantTiles - 1).coerceAtLeast(0),
            )
        }

        client.actions.plantGardenPlant(slot = freeSlot, itemId = itemId)

        // Rollback after 5s if server hasn't confirmed
        val key = "$sessionId:unpot:$itemId"
        pendingUnpotJobs[key]?.cancel()
        pendingUnpotJobs[key] = ctx.scope.launch {
            delay(5000)
            ctx.updateSession(sessionId) { s ->
                s.copy(inventory = previousInventory, freePlantTiles = previousFreeTiles)
            }
            pendingUnpotJobs.remove(key)
        }
    }

    private val pendingPlantJobs = mutableMapOf<String, Job>()

    /**
     * Plant a seed with optimistic update, on [tileId] when the player picked one, otherwise on
     * the first available dirt tile (see [com.mgafk.app.data.model.PlantPlacementMode]).
     */
    fun plantSeed(sessionId: String, species: String, tileId: Int? = null) {
        val client = ctx.clients[sessionId] ?: return
        val session = ctx.session(sessionId) ?: return
        val freeSlot = tileId ?: findFirstFreePlantTile(client)
        if (freeSlot == null) {
            AppLog.w(LOG_TAG, "[PlantSeed] No free tile available")
            return
        }
        AppLog.d(LOG_TAG, "[PlantSeed] Planting $species on tile $freeSlot")

        // OPTIMISTIC: decrement seed quantity + decrement free tiles
        val previousInventory = session.inventory
        val previousFreeTiles = session.freePlantTiles
        ctx.updateSession(sessionId) { s ->
            s.copy(
                inventory = s.inventory.copy(
                    seeds = s.inventory.seeds.mapNotNull { seed ->
                        if (seed.species == species) {
                            if (seed.quantity > 1) seed.copy(quantity = seed.quantity - 1) else null
                        } else seed
                    }
                ),
                freePlantTiles = (s.freePlantTiles - 1).coerceAtLeast(0),
            )
        }

        // Send to server
        client.actions.plantSeed(slot = freeSlot, species = species)

        // Rollback after 5s if server hasn't confirmed
        val key = "$sessionId:plant:$species:$freeSlot"
        pendingPlantJobs[key]?.cancel()
        pendingPlantJobs[key] = ctx.scope.launch {
            delay(5000)
            ctx.updateSession(sessionId) { s ->
                s.copy(inventory = previousInventory, freePlantTiles = previousFreeTiles)
            }
            pendingPlantJobs.remove(key)
        }
    }

    /** Tile indices currently holding a plant/egg/decor, or null if the garden isn't known yet.
     * Empty tiles have no entry in tileObjects at all, so this set says nothing about the
     * garden's size - that's [GardenTiles.DIRT_TILES_PER_GARDEN]. */
    private fun occupiedPlantTiles(client: RoomClient): Set<Int>? {
        val me = client.gameState.getPlayer(client.playerId) ?: return null
        val tiles = me.getGardenTiles() ?: return null
        return tiles.keys.mapNotNull { it.toIntOrNull() }.toSet()
    }

    /** Number of empty tiles left in the player's garden. */
    internal fun computeFreePlantTileCount(client: RoomClient): Int {
        val occupied = occupiedPlantTiles(client) ?: return 0
        return GardenTiles.freeTileCount(occupied)
    }

    /** Find the first tile index not occupied by a garden object. */
    private fun findFirstFreePlantTile(client: RoomClient): Int? {
        val occupied = occupiedPlantTiles(client) ?: return null
        return GardenTiles.firstFreeTile(occupied)
    }

    // ---- Crystals ----

    /**
     * Plants one [type] shard on an empty tile, dirt or boardwalk.
     *
     * Which shard gets spent is [Crystals.chooseForPlace]'s call. Nothing is sent when the
     * player owns none, or when the garden already holds as many of that kind as it may.
     */
    fun placeCrystal(
        sessionId: String,
        type: CrystalType,
        tileType: GardenTileType,
        localTileIndex: Int,
    ) {
        val client = ctx.clients[sessionId] ?: return
        val session = ctx.session(sessionId) ?: return
        if (!Crystals.canPlace(type, session.crystals)) {
            AppLog.d(LOG_TAG, "[Crystal] ${type.id} is at its per-garden limit for $sessionId")
            return
        }
        val shard = Crystals.chooseForPlace(Crystals.ownedShards(type, session.inventory.tools))
        if (shard == null) {
            AppLog.d(LOG_TAG, "[Crystal] no ${type.toolId} to plant for $sessionId")
            return
        }
        client.actions.placeCrystal(shard.ref, tileType, localTileIndex)
    }

    /**
     * Fuses a shard into [target], extending it by whatever fits under the ceiling.
     *
     * The gain is computed here rather than trusted from the UI, because the server rejects a
     * gain larger than what is left, and the crystal may have ticked down since it was drawn.
     */
    fun fuseCrystal(sessionId: String, target: PlacedCrystal) {
        val client = ctx.clients[sessionId] ?: return
        val session = ctx.session(sessionId) ?: return
        // Measure against what the server last reported, not against what the card has counted
        // down to. The server refuses a gain larger than the room actually left, so erring on
        // the high side loses the whole fuse, while erring low only leaves seconds on the table.
        val current = session.crystals.firstOrNull {
            it.tileType == target.tileType && it.localTileIndex == target.localTileIndex
        } ?: target
        val shards = Crystals.ownedShards(current.type, session.inventory.tools)
        val shard = Crystals.chooseForFuse(shards, current.remainingSeconds) ?: return
        val gain = Crystals.mergeGainSeconds(current.remainingSeconds, shard.seconds)
        if (gain <= 0) return
        client.actions.fuseCrystal(shard.ref, current.tileType, current.localTileIndex, gain)
    }

    /** Takes [crystal] back into the inventory, keeping whatever time it has left. */
    fun pickupCrystal(sessionId: String, crystal: PlacedCrystal) {
        ctx.actions(sessionId)?.pickupCrystal(
            crystalType = crystal.type,
            tileType = crystal.tileType,
            localTileIndex = crystal.localTileIndex,
        )
    }

    // ---- Server confirmations ----

    /** The server sent the garden: the plant, water, pot, unpot and cleanse rollbacks are moot. */
    internal fun onGardenConfirmed(sessionId: String) {
        pendingPlantJobs.cancelForSession(sessionId)
        pendingWaterJobs.cancelForSession(sessionId)
        pendingPotJobs.cancelForSession(sessionId)
        pendingUnpotJobs.cancelForSession(sessionId)
        pendingCleanseJobs.cancelForSession(sessionId)
    }

    /** The server sent the inventory: every rollback of this session is moot. */
    internal fun onInventoryConfirmed(sessionId: String) {
        pendingPlantJobs.cancelForSession(sessionId)
        pendingWaterJobs.cancelForSession(sessionId)
        pendingHatchJobs.cancelForSession(sessionId)
        pendingGrowEggJobs.cancelForSession(sessionId)
        pendingPotJobs.cancelForSession(sessionId)
        pendingUnpotJobs.cancelForSession(sessionId)
        pendingCleanseJobs.cancelForSession(sessionId)
    }

    /** The server sent the garden eggs: the hatch and grow rollbacks are moot. */
    internal fun onEggsConfirmed(sessionId: String) {
        pendingHatchJobs.cancelForSession(sessionId)
        pendingGrowEggJobs.cancelForSession(sessionId)
    }

    /**
     * The pet ids the player owned right before [hatchEgg], taken out as they are read so a
     * later inventory does not report a second hatch. Null when no hatch is waiting.
     */
    internal fun takePreHatchPetIds(sessionId: String): Set<String>? = preHatchPetIds.remove(sessionId)
}
