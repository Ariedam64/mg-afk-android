package com.mgafk.app.ui.actions

import com.mgafk.app.data.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private class TokenExpiredException : Exception("Discord token expired")

/** Buying from the shops, and the player's currency balance. */
class ShopActions(private val ctx: SessionContext) {
    // Optimistic purchase: decrement stock locally, send to server, rollback if no confirmation
    private val pendingPurchaseJobs = mutableMapOf<String, Job>()

    // ---- Currency balance ----

    fun fetchCurrencyBalance(sessionId: String) {
        val session = ctx.session(sessionId) ?: run {
            AppLog.w(LOG_TAG, "[Balance] Session $sessionId not found")
            return
        }
        if (session.cookie.isBlank() || session.gameVersion.isBlank() || session.room.isBlank()) {
            AppLog.w(LOG_TAG, "[Balance] Skipped: cookie=${session.cookie.isNotBlank()}, version=${session.gameVersion}, room=${session.room}")
            return
        }
        ctx.state.update { it.copy(currencyBalanceLoading = true) }
        ctx.scope.launch {
            try {
                val host = session.gameUrl
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .ifBlank { "magicgarden.gg" }
                val url = "https://$host/version/${session.gameVersion}/api/rooms/${session.room}/me"
                AppLog.d(LOG_TAG, "[Balance] GET $url")
                AppLog.d(LOG_TAG, "[Balance] Cookie: mc_jwt=${session.cookie}")
                val balance = withContext(Dispatchers.IO) {
                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .header("Cookie", "mc_jwt=${session.cookie}")
                        .build()
                    val client = okhttp3.OkHttpClient()
                    val response = client.newCall(request).execute()
                    val code = response.code
                    AppLog.d(LOG_TAG, "[Balance] HTTP $code")
                    val body = response.body?.string() ?: throw Exception("Empty response")
                    AppLog.d(LOG_TAG, "[Balance] Body: $body")
                    if (code == 401) {
                        throw TokenExpiredException()
                    }
                    if (!response.isSuccessful) {
                        throw Exception("HTTP $code")
                    }
                    val obj = com.mgafk.app.data.AppJson.default
                        .parseToJsonElement(body).jsonObject
                    obj["currencyBalance"]?.jsonPrimitive?.longOrNull
                        ?: throw Exception("No currencyBalance field in response")
                }
                AppLog.d(LOG_TAG, "[Balance] OK -> $balance breads")
                ctx.state.update { it.copy(currencyBalance = balance, currencyBalanceLoading = false, currencyBalanceError = null) }
            } catch (e: TokenExpiredException) {
                AppLog.e(LOG_TAG, "[Balance] Token expired, need re-login")
                ctx.state.update { it.copy(currencyBalanceLoading = false, currencyBalanceError = "token_expired") }
            } catch (e: Exception) {
                AppLog.e(LOG_TAG, "[Balance] Failed: ${e.message}", e)
                ctx.state.update { it.copy(currencyBalanceLoading = false, currencyBalanceError = e.message) }
            }
        }
    }


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
