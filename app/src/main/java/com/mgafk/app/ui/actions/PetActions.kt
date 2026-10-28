package com.mgafk.app.ui.actions

import com.mgafk.app.data.AppLog
import com.mgafk.app.data.model.InventoryCropsItem
import com.mgafk.app.data.model.InventoryProduceItem
import com.mgafk.app.data.model.InventoryToolItem
import com.mgafk.app.data.model.POTION_STORAGE_ID
import com.mgafk.app.data.model.PetSnapshot
import com.mgafk.app.data.model.PetTeam
import com.mgafk.app.data.model.REPLENISH_POTION_ID
import com.mgafk.app.data.model.XP_POTION_ID
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.repository.PetTeams
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.data.websocket.RoomClient
import com.mgafk.app.ui.petTileFromSlotInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/** How long a Hunger Potion pulled from the Tool Shack is awaited before giving up on using it. */
private const val POTION_RETRIEVAL_TIMEOUT_MS = 6_000L

/** Pets: the feeding trough, feeding and potions, the active slots, and the saved teams. */
class PetActions(private val ctx: SessionContext) {
    private val pendingTroughJobs = mutableMapOf<String, Job>()

    /** Debounce per session for the "trough low" alert check (see scheduleTroughAlertCheck). */
    private val pendingTroughAlertJobs = mutableMapOf<String, Job>()

    /**
     * Debounced + ownership-gated feeding-trough low alert.
     *
     * Inventory snapshots can be transient/partial right after login or while the server
     * streams patches (e.g. a pet eating an item briefly replaces the player slot), which
     * made the alert fire "0/1 items left" while the trough was actually full. Instead of
     * checking the just-received snapshot, wait a short moment for the state to settle and
     * then evaluate the latest known trough - and only if the player actually owns a
     * FeedingTrough in that settled snapshot (an absent storage means an incomplete read,
     * not an empty trough).
     */
    internal fun scheduleTroughAlertCheck(sessionId: String) {
        pendingTroughAlertJobs[sessionId]?.cancel()
        pendingTroughAlertJobs[sessionId] = ctx.scope.launch {
            delay(2500)
            val session = ctx.session(sessionId) ?: return@launch
            if ("FeedingTrough" !in session.availableStorages) return@launch
            ctx.alertNotifier.checkFeedingTrough(session.feedingTrough, ctx.state.value.alerts)
        }
    }

    fun putItemsInFeedingTrough(sessionId: String, produceItems: List<InventoryProduceItem>) {
        val actions = ctx.actions(sessionId) ?: return
        val session = ctx.session(sessionId) ?: return
        val currentCount = session.feedingTrough.size
        val toAdd = produceItems.filterIndexed { i, _ -> currentCount + i < 9 }
        if (toAdd.isEmpty()) return

        // Optimistic: add crops to trough + remove from inventory
        val previousTrough = session.feedingTrough
        val previousInventory = session.inventory
        val addedIds = toAdd.map { it.id }.toSet()
        ctx.updateSession(sessionId) { s ->
            s.copy(
                feedingTrough = s.feedingTrough + toAdd.map { p ->
                    InventoryCropsItem(id = p.id, species = p.species, size = p.size, mutations = p.mutations)
                },
                inventory = s.inventory.copy(
                    produce = s.inventory.produce.filter { it.id !in addedIds }
                ),
            )
        }

        // Send to server
        toAdd.forEach { item ->
            actions.putItemInStorage(itemId = item.id, storageId = "FeedingTrough")
        }

        // Rollback after 5s if server hasn't confirmed
        val key = "$sessionId:trough:add:${toAdd.first().id}"
        pendingTroughJobs[key]?.cancel()
        pendingTroughJobs[key] = ctx.scope.launch {
            delay(5000)
            ctx.updateSession(sessionId) { s ->
                s.copy(feedingTrough = previousTrough, inventory = previousInventory)
            }
            pendingTroughJobs.remove(key)
        }
    }

    fun removeItemFromFeedingTrough(sessionId: String, itemId: String) {
        val actions = ctx.actions(sessionId) ?: return
        val session = ctx.session(sessionId) ?: return

        // Optimistic: remove crop from trough + add back to inventory
        val previousTrough = session.feedingTrough
        val previousInventory = session.inventory
        val removed = session.feedingTrough.find { it.id == itemId } ?: return
        ctx.updateSession(sessionId) { s ->
            s.copy(
                feedingTrough = s.feedingTrough.filter { it.id != itemId },
                inventory = s.inventory.copy(
                    produce = s.inventory.produce + InventoryProduceItem(
                        id = removed.id, species = removed.species,
                        size = removed.size, mutations = removed.mutations,
                    )
                ),
            )
        }

        // Send to server
        actions.retrieveItemFromStorage(itemId = itemId, storageId = "FeedingTrough")

        // Rollback after 5s if server hasn't confirmed
        val key = "$sessionId:trough:remove:$itemId"
        pendingTroughJobs[key]?.cancel()
        pendingTroughJobs[key] = ctx.scope.launch {
            delay(5000)
            ctx.updateSession(sessionId) { s ->
                s.copy(feedingTrough = previousTrough, inventory = previousInventory)
            }
            pendingTroughJobs.remove(key)
        }
    }

