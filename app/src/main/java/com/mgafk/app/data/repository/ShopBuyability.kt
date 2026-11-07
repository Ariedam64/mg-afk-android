package com.mgafk.app.data.repository

import com.mgafk.app.data.model.Session

/**
 * Tells whether a shop item can still be bought. The reason matters too -
 * the UI surfaces "Owned" vs "Max" badges differently.
 */
enum class ShopItemBuyState {
    Buyable,
    Owned,        // entry.isOneTimePurchase + already owned
    MaxReached,   // entry.maxInventoryQuantity reached
    InventoryFull, // all inventory slots taken and no stack of this item to merge into
}

/**
 * Resolves buyability for a single shop tile.
 *
 * Tools: counts what's in `inventory.tools`.
 * Decors: counts both `inventory.decors` (bought, not yet placed) AND
 *   `availableStorages` (placed in the garden as a storage structure).
 * Seeds and eggs have no per-item cap - the API does not currently expose one.
 * Any item is InventoryFull when the inventory holds INVENTORY_LIMIT entries and
 * has no stack of it to merge into.
 *
 * The count keys off the item's own data category, not the shop it is sold in:
 * the same kind of item now shows up in several shops (ward shards are tools sold
 * in the weather shops, storages are decors sold in the tool shop), so trusting
 * `shopType` would silently skip the cap check.
 */
fun Session.buyState(itemId: String): ShopItemBuyState {
    val entry = MgApi.findItem(itemId) ?: return ShopItemBuyState.Buyable

    val owned: Int = when (MgApi.categoryOf(itemId)) {
        "items" -> inventory.tools.find { it.toolId == itemId }?.quantity ?: 0
        "decors" -> {
            val inInventory = inventory.decors.find { it.decorId == itemId }?.quantity ?: 0
            val placedInGarden = if (itemId in availableStorages) 1 else 0
            inInventory + placedInGarden
        }
        else -> 0
    }

    if (entry.isOneTimePurchase && owned >= 1) return ShopItemBuyState.Owned
    val cap = entry.maxInventoryQuantity
    if (cap != null && owned >= cap) return ShopItemBuyState.MaxReached

    // The game holds INVENTORY_LIMIT entries; a bought item only fits a full inventory
    // when it merges into a stack that is already there (game client checks this
    // before sending PurchaseShopItem, the server rejects it otherwise).
    val stackExists: Boolean = when (MgApi.categoryOf(itemId)) {
        "plants" -> inventory.seeds.any { it.species == itemId }
        "eggs" -> inventory.eggs.any { it.eggId == itemId }
        "decors" -> inventory.decors.any { it.decorId == itemId }
        "items" -> inventory.tools.any { it.toolId == itemId && it.id == null }
        else -> false
    }
    // Tools pinned in the tool ring stay in the inventory but take no bag slot (BR-36).
    val entryCount = StorageCapacity.bagCount(inventory)
    if (!StorageCapacity.canAddStackable(entryCount, StorageCapacity.INVENTORY_LIMIT, stackExists)) {
        return ShopItemBuyState.InventoryFull
    }
    return ShopItemBuyState.Buyable
}
