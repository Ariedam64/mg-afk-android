package com.mgafk.app.data.repository

import com.mgafk.app.data.model.InventoryProduceItem

/**
 * The game only sells crops in bulk, so selling one means locking every other crop first,
 * selling, then putting the locks back. A lock can name a crop id or a whole species.
 */
object SingleCropSale {

    /** Locks to toggle, in order: [unlock], then [tempLock], sell, [tempLock] again, then [relock]. */
    data class Plan(val unlock: List<String>, val tempLock: List<String>, val relock: List<String>)

    /** Null when [targetId] is no longer in [produce]. */
    fun plan(produce: List<InventoryProduceItem>, locked: Set<String>, targetId: String): Plan? {
        val target = produce.find { it.id == targetId } ?: return null
        val speciesLocked = target.species in locked
        // Lifting the target's species lock frees its siblings too, so they need their own lock.
        val stillLocked = if (speciesLocked) locked - target.species else locked
        val tempLock = produce.filter {
            it.id != targetId && it.id !in stillLocked && it.species !in stillLocked
        }.map { it.id }
        return Plan(
            unlock = listOfNotNull(targetId.takeIf { it in locked }, target.species.takeIf { speciesLocked }),
            tempLock = tempLock,
            relock = listOfNotNull(target.species.takeIf { speciesLocked }),
        )
    }
}
