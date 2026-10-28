package com.mgafk.app.ui.actions

import com.mgafk.app.data.model.InventoryDecorItem
import com.mgafk.app.data.model.InventorySeedItem
import com.mgafk.app.data.model.InventoryToolItem
import com.mgafk.app.data.model.Session
import com.mgafk.app.service.AutoStockTracker

/** The inventory and the storage buildings: locks, selling, upgrades, moves, and auto-stock. */
class StorageActions(private val ctx: SessionContext) {
    // ---- Favorites (lock/unlock) ----

    /** Toggle lock state of an item with optimistic update. */
    fun toggleLockItem(sessionId: String, itemId: String) {
        val actions = ctx.actions(sessionId) ?: return

        // OPTIMISTIC: toggle in the local set
        ctx.updateSession(sessionId) { s ->
            val newFavs = if (itemId in s.favoritedItemIds) s.favoritedItemIds - itemId
                else s.favoritedItemIds + itemId
            s.copy(favoritedItemIds = newFavs)
        }

        actions.toggleLockItem(itemId = itemId)
    }

    // ---- Sell pet ----

    /** Sell a pet. If locked, unlock first then sell. */
    fun sellPet(sessionId: String, itemId: String) {
        val actions = ctx.actions(sessionId) ?: return
        val session = ctx.session(sessionId) ?: return

        // If locked, unlock first
        if (itemId in session.favoritedItemIds) {
            actions.toggleLockItem(itemId = itemId)
        }
        // Also check if locked by species
        val pet = (session.inventory.pets + session.petHutch).find { it.id == itemId }
        if (pet != null && pet.petSpecies in session.favoritedItemIds) {
            actions.toggleLockItem(itemId = pet.petSpecies)
        }

        actions.sellPet(itemId = itemId)
    }

    /** Upgrade the pet hutch capacity by one level (server uses caller's dust). */
    fun upgradePetHutch(sessionId: String) {
        ctx.actions(sessionId)?.upgradePetHutch()
    }

    /** Upgrade the seed silo capacity by one level (server uses caller's dust). */
    fun upgradeSeedSilo(sessionId: String) {
        ctx.actions(sessionId)?.upgradeSeedSilo()
    }

    /** Upgrade the decor shed capacity by one level (server uses caller's dust). */
    fun upgradeDecorShed(sessionId: String) {
        ctx.actions(sessionId)?.upgradeDecorShed()
    }

    /** Upgrade the tool shack capacity by one level (server uses caller's dust). */
    fun upgradeToolShack(sessionId: String) {
        ctx.actions(sessionId)?.upgradeToolShack()
    }

    // ─── Move items between inventory and dedicated storages ────────────────

    /** Move a pet from inventory into the Pet Hutch. */
    fun movePetToHutch(sessionId: String, petId: String) {
        ctx.actions(sessionId)?.putItemInStorage(itemId = petId, storageId = "PetHutch")
    }

    /** Move a pet from the Pet Hutch back to the inventory. */
    fun movePetFromHutch(sessionId: String, petId: String) {
        ctx.actions(sessionId)?.retrieveItemFromStorage(itemId = petId, storageId = "PetHutch")
    }

    /** Move a seed (whole stack) from inventory into the Seed Silo. */
    fun moveSeedToSilo(sessionId: String, species: String) {
        ctx.actions(sessionId)?.putItemInStorage(itemId = species, storageId = "SeedSilo")
    }

    /** Move a seed (whole stack) from the Seed Silo back to inventory. */
    fun moveSeedFromSilo(sessionId: String, species: String) {
        ctx.actions(sessionId)?.retrieveItemFromStorage(itemId = species, storageId = "SeedSilo")
    }

    /** Move a decor (whole stack) from inventory into the Decor Shed. */
    fun moveDecorToShed(sessionId: String, decorId: String) {
        ctx.actions(sessionId)?.putItemInStorage(itemId = decorId, storageId = "DecorShed")
    }

    /** Move a decor (whole stack) from the Decor Shed back to inventory. */
    fun moveDecorFromShed(sessionId: String, decorId: String) {
        ctx.actions(sessionId)?.retrieveItemFromStorage(itemId = decorId, storageId = "DecorShed")
    }

    /** Move a tool (whole stack) from inventory into the Tool Shack. */
    /**
     * Moves a tool to the Tool Shack. [toolKey] is what the game names it by, which is its own
     * id for the tools it tracks individually (see [InventoryToolItem.storageKey]).
     */
    fun moveToolToShack(sessionId: String, toolKey: String) {
        ctx.actions(sessionId)?.putItemInStorage(itemId = toolKey, storageId = "ToolShack")
    }

    /** Move a tool (whole stack) from the Tool Shack back to inventory. */
    fun moveToolFromShack(sessionId: String, toolKey: String) {
        ctx.actions(sessionId)?.retrieveItemFromStorage(itemId = toolKey, storageId = "ToolShack")
    }

