package com.mgafk.app.ui.actions

import com.mgafk.app.data.repository.ShopStock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Buying from the shops. */
class ShopActions(private val ctx: SessionContext) {
    // Optimistic purchase: decrement stock locally, send to server, rollback if no confirmation
    private val pendingPurchaseJobs = mutableMapOf<String, Job>()

    /** How many of an item each pending rollback puts back, keyed like [pendingPurchaseJobs]. */
    private val pendingCounts = mutableMapOf<String, Int>()

    fun purchaseShopItem(sessionId: String, shopType: String, itemName: String) {
        buy(sessionId, shopType, itemName, all = false)
    }

    fun purchaseAllShopItem(sessionId: String, shopType: String, itemName: String) {
        buy(sessionId, shopType, itemName, all = true)
    }

    /** Buys one [itemName], or every one left when [all], and takes them off the stock right away. */
    private fun buy(sessionId: String, shopType: String, itemName: String, all: Boolean) {
        val actions = ctx.actions(sessionId) ?: return
        val session = ctx.session(sessionId) ?: return
        val shop = session.shops.find { it.type == shopType } ?: return
        val currentStock = shop.itemStocks[itemName] ?: 0
        if (currentStock <= 0) return
        val count = if (all) currentStock else 1

        ctx.updateSession(sessionId) { s -> s.copy(shops = ShopStock.take(s.shops, shopType, itemName, count)) }
        repeat(count) { actions.purchaseShopItem(shopType, itemName) }

        // Rollback after 5s if server hasn't confirmed (ShopsChanged would cancel this first).
        // Only this purchase is put back, so any bought since stays bought.
        val key = "$sessionId:$shopType:$itemName"
        // A purchase of the same item still waiting folds into this one's rollback.
        val unconfirmed = count + (if (pendingPurchaseJobs[key]?.isActive == true) pendingCounts[key] ?: 0 else 0)
        pendingCounts[key] = unconfirmed
        pendingPurchaseJobs[key]?.cancel()
        pendingPurchaseJobs[key] = ctx.scope.launch {
            delay(5000)
            ctx.updateSession(sessionId) { s -> s.copy(shops = ShopStock.giveBack(s.shops, shopType, itemName, unconfirmed)) }
            pendingPurchaseJobs.remove(key)
            pendingCounts.remove(key)
            ctx.state.update { it.copy(purchaseError = "Purchase failed: $itemName") }
            delay(3000)
            ctx.state.update { if (it.purchaseError == "Purchase failed: $itemName") it.copy(purchaseError = "") else it }
        }
    }

    /** The server confirmed the shops: this session's purchases need no rollback any more. */
    internal fun cancelPendingPurchases(sessionId: String) {
        pendingPurchaseJobs.cancelForSession(sessionId)
        pendingCounts.keys.removeAll { it.startsWith("$sessionId:") }
    }
}
