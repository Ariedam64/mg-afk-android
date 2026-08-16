package com.mgafk.app.service

/**
 * Decides which in-stock shop items still deserve an alert, so a standing stock is announced once
 * and not on every shop update.
 *
 * A shop's item list only carries what its current cycle rolled, so an item rolled by two
 * consecutive restocks never leaves that list: presence alone cannot tell "still the same stock"
 * from "rolled again". Hence the restock reset - a shop that just restocked forgets what it
 * already alerted, and the new cycle gets to alert for the same items.
 */
internal class ShopAlertTracker {

    /** Keys already alerted in their shop's current cycle. */
    private val alerted = mutableSetOf<String>()

    /**
     * Records and returns the keys that should alert now, in [stockedKeys] order.
     *
     * @param stockedKeys every key currently in stock, across all shops.
     * @param restockedShopTypes shop types that rolled a new stock since the previous call.
     * @param isAlertEnabled whether the user asked for an alert on that key. Disabled keys are
     *   never recorded, so enabling one mid cycle still alerts on the stock already standing.
     */
    fun newlyStocked(
        stockedKeys: List<String>,
        restockedShopTypes: Set<String>,
        isAlertEnabled: (String) -> Boolean,
    ): List<String> {
        if (restockedShopTypes.isNotEmpty()) {
            alerted.removeAll { shopTypeOf(it) in restockedShopTypes }
        }

        val newKeys = mutableListOf<String>()
        for (key in stockedKeys) {
            if (key in alerted) continue
            if (!isAlertEnabled(key)) continue
            alerted.add(key)
            newKeys.add(key)
        }
        // An item that sold out or left the shop alerts again the next time it shows up.
        alerted.retainAll(stockedKeys.toSet())
        return newKeys
    }

    private fun shopTypeOf(key: String): String = key.split(KEY_SEPARATOR).getOrElse(1) { "" }

    companion object {
        private const val KEY_SEPARATOR = ':'

        /** The alert key AlertConfig stores for a shop item. */
        fun keyOf(shopType: String, itemName: String): String = "shop$KEY_SEPARATOR$shopType$KEY_SEPARATOR$itemName"
    }
}