    private fun totalInventoryCount(session: Session): Int {
        val inv = session.inventory
        return inv.seeds.size + inv.eggs.size + inv.produce.size + inv.plants.size +
            inv.pets.size + inv.tools.size + inv.decors.size
    }

    /**
     * Auto-consolidate inventory stacks into matching storage slots when the
     * corresponding setting is on AND the player owns the storage.
     *
     * For each inventory item whose species/decorId already has a slot in the
     * storage, fire a single move into it. Stacks merge server-side.
     */
    internal fun runAutoStock(
        sessionId: String,
        invSeeds: List<InventorySeedItem>,
        invDecors: List<InventoryDecorItem>,
        invTools: List<InventoryToolItem>,
        siloSeeds: List<InventorySeedItem>,
        shedDecors: List<InventoryDecorItem>,
        shackTools: List<InventoryToolItem>,
        availableStorages: Set<String>,
    ) {
        val actions = ctx.actions(sessionId) ?: return
        val settings = ctx.state.value.settings

        // Each move as the storage it targets and the key the game names the item by.
        // Gathered first so the tracker can weed out the ones already asked for: auto-stock
        // runs on every inventory change, and a move the server refuses leaves the item in
        // place, so an unguarded retry never stops.
        val moves = buildList {
            if (settings.autoStockSeedSilo && "SeedSilo" in availableStorages) {
                val siloSpecies = siloSeeds.map { it.species }.toSet()
                invSeeds.filter { it.species in siloSpecies }
                    .forEach { add("SeedSilo" to it.species) }
            }
            if (settings.autoStockDecorShed && "DecorShed" in availableStorages) {
                val shedIds = shedDecors.map { it.decorId }.toSet()
                invDecors.filter { it.decorId in shedIds }
                    .forEach { add("DecorShed" to it.decorId) }
            }
            if (settings.autoStockToolShack && "ToolShack" in availableStorages) {
                val shackIds = shackTools.map { it.toolId }.toSet()
                // By storageKey, not toolId: a crystal shard the game tracks individually
                // answers to its own id, and asking for its toolId is refused every time.
                invTools.filter { it.toolId in shackIds }
                    .forEach { add("ToolShack" to it.storageKey) }
            }
        }

        val worthSending = autoStockTracker
            .pending(sessionId, moves.map { (storage, key) -> "$storage:$key" })
            .toSet()

        for ((storageId, itemKey) in moves) {
            if ("$storageId:$itemKey" !in worthSending) continue
            actions.putItemInStorage(itemId = itemKey, storageId = storageId)
        }
    }

    /** See [AutoStockTracker]: keeps a refused move from being asked for on every update. */
    private val autoStockTracker = AutoStockTracker()

    /** Sell all crops at once. */
    fun sellAllCrops(sessionId: String) {
        ctx.actions(sessionId)?.sellAllCrops()
    }

    /** Sell a single crop by temporarily locking all others, selling, then unlocking. */
    fun sellSingleCrop(sessionId: String, itemId: String) {
        val actions = ctx.actions(sessionId) ?: return
        val session = ctx.session(sessionId) ?: return

        // Find all unlocked produces except the target
        val toTempLock = session.inventory.produce.filter { produce ->
            produce.id != itemId &&
                produce.id !in session.favoritedItemIds &&
                produce.species !in session.favoritedItemIds
        }

        // If the target itself is locked, unlock it first
        val targetLocked = itemId in session.favoritedItemIds
        if (targetLocked) {
            actions.toggleLockItem(itemId = itemId)
        }
        // Also check species lock
        val targetProduce = session.inventory.produce.find { it.id == itemId }
        val speciesLocked = targetProduce != null && targetProduce.species in session.favoritedItemIds
        if (speciesLocked) {
            actions.toggleLockItem(itemId = targetProduce!!.species)
        }

        // Lock all other unlocked produces
        val lockedIds = toTempLock.map { it.id }
        lockedIds.forEach { id -> actions.toggleLockItem(itemId = id) }

        // Sell (only the target remains unlocked)
        actions.sellAllCrops()

        // Unlock everything we temporarily locked
        lockedIds.forEach { id -> actions.toggleLockItem(itemId = id) }

        // Re-lock target if it was locked before
        if (targetLocked) {
            // Don't re-lock since we just sold it
        }
        // Re-lock species if it was locked
        if (speciesLocked) {
            actions.toggleLockItem(itemId = targetProduce!!.species)
        }
    }

    /** Sell multiple pets at once (one request per pet). */
    fun sellAllUnlockedPets(sessionId: String, itemIds: List<String>) {
        val actions = ctx.actions(sessionId) ?: return
        itemIds.forEach { itemId ->
            actions.sellPet(itemId = itemId)
        }
    }

    /** A reconnect rebuilds the inventory, so a move [autoStockTracker] gave up on gets another go. */
    internal fun forgetAutoStock(sessionId: String) {
        autoStockTracker.forget(sessionId)
    }
}
