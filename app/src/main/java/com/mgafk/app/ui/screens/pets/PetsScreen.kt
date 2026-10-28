package com.mgafk.app.ui.screens.pets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mgafk.app.data.model.REPLENISH_POTION_ID
import com.mgafk.app.data.model.XP_POTION_ID
import com.mgafk.app.data.repository.Crystals
import com.mgafk.app.data.model.Tip
import com.mgafk.app.ui.MainViewModel
import com.mgafk.app.ui.UiState
import com.mgafk.app.ui.screens.logs.AbilityLogsCard

/** Active pets, saved teams and the ability log. */
@Composable
fun PetsScreen(
    state: UiState,
    viewModel: MainViewModel,
) {
    val session = state.activeSession
    ActivePetsCard(
        pets = session.pets,
        produce = session.inventory.produce,
        inventoryPets = session.inventory.pets,
        hutchPets = session.petHutch,
        apiReady = state.apiReady,
        showTip = Tip.PET in state.visibleTips,
        onDismissTip = { viewModel.dismissTip(Tip.PET) },
        potionsInInventory = session.inventory.tools
            .find { it.toolId == REPLENISH_POTION_ID }?.quantity ?: 0,
        potionsInShack = session.toolShack
            .find { it.toolId == REPLENISH_POTION_ID }?.quantity ?: 0,
        xpPotionsInInventory = session.inventory.tools
            .find { it.toolId == XP_POTION_ID }?.quantity ?: 0,
        xpPotionsInShack = session.toolShack
            .find { it.toolId == XP_POTION_ID }?.quantity ?: 0,
        strengthBonus = Crystals.effects(session.crystals).strengthBonus,
        onFeedPet = { petItemId, cropItemIds ->
            viewModel.pets.feedPet(session.id, petItemId, cropItemIds)
        },
        onUsePotionOnPet = { petItemId ->
            viewModel.pets.useReplenishPotionOnPet(session.id, petItemId)
        },
        onUseXpPotionOnPet = { petItemId ->
            viewModel.pets.useXpPotionOnPet(session.id, petItemId)
        },
        onSwapPet = { activePetId, targetPetId, isInHutch ->
            viewModel.pets.swapPet(session.id, activePetId, targetPetId, isInHutch)
        },
        onEquipPet = { targetPetId, isInHutch ->
            viewModel.pets.equipPet(session.id, targetPetId, isInHutch)
        },
        onUnequipPet = { petId ->
            viewModel.pets.unequipPet(session.id, petId)
        },
    )
    PetTeamCard(
        teams = session.petTeams,
        activePets = session.pets,
        inventoryPets = session.inventory.pets,
        hutchPets = session.petHutch,
        activeTeamId = remember(session.pets, session.petTeams) {
            viewModel.pets.detectActiveTeamId(session.id)
        },
        apiReady = state.apiReady,
        strengthBonus = Crystals.effects(session.crystals).strengthBonus,
        onCreate = { name, petIds -> viewModel.pets.createPetTeam(session.id, name, petIds) },
        onUpdate = { teamId, name, petIds ->
            viewModel.pets.updatePetTeam(session.id, teamId, name, petIds)
        },
        onDelete = { teamId -> viewModel.pets.deletePetTeam(session.id, teamId) },
        onReorder = { from, to -> viewModel.pets.reorderPetTeams(session.id, from, to) },
        onActivate = { team -> viewModel.pets.activateTeam(session.id, team) },
        showTip = Tip.TEAM in state.visibleTips,
        onDismissTip = { viewModel.dismissTip(Tip.TEAM) },
    )
    AbilityLogsCard(logs = session.logs, apiReady = state.apiReady, onClear = { viewModel.clearLogs(session.id) })
}
