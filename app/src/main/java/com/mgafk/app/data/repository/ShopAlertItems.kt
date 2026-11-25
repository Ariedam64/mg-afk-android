package com.mgafk.app.data.repository

/**
 * Which catalogue entries each shop alert category lists. A core shop (seed, tool, egg) keeps
 * the entries whose `eligibleShops` name it or are empty; the weather shops (rain, snow, dawn,
 * amber, thunder...) keep the entries that name them. So a weather potion (eligibleShops [Rain])
 * shows under its weather shop and not under Tools.
 */
object ShopAlertItems {

    fun forCategory(
        category: String,
        plants: Collection<MgApi.GameEntry>,
        eggs: Collection<MgApi.GameEntry>,
        items: Collection<MgApi.GameEntry>,
        decors: Collection<MgApi.GameEntry>,
    ): List<MgApi.GameEntry> = when (category) {
        "seed" -> plants.filter { it.eligibleShops.isEmpty() || "Seed" in it.eligibleShops }
        "tool" -> items.filter { it.eligibleShops.isEmpty() || "Tool" in it.eligibleShops }
        "egg" -> eggs.filter { it.eligibleShops.isEmpty() || "Egg" in it.eligibleShops }
        "decor" -> decors.toList()
        else -> {
            val shopName = category.replaceFirstChar { it.uppercase() }
            (plants + eggs + items + decors).filter { shopName in it.eligibleShops }
        }
    }
}
