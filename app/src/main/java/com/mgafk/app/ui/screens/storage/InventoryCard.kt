package com.mgafk.app.ui.screens.storage

import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.InventorySnapshot
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.data.repository.StorageCapacity
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.RarityFilterRow
import com.mgafk.app.data.repository.RaritySort
import com.mgafk.app.ui.components.GridOf
import com.mgafk.app.ui.components.LockOverlay

// ── Rarity colors ──



private fun itemRarity(itemId: String): String? = MgApi.findItem(itemId)?.rarity
private fun petRarity(species: String): String? = MgApi.findPet(species)?.rarity

internal fun fmtQty(q: Int): String = when {
    q >= 1_000_000 -> "%.1fM".format(q / 1_000_000.0).removeSuffix(".0M") + "M".takeIf { "M" !in "%.1fM".format(q / 1_000_000.0) }.orEmpty()
    q >= 10_000 -> "${q / 1000}K"
    q >= 1_000 -> "%.1fK".format(q / 1000.0)
    else -> "$q"
}

// ── Main ──

@Composable
fun InventoryCard(
    inventory: InventorySnapshot,
    apiReady: Boolean = false,
    freePlantTiles: Int = 0,
    favoritedItemIds: Set<String> = emptySet(),
    hasPetHutch: Boolean = false,
    petHutchCount: Int = 0,
    petHutchMax: Int = 25,
    hasSeedSilo: Boolean = false,
    seedSiloCount: Int = 0,
    seedSiloMax: Int = 25,
    seedSiloSpecies: Set<String> = emptySet(),
    hasDecorShed: Boolean = false,
    decorShedCount: Int = 0,
    decorShedMax: Int = 25,
    decorShedDecorIds: Set<String> = emptySet(),
    hasToolShack: Boolean = false,
    toolShackCount: Int = 0,
    toolShackMax: Int = 10,
    toolShackToolIds: Set<String> = emptySet(),
    onPlantSeed: (species: String) -> Unit = {},
    onGrowEgg: (eggId: String) -> Unit = {},
    onPlantGardenPlant: (itemId: String) -> Unit = {},
    onToggleLock: (itemId: String) -> Unit = {},
    onSellPet: (itemId: String) -> Unit = {},
    onSellAllUnlockedPets: (itemIds: List<String>) -> Unit = {},
    onSellAllCrops: () -> Unit = {},
    onSellCrop: (itemId: String) -> Unit = {},
    onMovePetToHutch: (petId: String) -> Unit = {},
    onMoveSeedToSilo: (species: String) -> Unit = {},
    onMoveDecorToShed: (decorId: String) -> Unit = {},
    onMoveToolToShack: (toolId: String) -> Unit = {},
    playerCount: Int = 1,
) {
    val totalItems = inventory.seeds.size + inventory.eggs.size + inventory.produce.size +
        inventory.plants.size + inventory.pets.size + inventory.tools.size + inventory.decors.size

    var selectedSeedSpecies by remember { mutableStateOf<String?>(null) }
    var selectedEggId by remember { mutableStateOf<String?>(null) }
    var selectedPlantId by remember { mutableStateOf<String?>(null) }
    var selectedToolId by remember { mutableStateOf<String?>(null) }
    var selectedProduceId by remember { mutableStateOf<String?>(null) }
    var selectedDecorId by remember { mutableStateOf<String?>(null) }
    var selectedPetId by remember { mutableStateOf<String?>(null) }
    var showSellAllPets by remember { mutableStateOf(false) }
    var showSellAllCrops by remember { mutableStateOf(false) }
    var selectedRarity by remember { mutableStateOf<String?>(null) }

    AppCard(title = "Inventory", collapsible = true, persistKey = "storage.inventory", trailing = {
        Text("$totalItems types", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = AppTheme.colors.accent.copy(0.7f))
    }) {
        if (totalItems == 0) {
            Text("No inventory data yet.", fontSize = 12.sp, color = AppTheme.colors.textMuted)
        } else {
            val sortedSeeds = remember(inventory.seeds, apiReady) { inventory.seeds.sortedBy { RaritySort.item(it.species) } }
            val sortedTools = remember(inventory.tools, apiReady) { inventory.tools.sortedBy { RaritySort.item(it.toolId) } }
            val sortedEggs = remember(inventory.eggs, apiReady) { inventory.eggs.sortedBy { RaritySort.item(it.eggId) } }
            val sortedPlants = remember(inventory.plants, apiReady) { inventory.plants.sortedBy { RaritySort.item(it.species) } }
            val sortedProduce = remember(inventory.produce, apiReady) { inventory.produce.sortedBy { RaritySort.item(it.species) } }
            val sortedDecors = remember(inventory.decors, apiReady) { inventory.decors.sortedBy { RaritySort.item(it.decorId) } }
            val sortedPets = remember(inventory.pets, apiReady) { inventory.pets.sortedBy { RaritySort.pet(it.petSpecies) } }

            val allRarities = remember(sortedSeeds, sortedTools, sortedEggs, sortedPlants, sortedProduce, sortedDecors, sortedPets, apiReady) {
                val present = buildSet {
                    sortedSeeds.forEach { itemRarity(it.species)?.let(::add) }
                    sortedTools.forEach { itemRarity(it.toolId)?.let(::add) }
                    sortedEggs.forEach { itemRarity(it.eggId)?.let(::add) }
                    sortedPlants.forEach { itemRarity(it.species)?.let(::add) }
                    sortedProduce.forEach { itemRarity(it.species)?.let(::add) }
                    sortedDecors.forEach { itemRarity(it.decorId)?.let(::add) }
                    sortedPets.forEach { petRarity(it.petSpecies)?.let(::add) }
                }
                MgApi.RARITY_ORDER.filter { it in present }
            }
            val filteredSeeds = remember(sortedSeeds, selectedRarity) {
                if (selectedRarity == null) sortedSeeds else sortedSeeds.filter { itemRarity(it.species).equals(selectedRarity, ignoreCase = true) }
            }
            val filteredTools = remember(sortedTools, selectedRarity) {
                if (selectedRarity == null) sortedTools else sortedTools.filter { itemRarity(it.toolId).equals(selectedRarity, ignoreCase = true) }
            }
            val filteredEggs = remember(sortedEggs, selectedRarity) {
                if (selectedRarity == null) sortedEggs else sortedEggs.filter { itemRarity(it.eggId).equals(selectedRarity, ignoreCase = true) }
            }
            val filteredPlants = remember(sortedPlants, selectedRarity) {
                if (selectedRarity == null) sortedPlants else sortedPlants.filter { itemRarity(it.species).equals(selectedRarity, ignoreCase = true) }
            }
            val filteredProduce = remember(sortedProduce, selectedRarity) {
                if (selectedRarity == null) sortedProduce else sortedProduce.filter { itemRarity(it.species).equals(selectedRarity, ignoreCase = true) }
            }
            val filteredDecors = remember(sortedDecors, selectedRarity) {
                if (selectedRarity == null) sortedDecors else sortedDecors.filter { itemRarity(it.decorId).equals(selectedRarity, ignoreCase = true) }
            }
            val filteredPets = remember(sortedPets, selectedRarity) {
                if (selectedRarity == null) sortedPets else sortedPets.filter { petRarity(it.petSpecies).equals(selectedRarity, ignoreCase = true) }
            }

            RarityFilterRow(rarities = allRarities, selected = selectedRarity, onSelect = { selectedRarity = it })
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (filteredSeeds.isNotEmpty()) SubSection("Seeds", filteredSeeds.size) {
                    GridOf(filteredSeeds.size) { i ->
                        Box(modifier = Modifier.clickable { selectedSeedSpecies = filteredSeeds[i].species }) {
                            LockOverlay(isLocked = filteredSeeds[i].species in favoritedItemIds) {
                                QuantityTile(filteredSeeds[i].species, filteredSeeds[i].quantity, apiReady)
                            }
                        }
                    }
                }
                if (filteredTools.isNotEmpty()) SubSection("Tools", filteredTools.size) {
                    GridOf(filteredTools.size) { i ->
                        Box(modifier = Modifier.clickable { selectedToolId = filteredTools[i].storageKey }) {
                            LockOverlay(isLocked = filteredTools[i].toolId in favoritedItemIds) {
                                QuantityTile(filteredTools[i].toolId, filteredTools[i].quantity, apiReady)
                            }
                        }
                    }
                }
                if (filteredEggs.isNotEmpty()) SubSection("Eggs", filteredEggs.size) {
                    GridOf(filteredEggs.size) { i ->
                        Box(modifier = Modifier.clickable { selectedEggId = filteredEggs[i].eggId }) {
                            LockOverlay(isLocked = filteredEggs[i].eggId in favoritedItemIds) {
                                QuantityTile(filteredEggs[i].eggId, filteredEggs[i].quantity, apiReady)
                            }
                        }
                    }
                }
                if (filteredPlants.isNotEmpty()) {
                    val totalPlantsValue = remember(filteredPlants) { filteredPlants.sumOf { it.totalPrice } }
                    SubSection("Plants", filteredPlants.size, extraInfo = if (totalPlantsValue > 0) PriceCalculator.formatPrice(totalPlantsValue) else null) {
                        GridOf(filteredPlants.size) { i ->
                            val pl = filteredPlants[i]
                            Box(modifier = Modifier.clickable { selectedPlantId = pl.id }) {
                                LockOverlay(isLocked = pl.id in favoritedItemIds || pl.species in favoritedItemIds) {
                                    PlantTile(pl, apiReady)
                                }
                            }
                        }
                    }
                }
                if (filteredProduce.isNotEmpty()) {
                    val totalProduceValue = remember(filteredProduce, apiReady, playerCount) {
                        filteredProduce.sumOf { p ->
                            PriceCalculator.calculateCropSellPrice(p.species, p.size, p.mutations, playerCount) ?: 0L
                        }
                    }
                    // Scoped to the full (unfiltered) inventory - matches the sell-all dialog
                    // below, which always sells every unlocked crop regardless of the rarity
                    // filter currently narrowing what's on screen.
                    val unlockedProduce = remember(sortedProduce, favoritedItemIds) {
                        sortedProduce.filter { it.id !in favoritedItemIds && it.species !in favoritedItemIds }
                    }
                    SubSection("Produce", filteredProduce.size, extraInfo = if (totalProduceValue > 0) PriceCalculator.formatPrice(totalProduceValue) else null) {
                        GridOf(filteredProduce.size) { i ->
                            val p = filteredProduce[i]
                            Box(modifier = Modifier.clickable { selectedProduceId = p.id }) {
                                LockOverlay(isLocked = p.id in favoritedItemIds || p.species in favoritedItemIds) {
                                    ProduceTile(p, apiReady, playerCount)
                                }
                            }
                        }
                        if (unlockedProduce.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showSellAllCrops = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.bad),
                                shape = AppTheme.shapes.smallShape,
                            ) {
                                Text(
                                    "Sell All Crops (${unlockedProduce.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.onBad,
                                )
                            }
                        }
                    }
                }
                if (filteredDecors.isNotEmpty()) SubSection("Decors", filteredDecors.size) {
                    GridOf(filteredDecors.size) { i ->
                        Box(modifier = Modifier.clickable { selectedDecorId = filteredDecors[i].decorId }) {
                            LockOverlay(isLocked = filteredDecors[i].decorId in favoritedItemIds) {
                                QuantityTile(filteredDecors[i].decorId, filteredDecors[i].quantity, apiReady)
                            }
                        }
                    }
                }
                if (filteredPets.isNotEmpty()) {
                    // Scoped to the full (unfiltered) inventory - matches the sell-all dialog
                    // below, which always sells every unlocked pet regardless of the rarity
                    // filter currently narrowing what's on screen.
                    val unlockedPets = remember(sortedPets, favoritedItemIds) {
                        sortedPets.filter { it.id !in favoritedItemIds && it.petSpecies !in favoritedItemIds }
                    }
                    SubSection("Pets", filteredPets.size) {
                        PetsList(filteredPets, apiReady, favoritedItemIds, onPetClick = { selectedPetId = it })
                        if (unlockedPets.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showSellAllPets = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.bad),
                                shape = AppTheme.shapes.smallShape,
                            ) {
                                Text(
                                    "Sell All Unlocked (${unlockedPets.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.onBad,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Seed detail dialog - look up live data from inventory so quantity updates in real-time
    selectedSeedSpecies?.let { species ->
        val liveSeed = inventory.seeds.find { it.species == species }
        if (liveSeed != null) {
            SeedDetailDialog(
                seed = liveSeed,
                apiReady = apiReady,
                freePlantTiles = freePlantTiles,
                isLocked = species in favoritedItemIds,
                hasSeedSilo = hasSeedSilo,
                canMoveToSilo = StorageCapacity.canAddStackable(
                    currentCount = seedSiloCount,
                    max = seedSiloMax,
                    stackExists = species in seedSiloSpecies,
                ),
                onPlantSeed = { onPlantSeed(species) },
                onToggleLock = { onToggleLock(species) },
                onMoveToSilo = {
                    onMoveSeedToSilo(species)
                    selectedSeedSpecies = null
                },
                onDismiss = { selectedSeedSpecies = null },
            )
        } else {
            // Seed was fully consumed - close dialog
            selectedSeedSpecies = null
        }
    }

    // Egg detail dialog - live lookup
    selectedEggId?.let { eggId ->
        val liveEgg = inventory.eggs.find { it.eggId == eggId }
        if (liveEgg != null) {
            EggGrowDialog(
                egg = liveEgg,
                apiReady = apiReady,
                freePlantTiles = freePlantTiles,
                isLocked = eggId in favoritedItemIds,
                onGrowEgg = { onGrowEgg(eggId) },
                onToggleLock = { onToggleLock(eggId) },
                onDismiss = { selectedEggId = null },
            )
        } else {
            selectedEggId = null
        }
    }

    // Plant detail dialog - live lookup
    selectedPlantId?.let { plantId ->
        val livePlant = inventory.plants.find { it.id == plantId }
        if (livePlant != null) {
            val plantLockId = if (livePlant.id in favoritedItemIds) livePlant.id
                else if (livePlant.species in favoritedItemIds) livePlant.species
                else livePlant.id
            PlantUnpotDialog(
                plant = livePlant,
                apiReady = apiReady,
                freePlantTiles = freePlantTiles,
                isLocked = plantLockId in favoritedItemIds,
                onPlant = {
                    onPlantGardenPlant(plantId)
                    selectedPlantId = null
                },
                onToggleLock = { onToggleLock(plantLockId) },
                onDismiss = { selectedPlantId = null },
            )
        } else {
            selectedPlantId = null
        }
    }

    // Tool detail dialog
    selectedToolId?.let { toolKey ->
        val liveTool = inventory.tools.find { it.storageKey == toolKey }
        if (liveTool != null) {
            val toolId = liveTool.toolId
            val canMoveToShack = StorageCapacity.canAddStackable(
                currentCount = toolShackCount,
                max = toolShackMax,
                // A tool the game tracks individually cannot merge into a stack: it needs a
                // slot of its own, so the shack has to have one free.
                stackExists = liveTool.isStackable && toolId in toolShackToolIds,
            )
            ItemDetailDialog(
                itemId = toolId,
                apiReady = apiReady,
                quantity = liveTool.quantity,
                isLocked = toolId in favoritedItemIds,
                onToggleLock = { onToggleLock(toolId) },
                onDismiss = { selectedToolId = null },
                extraContent = if (!hasToolShack) null else ({
                    Button(
                        onClick = {
                            onMoveToolToShack(liveTool.storageKey)
                            selectedToolId = null
                        },
                        enabled = canMoveToShack,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.accent,
                            disabledContainerColor = AppTheme.colors.surfaceHigh,
                        ),
                        shape = AppTheme.shapes.smallShape,
                    ) {
                        Text(
                            if (canMoveToShack) "Move to Tool Shack" else "Tool Shack full",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = if (canMoveToShack) AppTheme.colors.onAccent else AppTheme.colors.textMuted,
                        )
                    }
                }),
            )
        } else {
            selectedToolId = null
        }
    }

    // Produce detail dialog
    selectedProduceId?.let { produceId ->
        val liveProduce = inventory.produce.find { it.id == produceId }
        if (liveProduce != null) {
            val produceLockId = if (liveProduce.id in favoritedItemIds) liveProduce.id
                else if (liveProduce.species in favoritedItemIds) liveProduce.species
                else liveProduce.id
            ProduceDetailDialog(
                item = liveProduce,
                apiReady = apiReady,
                isLocked = produceLockId in favoritedItemIds,
                playerCount = playerCount,
                onToggleLock = { onToggleLock(produceLockId) },
                onSell = {
                    onSellCrop(produceId)
                    selectedProduceId = null
                },
                onDismiss = { selectedProduceId = null },
            )
        } else {
            selectedProduceId = null
        }
    }

    // Decor detail dialog
    selectedDecorId?.let { decorId ->
        val liveDecor = inventory.decors.find { it.decorId == decorId }
        if (liveDecor != null) {
            val canMoveToShed = StorageCapacity.canAddStackable(
                currentCount = decorShedCount,
                max = decorShedMax,
                stackExists = decorId in decorShedDecorIds,
            )
            ItemDetailDialog(
                itemId = decorId,
                apiReady = apiReady,
                quantity = liveDecor.quantity,
                isLocked = decorId in favoritedItemIds,
                onToggleLock = { onToggleLock(decorId) },
                onDismiss = { selectedDecorId = null },
                extraContent = if (!hasDecorShed) null else ({
                    Button(
                        onClick = {
                            onMoveDecorToShed(decorId)
                            selectedDecorId = null
                        },
                        enabled = canMoveToShed,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.accent,
                            disabledContainerColor = AppTheme.colors.surfaceHigh,
                        ),
                        shape = AppTheme.shapes.smallShape,
                    ) {
                        Text(
                            if (canMoveToShed) "Move to Decor Shed" else "Decor Shed full",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = if (canMoveToShed) AppTheme.colors.onAccent else AppTheme.colors.textMuted,
                        )
                    }
                }),
            )
        } else {
            selectedDecorId = null
        }
    }

    // Pet detail dialog
    selectedPetId?.let { petId ->
        val livePet = inventory.pets.find { it.id == petId }
        if (livePet != null) {
            val petLockId = if (livePet.id in favoritedItemIds) livePet.id
                else if (livePet.petSpecies in favoritedItemIds) livePet.petSpecies
                else livePet.id
            PetDetailDialog(
                pet = livePet,
                apiReady = apiReady,
                isLocked = petLockId in favoritedItemIds,
                hasPetHutch = hasPetHutch,
                canMoveToHutch = StorageCapacity.hasFreeSlot(petHutchCount, petHutchMax),
                onToggleLock = { onToggleLock(petLockId) },
                onSell = {
                    onSellPet(petId)
                    selectedPetId = null
                },
                onMoveToHutch = {
                    onMovePetToHutch(petId)
                    selectedPetId = null
                },
                onDismiss = { selectedPetId = null },
            )
        } else {
            selectedPetId = null
        }
    }

    // Sell all unlocked pets dialog
    if (showSellAllPets) {
        val unlockedPets = inventory.pets.filter {
            it.id !in favoritedItemIds && it.petSpecies !in favoritedItemIds
        }
        if (unlockedPets.isNotEmpty()) {
            SellAllPetsDialog(
                pets = unlockedPets,
                apiReady = apiReady,
                onConfirm = {
                    onSellAllUnlockedPets(unlockedPets.map { it.id })
                    showSellAllPets = false
                },
                onDismiss = { showSellAllPets = false },
            )
        } else {
            showSellAllPets = false
        }
    }

    // Sell all crops dialog
    if (showSellAllCrops) {
        val unlockedProduce = inventory.produce.filter {
            it.id !in favoritedItemIds && it.species !in favoritedItemIds
        }
        if (unlockedProduce.isNotEmpty()) {
            SellAllCropsDialog(
                produce = unlockedProduce,
                apiReady = apiReady,
                playerCount = playerCount,
                onConfirm = {
                    onSellAllCrops()
                    showSellAllCrops = false
                },
                onDismiss = { showSellAllCrops = false },
            )
        } else {
            showSellAllCrops = false
        }
    }
}

// ── Sub-section with toggle ──

@Composable
private fun SubSection(label: String, count: Int, extraInfo: String? = null, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(label) { mutableStateOf(true) }

    HorizontalDivider(color = AppTheme.colors.surfaceHigh.copy(0.5f), thickness = 0.5.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        if (extraInfo != null) {
            Text(extraInfo, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFFD700))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text("$count", fontSize = 11.sp, color = AppTheme.colors.textMuted)
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            Icons.Default.ExpandMore, contentDescription = null,
            tint = AppTheme.colors.textMuted, modifier = Modifier.rotate(if (expanded) 0f else -90f),
        )
    }
    if (expanded) {
        content()
        Spacer(modifier = Modifier.height(4.dp))
    }
}