    private val pendingFeedJobs = mutableMapOf<String, Job>()

    fun feedPet(sessionId: String, petItemId: String, cropItemIds: List<String>) {
        val actions = ctx.actions(sessionId) ?: return
        val session = ctx.session(sessionId) ?: return

        // Optimistic: remove crops from inventory
        val previousInventory = session.inventory
        val idsToRemove = cropItemIds.toSet()
        ctx.updateSession(sessionId) { s ->
            s.copy(
                inventory = s.inventory.copy(
                    produce = s.inventory.produce.filter { it.id !in idsToRemove }
                ),
            )
        }

        // Send each feed action to server
        cropItemIds.forEach { cropId ->
            actions.feedPet(petItemId = petItemId, cropItemId = cropId)
        }

        // Rollback after 5s if server hasn't confirmed
        val key = "$sessionId:feed:$petItemId:${cropItemIds.first()}"
        pendingFeedJobs[key]?.cancel()
        pendingFeedJobs[key] = ctx.scope.launch {
            delay(5000)
            ctx.updateSession(sessionId) { s ->
                s.copy(inventory = previousInventory)
            }
            pendingFeedJobs.remove(key)
        }
    }

    /**
     * Uses one Hunger Potion on [petItemId] to refill its hunger.
     *
     * See [usePotionOnPet] for the sequence both pet potions share.
     */
    fun useReplenishPotionOnPet(sessionId: String, petItemId: String) {
        usePotionOnPet(sessionId, petItemId, REPLENISH_POTION_ID) { client ->
            client.actions.useReplenishPotion(petItemId)
        }
    }

    /**
     * Uses one XP Potion on [petItemId].
     *
     * A pet that has already reached its strength ceiling is left alone: the game's reducer
     * refuses the potion on it and returns without consuming anything, so sending the command
     * would only cost the round trip. The caller hides the action in that case; this is the
     * guard for a pet that matures between the tap and the send.
     */
    fun useXpPotionOnPet(sessionId: String, petItemId: String) {
        val pet = ctx.session(sessionId)
            ?.pets?.find { it.id == petItemId }
        if (pet != null && isPetFullyGrown(pet)) {
            AppLog.d(LOG_TAG, "[Potion] $petItemId is already fully grown, XP Potion not used")
            return
        }
        usePotionOnPet(sessionId, petItemId, XP_POTION_ID) { client ->
            client.actions.xpPotion(petItemId)
        }
    }

    /**
     * Runs the sequence a pet potion needs: make sure one sits in the inventory, pulling it out
     * of the Tool Shack when it does not, then walk over to the pet and drink it. A pet potion
     * only applies to the pet the player is standing on, hence the teleport - the same sequence
     * the auto-feed uses.
     *
     * The retrieval is a separate round trip, so this waits for the server to confirm the potion
     * in the inventory before using it. If that confirmation never lands the potion simply stays
     * where it is, retrieved but unused: nothing is consumed on a guess.
     */
    private fun usePotionOnPet(
        sessionId: String,
        petItemId: String,
        potionId: String,
        use: (RoomClient) -> Unit,
    ) {
        ctx.scope.launch {
            val client = ctx.clients[sessionId] ?: return@launch
            val session = ctx.session(sessionId) ?: return@launch

            if (potionCountIn(session.inventory.tools, potionId) == 0) {
                if (potionCountIn(session.toolShack, potionId) == 0) return@launch
                client.actions.retrieveItemFromStorage(itemId = potionId, storageId = POTION_STORAGE_ID)
                val arrived = withTimeoutOrNull(POTION_RETRIEVAL_TIMEOUT_MS) {
                    ctx.state.first { state ->
                        val tools = state.sessions.find { it.id == sessionId }?.inventory?.tools
                        potionCountIn(tools.orEmpty(), potionId) > 0
                    }
                }
                if (arrived == null) {
                    AppLog.d(LOG_TAG, "[Potion] Retrieval of $potionId from $POTION_STORAGE_ID not confirmed for $sessionId")
                    return@launch
                }
            }

            val position = findPetPosition(client, petItemId) ?: return@launch
            client.actions.teleport(position.first, position.second)
            use(client)
        }
    }

