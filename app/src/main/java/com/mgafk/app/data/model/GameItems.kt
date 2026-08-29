package com.mgafk.app.data.model

/**
 * Ids of the few game items the app has to name explicitly, because they drive an action
 * rather than just being displayed. Everything else is looked up by id from MgApi.
 */

/** Potion that fully restores a pet's hunger (displayed as "Hunger Potion"). */
const val REPLENISH_POTION_ID = "ReplenishPotion"

/** Storage the game keeps potions in, and the only one a Hunger Potion can be pulled back from. */
const val POTION_STORAGE_ID = "ToolShack"
