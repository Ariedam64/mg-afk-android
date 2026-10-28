package com.mgafk.app.ui.screens.storage

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.mgafk.app.data.model.PlantPlacementMode
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.MainViewModel
import com.mgafk.app.ui.UiState
import com.mgafk.app.ui.components.SectionTip
import com.mgafk.app.ui.screens.garden.ManualPlantTarget
import com.mgafk.app.ui.screens.garden.ManualPlantTileDialog

/** Inventory and the storage buildings placed in the garden. */
@Composable
fun StorageScreen(
    state: UiState,
    viewModel: MainViewModel,
) {
    val session = state.activeSession
    SectionTip(
        visible = state.showStorageTip,
        text = "Tap any item to view its details, lock/unlock it, or perform actions like planting, selling and more.",
        onDismiss = { viewModel.dismissStorageTip() },
    )
    val inv = session.inventory
    val totalInventoryItems = inv.seeds.size + inv.eggs.size + inv.produce.size +
        inv.plants.size + inv.pets.size + inv.tools.size + inv.decors.size
    val hutchMax = session.hutchCapacitySlots
    val siloMax = session.siloCapacitySlots
    val decorShedMax = session.decorShedCapacitySlots
    val toolShackMax = session.toolShackCapacitySlots
    // Storage cards and their "move to storage" buttons only make sense for
    // structures the player has actually placed in the garden.
    val hasSeedSilo = "SeedSilo" in session.availableStorages
    val hasDecorShed = "DecorShed" in session.availableStorages
    val hasPetHutch = "PetHutch" in session.availableStorages
    val hasFeedingTrough = "FeedingTrough" in session.availableStorages
    val hasToolShack = "ToolShack" in session.availableStorages
    val seedSiloSpecies = remember(session.seedSilo) { session.seedSilo.map { it.species }.toSet() }
    val decorShedIds = remember(session.decorShed) { session.decorShed.map { it.decorId }.toSet() }
    val toolShackToolIds = remember(session.toolShack) { session.toolShack.map { it.toolId }.toSet() }
    // Manual planting can either drop on the first free tile or ask the player to point
    // at one on the garden grid (see PlantPlacementMode). Nothing is remembered either
    // way - this is a one-shot placement, not auto-plant.
    val plantOnGrid = state.settings.plantPlacementMode == PlantPlacementMode.GRID
    var manualPlantTarget by remember { mutableStateOf<ManualPlantTarget?>(null) }

    val invSeedSpecies = remember(inv.seeds) { inv.seeds.map { it.species }.toSet() }
    val invDecorIds = remember(inv.decors) { inv.decors.map { it.decorId }.toSet() }
    val invToolIds = remember(inv.tools) { inv.tools.map { it.toolId }.toSet() }

    InventoryCard(
        inventory = session.inventory,
        apiReady = state.apiReady,
        freePlantTiles = session.freePlantTiles,
        favoritedItemIds = session.favoritedItemIds,
        hasPetHutch = hasPetHutch,
        petHutchCount = session.petHutch.size,
        petHutchMax = hutchMax,
        hasSeedSilo = hasSeedSilo,
        seedSiloCount = session.seedSilo.size,
        seedSiloMax = siloMax,
        seedSiloSpecies = seedSiloSpecies,
        hasDecorShed = hasDecorShed,
        decorShedCount = session.decorShed.size,
        decorShedMax = decorShedMax,
        decorShedDecorIds = decorShedIds,
        hasToolShack = hasToolShack,
        toolShackCount = session.toolShack.size,
        toolShackMax = toolShackMax,
        toolShackToolIds = toolShackToolIds,
        onPlantSeed = { species ->
            if (plantOnGrid) manualPlantTarget = ManualPlantTarget.Seed(species)
            else viewModel.plantSeed(session.id, species)
        },
        onGrowEgg = { eggId -> viewModel.growEgg(session.id, eggId) },
        onPlantGardenPlant = { itemId ->
            if (plantOnGrid) manualPlantTarget = ManualPlantTarget.Pot(itemId)
            else viewModel.plantGardenPlant(session.id, itemId)
        },
        onToggleLock = { itemId -> viewModel.toggleLockItem(session.id, itemId) },
        onSellPet = { itemId -> viewModel.sellPet(session.id, itemId) },
        onSellAllUnlockedPets = { itemIds -> viewModel.sellAllUnlockedPets(session.id, itemIds) },
        onSellAllCrops = { viewModel.sellAllCrops(session.id) },
        onSellCrop = { itemId -> viewModel.sellSingleCrop(session.id, itemId) },
        onMovePetToHutch = { petId -> viewModel.movePetToHutch(session.id, petId) },
        onMoveSeedToSilo = { species -> viewModel.moveSeedToSilo(session.id, species) },
        onMoveDecorToShed = { decorId -> viewModel.moveDecorToShed(session.id, decorId) },
        onMoveToolToShack = { toolId -> viewModel.moveToolToShack(session.id, toolId) },
        playerCount = session.players,
    )

    when (val target = manualPlantTarget) {
        null -> Unit
        is ManualPlantTarget.Seed -> {
            val entry = MgApi.findItem(target.species)
            ManualPlantTileDialog(
                species = target.species,
                displayName = entry?.name ?: target.species,
                spriteUrl = entry?.sprite,
                // Falls to 0 as the optimistic update empties the stack, closing the popup.
                remaining = inv.seeds.find { it.species == target.species }?.quantity ?: 0,
                garden = session.garden,
                gardenEggs = session.gardenEggs,
                // This build does not track garden decor, so decor tiles read as free
                // here and the server is what refuses one.
                decorSpriteByTile = emptyMap(),
                onPlantTile = { tileId ->
                    viewModel.plantSeed(session.id, target.species, tileId)
                },
                onDismiss = { manualPlantTarget = null },
            )
        }
        is ManualPlantTarget.Pot -> {
            val pot = inv.plants.find { it.id == target.itemId }
            val entry = pot?.let { MgApi.findItem(it.species) }
            ManualPlantTileDialog(
                species = null,
                displayName = entry?.name ?: pot?.species.orEmpty(),
                spriteUrl = entry?.cropSprite ?: entry?.sprite,
                remaining = if (pot != null) 1 else 0,
                garden = session.garden,
                gardenEggs = session.gardenEggs,
                decorSpriteByTile = emptyMap(),
                onPlantTile = { tileId ->
                    viewModel.plantGardenPlant(session.id, target.itemId, tileId)
                },
                onDismiss = { manualPlantTarget = null },
            )
        }
    }

    if (hasSeedSilo) {
        SeedSiloCard(seeds = session.seedSilo, apiReady = state.apiReady, favoritedItemIds = session.favoritedItemIds,
            inventorySeedSpecies = invSeedSpecies,
            inventoryItemCount = totalInventoryItems,
            magicDust = session.magicDust,
            capacitySlots = siloMax,
            onToggleLock = { itemId -> viewModel.toggleLockItem(session.id, itemId) },
            onMoveToInventory = { species -> viewModel.moveSeedFromSilo(session.id, species) },
            onUpgrade = { viewModel.upgradeSeedSilo(session.id) })
    }
    if (hasDecorShed) {
        DecorShedCard(decors = session.decorShed, apiReady = state.apiReady, favoritedItemIds = session.favoritedItemIds,
            inventoryDecorIds = invDecorIds,
            inventoryItemCount = totalInventoryItems,
            magicDust = session.magicDust,
            capacitySlots = decorShedMax,
            onToggleLock = { itemId -> viewModel.toggleLockItem(session.id, itemId) },
            onMoveToInventory = { decorId -> viewModel.moveDecorFromShed(session.id, decorId) },
            onUpgrade = { viewModel.upgradeDecorShed(session.id) })
    }
    if (hasToolShack) {
        ToolShackCard(tools = session.toolShack, apiReady = state.apiReady, favoritedItemIds = session.favoritedItemIds,
            inventoryToolIds = invToolIds,
            inventoryItemCount = totalInventoryItems,
            magicDust = session.magicDust,
            capacitySlots = toolShackMax,
            onToggleLock = { itemId -> viewModel.toggleLockItem(session.id, itemId) },
            onMoveToInventory = { toolId -> viewModel.moveToolFromShack(session.id, toolId) },
            onUpgrade = { viewModel.upgradeToolShack(session.id) })
    }
    if (hasPetHutch) {
        PetHutchCard(pets = session.petHutch, apiReady = state.apiReady, favoritedItemIds = session.favoritedItemIds,
            magicDust = session.magicDust,
            capacitySlots = hutchMax,
            inventoryItemCount = totalInventoryItems,
            onToggleLock = { itemId -> viewModel.toggleLockItem(session.id, itemId) },
            onSellPet = { itemId -> viewModel.sellPet(session.id, itemId) },
            onUpgrade = { viewModel.upgradePetHutch(session.id) },
            onMoveToInventory = { petId -> viewModel.movePetFromHutch(session.id, petId) })
    }
    if (hasFeedingTrough) {
        FeedingTroughCard(
            crops = session.feedingTrough,
            produce = session.inventory.produce,
            apiReady = state.apiReady,
            showTip = state.showTroughTip,
            onDismissTip = { viewModel.dismissTroughTip() },
            onAddItems = { items ->
                viewModel.putItemsInFeedingTrough(session.id, items)
            },
            onRemoveItem = { itemId ->
                viewModel.removeItemFromFeedingTrough(session.id, itemId)
            },
        )
    }
}
