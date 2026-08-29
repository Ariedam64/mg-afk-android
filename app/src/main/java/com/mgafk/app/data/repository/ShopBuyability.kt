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
}

/**
 * Resolves buyability for a single shop tile.
 *
 * Tools: counts what's in `inventory.tools`.
 * Decors: counts both `inventory.decors` (bought, not yet placed) AND
 *   `availableStorages` (placed in the garden as a storage structure).
 * Seeds and eggs are treated as always buyable - the API does not currently
 * expose caps for them.
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
    return ShopItemBuyState.Buyable
}
