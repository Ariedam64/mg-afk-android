package com.mgafk.app.data.repository

import com.mgafk.app.data.model.ShopSnapshot

/**
 * The optimistic side of buying: the stock drops as soon as the player taps, and only the
 * part a failed purchase took is put back, so purchases made since are left alone.
 */
object ShopStock {

    /** [shops] with [count] fewer [itemName] in [shopType], never below zero. */
    fun take(shops: List<ShopSnapshot>, shopType: String, itemName: String, count: Int): List<ShopSnapshot> =
        adjust(shops, shopType, itemName) { maxOf(0, it - count) }

    /** Undoes a [take] of [count] that the server never confirmed. */
    fun giveBack(shops: List<ShopSnapshot>, shopType: String, itemName: String, count: Int): List<ShopSnapshot> =
        adjust(shops, shopType, itemName) { it + count }

    private fun adjust(
        shops: List<ShopSnapshot>,
        shopType: String,
        itemName: String,
        change: (Int) -> Int,
    ): List<ShopSnapshot> = shops.map { shop ->
        val stock = shop.itemStocks[itemName]
        if (shop.type != shopType || stock == null) shop
        else shop.copy(itemStocks = shop.itemStocks + (itemName to change(stock)))
    }
}
