package com.mgafk.app.ui.actions

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Buying from the shops. */
class ShopActions(private val ctx: SessionContext) {
    // Optimistic purchase: decrement stock locally, send to server, rollback if no confirmation
    private val pendingPurchaseJobs = mutableMapOf<String, Job>()

    fun purchaseShopItem(sessionId: String, shopType: String, itemName: String) {
        val actions = ctx.actions(sessionId) ?: return

        // Check current stock before optimistic update
        val session = ctx.session(sessionId) ?: return
        val shop = session.shops.find { it.type == shopType } ?: return
        val currentStock = shop.itemStocks[itemName] ?: 0
        if (currentStock <= 0) return

        // Optimistic: decrement stock locally
        val previousShops = session.shops
        ctx.updateSession(sessionId) { s ->
            s.copy(shops = s.shops.map { snap ->
                if (snap.type == shopType) {
                    snap.copy(itemStocks = snap.itemStocks.mapValues { (name, stock) ->
                        if (name == itemName) maxOf(0, stock - 1) else stock
                    })
                } else snap
            })
        }

        // Send to server
        actions.purchaseShopItem(shopType, itemName)

        // Rollback after 5s if server hasn't confirmed (ShopsChanged would overwrite first)
        val key = "$sessionId:$shopType:$itemName"
        pendingPurchaseJobs[key]?.cancel()
        pendingPurchaseJobs[key] = ctx.scope.launch {
            delay(5000)
            // If we get here, server never confirmed - rollback
            ctx.updateSession(sessionId) { s ->
                val current = s.shops.find { it.type == shopType }
                if (current != null) s.copy(shops = previousShops) else s
            }
            pendingPurchaseJobs.remove(key)
            // Show error briefly
            ctx.state.update { it.copy(purchaseError = "Purchase failed: $itemName") }
            delay(3000)
            ctx.state.update { if (it.purchaseError == "Purchase failed: $itemName") it.copy(purchaseError = "") else it }
        }
    }

    fun purchaseAllShopItem(sessionId: String, shopType: String, itemName: String) {
        val actions = ctx.actions(sessionId) ?: return
        val session = ctx.session(sessionId) ?: return
        val shop = session.shops.find { it.type == shopType } ?: return
        val currentStock = shop.itemStocks[itemName] ?: 0
        if (currentStock <= 0) return

        // Optimistic: set stock to 0
        val previousShops = session.shops
        ctx.updateSession(sessionId) { s ->
            s.copy(shops = s.shops.map { snap ->
                if (snap.type == shopType) {
                    snap.copy(itemStocks = snap.itemStocks.mapValues { (name, stock) ->
                        if (name == itemName) 0 else stock
                    })
                } else snap
            })
        }

        // Send N purchase commands to server
        repeat(currentStock) { actions.purchaseShopItem(shopType, itemName) }

        // Rollback after 5s if server hasn't confirmed
        val key = "$sessionId:$shopType:$itemName"
        pendingPurchaseJobs[key]?.cancel()
        pendingPurchaseJobs[key] = ctx.scope.launch {
            delay(5000)
            ctx.updateSession(sessionId) { s ->
                val current = s.shops.find { it.type == shopType }
                if (current != null) s.copy(shops = previousShops) else s
            }
            pendingPurchaseJobs.remove(key)
            ctx.state.update { it.copy(purchaseError = "Purchase failed: $itemName") }
            delay(3000)
            ctx.state.update { if (it.purchaseError == "Purchase failed: $itemName") it.copy(purchaseError = "") else it }
        }
    }

    /** The server confirmed the shops: this session's purchases need no rollback any more. */
    internal fun cancelPendingPurchases(sessionId: String) {
        pendingPurchaseJobs.cancelForSession(sessionId)
    }
}