    private fun potionCountIn(
        tools: List<InventoryToolItem>,
        potionId: String = REPLENISH_POTION_ID,
    ): Int = tools.find { it.toolId == potionId }?.quantity ?: 0

    /** Whether [pet] sits at its strength ceiling, which is what makes an XP Potion a no-op. */
    private fun isPetFullyGrown(pet: PetSnapshot): Boolean {
        val entry = MgApi.findPet(pet.species) ?: return false
        return PriceCalculator.isPetMaxStrength(
            xp = pet.xp,
            targetScale = pet.targetScale,
            maxScale = entry.maxScale ?: 1.0,
            hoursToMature = entry.hoursToMature ?: 1.0,
        )
    }

    /** Reads the pet's current tile position from the player's own live petSlotInfos. */
    private fun findPetPosition(client: RoomClient, petId: String): Pair<Double, Double>? {
        val me = client.gameState.getPlayer(client.playerId) ?: return null
        val info = me.petSlotInfos?.get(petId) as? JsonObject ?: return null
        return petTileFromSlotInfo(info)
    }

    // ---- Pet swap / equip / unequip ----

    /**
     * Swap an active pet with one from inventory or hutch.
     * If the target pet is in the hutch, do it atomically with SwapPetFromStorage
     * (single message, capacity-neutral) instead of retrieve+swap+store.
     */
    fun swapPet(sessionId: String, activePetId: String, targetPetId: String, targetIsInHutch: Boolean) {
        val client = ctx.clients[sessionId] ?: return
        val actions = client.actions

        if (targetIsInHutch) {
            actions.swapPetFromStorage(petSlotId = activePetId, storagePetId = targetPetId, storageId = "PetHutch")
            return
        }

        actions.swapPet(petSlotId = activePetId, petInventoryId = targetPetId)

        // Store the old active pet back in hutch
        actions.putItemInStorage(itemId = activePetId, storageId = "PetHutch")
    }

    /**
     * Equip a pet from inventory/hutch into an empty active slot.
     * Uses placePet with a garden dirt tile.
     */
    fun equipPet(sessionId: String, targetPetId: String, targetIsInHutch: Boolean) {
        val client = ctx.clients[sessionId] ?: run {
            AppLog.w(LOG_TAG, "[EquipPet] No client for session $sessionId")
            return
        }
        val actions = client.actions

        AppLog.d(LOG_TAG, "[EquipPet] petId=$targetPetId, isInHutch=$targetIsInHutch")

        if (targetIsInHutch) {
            AppLog.d(LOG_TAG, "[EquipPet] Retrieving from hutch first")
            actions.retrieveItemFromStorage(itemId = targetPetId, storageId = "PetHutch")
        }

        // Place pet on a free dirt tile of our garden slot
        val me = client.gameState.getPlayer(client.playerId)
        val slotIndex = (me?.slotIndex ?: 0).coerceIn(0, 5)
        val base = SLOT_BASE_TILE[slotIndex]

        // Find which local indices (0,1,2) are occupied by active pets
        val occupiedLocal = mutableSetOf<Int>()
        val slotInfos = me?.petSlotInfos
        AppLog.d(LOG_TAG, "[EquipPet] petSlotInfos = $slotInfos")
        if (slotInfos != null) {
            for ((_, infoEl) in slotInfos) {
                val pos = (infoEl as? JsonObject)?.get("position") as? JsonObject ?: continue
                val px = pos["x"]?.jsonPrimitive?.intOrNull ?: continue
                // localIndex = px - baseX
                val local = px - base.first
                if (local in 0..2) occupiedLocal.add(local)
            }
        }

        val freeLocal = (0..2).firstOrNull { it !in occupiedLocal } ?: 0
        val x = base.first + freeLocal
        val y = base.second
        AppLog.d(LOG_TAG, "[EquipPet] slotIndex=$slotIndex, occupied=$occupiedLocal, freeLocal=$freeLocal, pos=($x,$y)")
        actions.placePet(
            itemId = targetPetId,
            x = x.toDouble(), y = y.toDouble(),
            tileType = "Dirt",
            localTileIndex = freeLocal,
        )
    }

    /** Base dirt tile (x, y) for each of the 6 player slots. Map is 101 cols, static layout. */
    private val SLOT_BASE_TILE = arrayOf(
        14 to 14,  // slot 0
        40 to 14,  // slot 1
        66 to 14,  // slot 2
        14 to 36,  // slot 3
        40 to 36,  // slot 4
        66 to 36,  // slot 5
    )

