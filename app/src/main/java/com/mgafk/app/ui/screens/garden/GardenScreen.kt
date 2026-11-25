package com.mgafk.app.ui.screens.garden

import androidx.compose.runtime.Composable
import com.mgafk.app.data.model.SessionStatus
import com.mgafk.app.data.model.Tip
import com.mgafk.app.data.repository.CropCleansers
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.repository.MutationPotions
import com.mgafk.app.data.repository.StorageCapacity
import com.mgafk.app.ui.MainViewModel
import com.mgafk.app.ui.UiState
import com.mgafk.app.ui.components.SectionTip

/** Crops, eggs and crystals planted in the garden. */
@Composable
fun GardenScreen(
    state: UiState,
    viewModel: MainViewModel,
) {
    val session = state.activeSession
    SectionTip(
        visible = Tip.GARDEN in state.visibleTips,
        text = "Tap any crop or egg to view its details and perform actions like watering, harvesting, potting and hatching.",
        onDismiss = { viewModel.dismissTip(Tip.GARDEN) },
    )
    GardenCard(
        plants = session.garden,
        apiReady = state.apiReady,
        gardenEggs = session.gardenEggs,
        onHarvest = { slot, slotIndex -> viewModel.garden.harvestCrop(session.id, slot, slotIndex) },
        onWater = { slot -> viewModel.garden.waterPlant(session.id, slot) },
        onPot = { slot -> viewModel.garden.potPlant(session.id, slot) },
        onCleanse = { tileId, slotIdx, toolId -> viewModel.garden.cropCleanse(session.id, tileId, slotIdx, toolId) },
        wateringCans = session.inventory.tools.find { it.toolId == "WateringCan" }?.quantity ?: 0,
        planterPots = session.inventory.tools.find { it.toolId == "PlanterPot" }?.quantity ?: 0,
        cropCleansers = CropCleansers.offers(session.inventory.tools, MgApi::findItem),
        mutationPotions = MutationPotions.offers(
            session.inventory.tools,
            session.toolShack,
            StorageCapacity.bagCount(session.inventory),
            MgApi::findItem,
        ),
        onApplyPotion = { tileId, slotIdx, potion ->
            when (potion.source) {
                MutationPotions.Source.INVENTORY ->
                    viewModel.garden.applyMutationPotion(session.id, tileId, slotIdx, potion.toolId, potion.mutation)
                MutationPotions.Source.STORAGE ->
                    viewModel.garden.applyMutationPotionFromStorage(session.id, tileId, slotIdx, potion.toolId, potion.mutation)
            }
        },
    )
    EggsCard(
        eggs = session.gardenEggs,
        apiReady = state.apiReady,
        onHatch = { slot -> viewModel.garden.hatchEgg(session.id, slot) },
        lastHatchedPet = session.lastHatchedPet,
        lastHatchedEggId = session.lastHatchedEggId,
        instantHatch = state.settings.instantHatch,
        onDismissHatchedPet = { viewModel.garden.clearHatchedPet(session.id) },
    )
    CrystalsCard(
        crystals = session.crystals,
        tools = session.inventory.tools,
        connected = session.status == SessionStatus.CONNECTED,
        crystalsReadAtMs = session.crystalsReadAtMs,
        occupiedTiles = session.occupiedTiles,
        apiReady = state.apiReady,
        onPlant = { type, ref ->
            viewModel.garden.placeCrystal(session.id, type, ref.tileType, ref.index)
        },
        onFuse = { crystal -> viewModel.garden.fuseCrystal(session.id, crystal) },
        onPickup = { crystal -> viewModel.garden.pickupCrystal(session.id, crystal) },
    )
}