    /**
     * Remove an active pet (pickup + store in hutch).
     */
    fun unequipPet(sessionId: String, petId: String) {
        val actions = ctx.actions(sessionId) ?: return
        actions.pickupPet(petId = petId)
        actions.putItemInStorage(itemId = petId, storageId = "PetHutch")
    }

    // ---- Pet Teams ----

    // Teams are server state now: every change goes out as the game's own command and comes
    // back through PetTeamsChanged. Nothing is stored locally, so nothing can drift.

    /**
     * Create a team from the editor overlay selections.
     *
     * The id is minted here, the way the game's client does. Refused past [PetTeam.MAX_TEAMS],
     * which the server would reject silently.
     */
    fun createPetTeam(sessionId: String, name: String, petIds: List<String>) {
        val client = ctx.clients[sessionId] ?: return
        val session = ctx.session(sessionId) ?: return
        if (session.petTeams.size >= PetTeam.MAX_TEAMS) {
            AppLog.w(LOG_TAG, "[PetTeam] Team limit reached (${PetTeam.MAX_TEAMS}), not creating")
            return
        }
        val members = petIds.filter { it.isNotBlank() }.take(PetTeam.MAX_PETS)
        if (members.size < PetTeam.MIN_PETS || name.isBlank()) return
        client.actions.savePetTeam(
            teamId = UUID.randomUUID().toString(),
            name = name,
            petIds = members,
            isCreate = true,
        )
    }

    /** Rename a team and/or change its members. Both travel in the same SavePetTeam. */
    fun updatePetTeam(sessionId: String, teamId: String, name: String, petIds: List<String>) {
        val client = ctx.clients[sessionId] ?: return
        val members = petIds.filter { it.isNotBlank() }.take(PetTeam.MAX_PETS)
        if (members.size < PetTeam.MIN_PETS || name.isBlank()) return
        client.actions.savePetTeam(teamId = teamId, name = name, petIds = members, isCreate = false)
    }

    fun deletePetTeam(sessionId: String, teamId: String) {
        ctx.actions(sessionId)?.deletePetTeam(teamId)
    }

    fun renamePetTeam(sessionId: String, teamId: String, newName: String) {
        val session = ctx.session(sessionId) ?: return
        val team = session.petTeams.find { it.id == teamId } ?: return
        updatePetTeam(sessionId, teamId, newName, team.petIds)
    }

    /** Reorder teams by moving [fromIndex] to [toIndex]. */
    fun reorderPetTeams(sessionId: String, fromIndex: Int, toIndex: Int) {
        val client = ctx.clients[sessionId] ?: return
        val teams = ctx.session(sessionId)?.petTeams ?: return
        if (fromIndex !in teams.indices || toIndex !in teams.indices || fromIndex == toIndex) return
        client.actions.movePetTeam(movePetTeamId = teams[fromIndex].id, toPetTeamIndex = toIndex)
    }

    /**
     * Activate a pet team.
     *
     * One command: the server pulls the members out of the inventory and the storages itself,
     * skipping any the player no longer owns. The app used to re-enact this pet by pet with
     * pickup/store/place, which could half-apply and could not reach a pet in a storage.
     */
    fun activateTeam(sessionId: String, team: PetTeam) {
        val client = ctx.clients[sessionId] ?: return
        val session = ctx.session(sessionId) ?: return

        if (PetTeams.isActive(team, session.pets.map { it.id })) {
            AppLog.d(LOG_TAG, "[ActivateTeam] Team already active, skipping")
            return
        }
        // The game refuses the same way rather than emptying the slots.
        val ownedPetIds = (session.pets.map { it.id } +
            session.petHutch.map { it.id } +
            session.inventory.pets.map { it.id }).toSet()
        if (team.petIds.none { it in ownedPetIds }) {
            AppLog.w(LOG_TAG, "[ActivateTeam] None of '${team.name}' pets are owned any more, skipping")
            return
        }

        AppLog.d(LOG_TAG, "[ActivateTeam] Applying '${team.name}' (${team.id})")
        client.actions.applyPetTeam(team.id)
    }

    /**
     * The team whose members are exactly the active pets, or null when none matches.
     * Mirrors the game's own rule, see [PetTeams.isActive].
     */
    fun detectActiveTeamId(sessionId: String): String? {
        val session = ctx.session(sessionId) ?: return null
        val activePetIds = session.pets.map { it.id }
        return session.petTeams.firstOrNull { PetTeams.isActive(it, activePetIds) }?.id
    }

    /** The server sent the inventory: every pending trough and feed rollback is moot. */
    internal fun onInventoryConfirmed() {
        pendingTroughJobs.values.forEach { it.cancel() }
        pendingTroughJobs.clear()
        pendingFeedJobs.values.forEach { it.cancel() }
        pendingFeedJobs.clear()
    }
}
