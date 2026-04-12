package com.mgafk.app.ui.screens.storage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.InventoryDecorItem
import com.mgafk.app.data.model.InventoryEggItem
import com.mgafk.app.data.model.InventoryPetItem
import com.mgafk.app.data.model.InventoryPlantItem
import com.mgafk.app.data.model.InventoryPlantSlot
import com.mgafk.app.data.model.InventoryProduceItem
import com.mgafk.app.data.model.InventorySeedItem
import com.mgafk.app.data.model.InventorySnapshot
import com.mgafk.app.data.model.InventoryToolItem
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary
import com.mgafk.app.ui.components.mutationSpriteUrl
import com.mgafk.app.ui.components.sortMutations
import androidx.compose.ui.window.Dialog

// ── Rarity colors ──

private val RarityCommon = Color(0xFFE7E7E7)
private val RarityUncommon = Color(0xFF67BD4D)
private val RarityRare = Color(0xFF0071C6)
private val RarityLegendary = Color(0xFFFFD700)
private val RarityMythical = Color(0xFF9944A7)
private val RarityDivine = Color(0xFFFF7835)
private val RarityCelestial = Color(0xFFFF00FF)

private fun rarityColor(rarity: String?): Color = when (rarity?.lowercase()) {
    "common" -> RarityCommon
    "uncommon" -> RarityUncommon
    "rare" -> RarityRare
    "legendary" -> RarityLegendary
    "mythical", "mythic" -> RarityMythical
    "divine" -> RarityDivine
    "celestial" -> RarityCelestial
    else -> TextMuted
}

private val TILE_MIN_WIDTH = 76.dp
private val TILE_SPACING = 6.dp

// ── Strength (from game source) ──

private const val XP_PER_HOUR = 3600.0
private const val BASE_STR = 80
private const val MAX_STR = 100
private const val STR_GAINED = 30

private fun maxStr(species: String, scale: Double): Int {
    val ms = MgApi.findPet(species)?.maxScale ?: return BASE_STR
    if (scale <= 1.0) return BASE_STR
    if (scale >= ms) return MAX_STR
    return (BASE_STR + 20 * (scale - 1.0) / (ms - 1.0)).toInt()
}

private fun curStr(species: String, xp: Double, max: Int): Int {
    val htm = MgApi.findPet(species)?.hoursToMature ?: return max - STR_GAINED
    val gained = minOf(STR_GAINED / htm * (xp / XP_PER_HOUR), STR_GAINED.toDouble())
    return ((max - STR_GAINED) + gained).toInt()
}

// ── Size percent ──

private fun sizePercent(scale: Double, maxScale: Double): Double {
    if (maxScale <= 1.0) return if (scale >= 1.0) 100.0 else scale * 100.0
    return if (scale <= 1.0) scale * 50.0
    else (50.0 + (scale - 1.0) / (maxScale - 1.0) * 50.0).coerceIn(0.0, 100.0)
}

private val RARITY_ORDER = listOf("Celestial", "Divine", "Mythic", "Mythical", "Legendary", "Rare", "Uncommon", "Common")

/** Rarity index for sorting (lower = rarer = first). Unknown rarities go last. */
private fun raritySort(itemId: String): Int {
    val rarity = MgApi.findItem(itemId)?.rarity ?: return RARITY_ORDER.size
    return RARITY_ORDER.indexOfFirst { it.equals(rarity, ignoreCase = true) }.let { if (it < 0) RARITY_ORDER.size else it }
}

private fun raritySortPet(species: String): Int {
    val rarity = MgApi.findPet(species)?.rarity ?: return RARITY_ORDER.size
    return RARITY_ORDER.indexOfFirst { it.equals(rarity, ignoreCase = true) }.let { if (it < 0) RARITY_ORDER.size else it }
}

private fun fmtQty(q: Int): String = when {
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
    onPlantSeed: (species: String) -> Unit = {},
    onGrowEgg: (eggId: String) -> Unit = {},
    onPlantGardenPlant: (itemId: String) -> Unit = {},
    onToggleLock: (itemId: String) -> Unit = {},
    onSellPet: (itemId: String) -> Unit = {},
    onSellAllUnlockedPets: (itemIds: List<String>) -> Unit = {},
    onSellAllCrops: () -> Unit = {},
    onSellCrop: (itemId: String) -> Unit = {},
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

    AppCard(title = "Inventory", collapsible = true, persistKey = "storage.inventory", trailing = {
        Text("$totalItems types", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Accent.copy(0.7f))
    }) {
        if (totalItems == 0) {
            Text("No inventory data yet.", fontSize = 12.sp, color = TextMuted)
        } else {
            val sortedSeeds = remember(inventory.seeds, apiReady) { inventory.seeds.sortedBy { raritySort(it.species) } }
            val sortedTools = remember(inventory.tools, apiReady) { inventory.tools.sortedBy { raritySort(it.toolId) } }
            val sortedEggs = remember(inventory.eggs, apiReady) { inventory.eggs.sortedBy { raritySort(it.eggId) } }
            val sortedPlants = remember(inventory.plants, apiReady) { inventory.plants.sortedBy { raritySort(it.species) } }
            val sortedProduce = remember(inventory.produce, apiReady) { inventory.produce.sortedBy { raritySort(it.species) } }
            val sortedDecors = remember(inventory.decors, apiReady) { inventory.decors.sortedBy { raritySort(it.decorId) } }
            val sortedPets = remember(inventory.pets, apiReady) { inventory.pets.sortedBy { raritySortPet(it.petSpecies) } }

            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (sortedSeeds.isNotEmpty()) SubSection("Seeds", sortedSeeds.size) {
                    GridOf(sortedSeeds.size) { i ->
                        Box(modifier = Modifier.clickable { selectedSeedSpecies = sortedSeeds[i].species }) {
                            LockOverlay(isLocked = sortedSeeds[i].species in favoritedItemIds) {
                                QuantityTile(sortedSeeds[i].species, sortedSeeds[i].quantity, apiReady)
                            }
                        }
                    }
                }
                if (sortedTools.isNotEmpty()) SubSection("Tools", sortedTools.size) {
                    GridOf(sortedTools.size) { i ->
                        Box(modifier = Modifier.clickable { selectedToolId = sortedTools[i].toolId }) {
                            LockOverlay(isLocked = sortedTools[i].toolId in favoritedItemIds) {
                                QuantityTile(sortedTools[i].toolId, sortedTools[i].quantity, apiReady)
                            }
                        }
                    }
                }
                if (sortedEggs.isNotEmpty()) SubSection("Eggs", sortedEggs.size) {
                    GridOf(sortedEggs.size) { i ->
                        Box(modifier = Modifier.clickable { selectedEggId = sortedEggs[i].eggId }) {
                            LockOverlay(isLocked = sortedEggs[i].eggId in favoritedItemIds) {
                                QuantityTile(sortedEggs[i].eggId, sortedEggs[i].quantity, apiReady)
                            }
                        }
                    }
                }
                if (sortedPlants.isNotEmpty()) {
                    val totalPlantsValue = remember(sortedPlants) { sortedPlants.sumOf { it.totalPrice } }
                    SubSection("Plants", sortedPlants.size, extraInfo = if (totalPlantsValue > 0) PriceCalculator.formatPrice(totalPlantsValue) else null) {
                        GridOf(sortedPlants.size) { i ->
                            val pl = sortedPlants[i]
                            Box(modifier = Modifier.clickable { selectedPlantId = pl.id }) {
                                LockOverlay(isLocked = pl.id in favoritedItemIds || pl.species in favoritedItemIds) {
                                    PlantTile(pl, apiReady)
                                }
                            }
                        }
                    }
                }
                if (sortedProduce.isNotEmpty()) {
                    val totalProduceValue = remember(sortedProduce, apiReady, playerCount) {
                        sortedProduce.sumOf { p ->
                            PriceCalculator.calculateCropSellPrice(p.species, p.scale, p.mutations, playerCount) ?: 0L
                        }
                    }
                    val unlockedProduce = remember(sortedProduce, favoritedItemIds) {
                        sortedProduce.filter { it.id !in favoritedItemIds && it.species !in favoritedItemIds }
                    }
                    SubSection("Produce", sortedProduce.size, extraInfo = if (totalProduceValue > 0) PriceCalculator.formatPrice(totalProduceValue) else null) {
                        GridOf(sortedProduce.size) { i ->
                            val p = sortedProduce[i]
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(
                                    "Sell All Crops (${unlockedProduce.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
                if (sortedDecors.isNotEmpty()) SubSection("Decors", sortedDecors.size) {
                    GridOf(sortedDecors.size) { i ->
                        Box(modifier = Modifier.clickable { selectedDecorId = sortedDecors[i].decorId }) {
                            LockOverlay(isLocked = sortedDecors[i].decorId in favoritedItemIds) {
                                QuantityTile(sortedDecors[i].decorId, sortedDecors[i].quantity, apiReady)
                            }
                        }
                    }
                }
                if (sortedPets.isNotEmpty()) {
                    val unlockedPets = remember(sortedPets, favoritedItemIds) {
                        sortedPets.filter { it.id !in favoritedItemIds && it.petSpecies !in favoritedItemIds }
                    }
                    SubSection("Pets", sortedPets.size) {
                        PetsList(sortedPets, apiReady, favoritedItemIds, onPetClick = { selectedPetId = it })
                        if (unlockedPets.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showSellAllPets = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(
                                    "Sell All Unlocked (${unlockedPets.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Seed detail dialog — look up live data from inventory so quantity updates in real-time
    selectedSeedSpecies?.let { species ->
        val liveSeed = inventory.seeds.find { it.species == species }
        if (liveSeed != null) {
            SeedDetailDialog(
                seed = liveSeed,
                apiReady = apiReady,
                freePlantTiles = freePlantTiles,
                isLocked = species in favoritedItemIds,
                onPlantSeed = { onPlantSeed(species) },
                onToggleLock = { onToggleLock(species) },
                onDismiss = { selectedSeedSpecies = null },
            )
        } else {
            // Seed was fully consumed — close dialog
            selectedSeedSpecies = null
        }
    }

    // Egg detail dialog — live lookup
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

    // Plant detail dialog — live lookup
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
    selectedToolId?.let { toolId ->
        val liveTool = inventory.tools.find { it.toolId == toolId }
        if (liveTool != null) {
            ItemDetailDialog(
                itemId = toolId,
                apiReady = apiReady,
                quantity = liveTool.quantity,
                isLocked = toolId in favoritedItemIds,
                onToggleLock = { onToggleLock(toolId) },
                onDismiss = { selectedToolId = null },
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
            ItemDetailDialog(
                itemId = decorId,
                apiReady = apiReady,
                quantity = liveDecor.quantity,
                isLocked = decorId in favoritedItemIds,
                onToggleLock = { onToggleLock(decorId) },
                onDismiss = { selectedDecorId = null },
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
                onToggleLock = { onToggleLock(petLockId) },
                onSell = {
                    onSellPet(petId)
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

    HorizontalDivider(color = SurfaceBorder.copy(0.5f), thickness = 0.5.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, modifier = Modifier.weight(1f))
        if (extraInfo != null) {
            Text(extraInfo, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFFD700))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text("$count", fontSize = 11.sp, color = TextMuted)
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            Icons.Default.ExpandMore, contentDescription = null,
            tint = TextMuted, modifier = Modifier.rotate(if (expanded) 0f else -90f),
        )
    }
    if (expanded) {
        content()
        Spacer(modifier = Modifier.height(4.dp))
    }
}

// ── Quantity tile (seeds, eggs, tools, decors) ──

@Composable
private fun QuantityTile(itemId: String, quantity: Int, apiReady: Boolean) {
    val entry = remember(itemId, apiReady) { MgApi.findItem(itemId) }
    val name = entry?.name ?: itemId
    val sprite = entry?.sprite
    val color = rarityColor(entry?.rarity)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SpriteImage(url = sprite, size = 28.dp, contentDescription = name)
        Spacer(modifier = Modifier.height(2.dp))
        Text(name, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 10.sp)
        Text(fmtQty(quantity), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Accent, lineHeight = 11.sp)
    }
}

// ── Produce tile ──

@Composable
private fun ProduceTile(item: InventoryProduceItem, apiReady: Boolean, playerCount: Int = 1) {
    val entry = remember(item.species, apiReady) { MgApi.findItem(item.species) }
    val color = rarityColor(entry?.rarity)
    val maxS = entry?.maxScale ?: 1.0
    val pct = sizePercent(item.scale, maxS)
    val fraction = (pct / 100.0).toFloat().coerceIn(0f, 1f)
    val name = entry?.name?.removeSuffix(" Seed") ?: item.species
    val price = remember(item.species, item.scale, item.mutations, apiReady, playerCount) {
        PriceCalculator.calculateCropSellPrice(item.species, item.scale, item.mutations, playerCount)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth().aspectRatio(0.85f)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        SpriteImage(url = entry?.cropSprite, size = 28.dp, contentDescription = name)
        Text(name, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 10.sp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color.copy(0.15f))) {
                Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(color.copy(0.8f)))
            }
            Spacer(Modifier.width(3.dp))
            Text("${pct.toInt()}%", fontSize = 7.sp, color = TextSecondary, fontWeight = FontWeight.Medium, lineHeight = 8.sp)
        }
        if (price != null) {
            Text(PriceCalculator.formatPrice(price), fontSize = 8.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD700), lineHeight = 10.sp)
        }
        if (item.mutations.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                sortMutations(item.mutations).take(4).forEach { SpriteImage(url = mutationSpriteUrl(it), size = 16.dp, contentDescription = it) }
            }
        }
    }
}

// ── Plant tile ──

@Composable
private fun PlantTile(item: InventoryPlantItem, apiReady: Boolean) {
    val entry = remember(item.species, apiReady) { MgApi.findItem(item.species) }
    val name = entry?.name?.removeSuffix(" Seed") ?: item.species
    val color = rarityColor(entry?.rarity)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (item.totalPrice > 0) 0.85f else 1f)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SpriteImage(url = entry?.cropSprite, size = 28.dp, contentDescription = name)
        Spacer(modifier = Modifier.height(2.dp))
        Text(name, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 10.sp)
        Text("${item.growSlots} slots", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Accent, lineHeight = 11.sp)
        if (item.totalPrice > 0) {
            Text(PriceCalculator.formatPrice(item.totalPrice), fontSize = 8.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD700), lineHeight = 10.sp)
        }
    }
}

// ── Pets grid (compact tiles, same style as pet selector) ──

@Composable
private fun PetsList(
    pets: List<InventoryPetItem>,
    apiReady: Boolean,
    favoritedItemIds: Set<String> = emptySet(),
    onPetClick: (String) -> Unit = {},
) {
    GridOf(count = pets.size) { i ->
        val pet = pets[i]
        Box(modifier = Modifier.clickable { onPetClick(pet.id) }) {
            LockOverlay(isLocked = pet.id in favoritedItemIds || pet.petSpecies in favoritedItemIds) {
                PetTile(pet, apiReady)
            }
        }
    }
}

@Composable
private fun PetTile(pet: InventoryPetItem, apiReady: Boolean) {
    val entry = remember(pet.petSpecies, apiReady) { MgApi.findPet(pet.petSpecies) }
    val name = pet.name?.ifBlank { null } ?: entry?.name ?: pet.petSpecies
    val borderColor = rarityColor(entry?.rarity).copy(alpha = 0.5f)
    val ms = maxStr(pet.petSpecies, pet.targetScale)
    val cs = curStr(pet.petSpecies, pet.xp, ms)
    val isMax = cs >= ms

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
            .background(SurfaceDark),
    ) {
        // Mutation icons top-left
        if (pet.mutations.isNotEmpty()) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                sortMutations(pet.mutations).take(2).forEach {
                    SpriteImage(url = mutationSpriteUrl(it), size = 12.dp, contentDescription = it)
                }
            }
        }
        // Center content
        Column(
            modifier = Modifier.align(Alignment.Center).padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpriteImage(category = "pets", name = pet.petSpecies, size = 28.dp, contentDescription = pet.petSpecies)
            Text(
                name, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 10.sp,
            )
            // STR centered
            if (ms > 0) {
                val strText = if (isMax) "$cs" else "$cs/$ms"
                val strColor = if (isMax) Color(0xFFFBBF24) else Accent
                Text(strText, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = strColor, lineHeight = 9.sp)
            }
            if (pet.abilities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    pet.abilities.forEach { abilityId ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(abilityColor(abilityId)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PetTileWithPrice(pet: InventoryPetItem, apiReady: Boolean, price: Long) {
    val entry = remember(pet.petSpecies, apiReady) { MgApi.findPet(pet.petSpecies) }
    val name = pet.name?.ifBlank { null } ?: entry?.name ?: pet.petSpecies
    val borderColor = rarityColor(entry?.rarity).copy(alpha = 0.5f)
    val ms = maxStr(pet.petSpecies, pet.targetScale)
    val cs = curStr(pet.petSpecies, pet.xp, ms)
    val isMax = cs >= ms

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
            .background(SurfaceDark),
    ) {
        // Mutation icons top-left
        if (pet.mutations.isNotEmpty()) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                sortMutations(pet.mutations).take(2).forEach {
                    SpriteImage(url = mutationSpriteUrl(it), size = 12.dp, contentDescription = it)
                }
            }
        }
        // Center content
        Column(
            modifier = Modifier.align(Alignment.Center).padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpriteImage(category = "pets", name = pet.petSpecies, size = 24.dp, contentDescription = pet.petSpecies)
            Text(
                name, fontSize = 7.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 9.sp,
            )
            if (ms > 0) {
                val strText = if (isMax) "$cs" else "$cs/$ms"
                val strColor = if (isMax) Color(0xFFFBBF24) else Accent
                Text(strText, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = strColor, lineHeight = 9.sp)
            }
            if (pet.abilities.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    pet.abilities.forEach { abilityId ->
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(abilityColor(abilityId)),
                        )
                    }
                }
            }
            if (price > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    SpriteImage(url = COIN_BAG_URL, size = 9.dp, contentDescription = "coins")
                    Text(
                        PriceCalculator.formatPrice(price),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                        lineHeight = 9.sp,
                    )
                }
            }
        }
    }
}

private fun abilityColor(abilityId: String): Color {
    val id = abilityId.lowercase().replace(Regex("[\\s_-]+"), "")
    return when {
        id.startsWith("moonkisser") -> Color(0xFFFAA623)
        id.startsWith("dawnkisser") -> Color(0xFFA25CF2)
        id.startsWith("producescaleboost") || id.startsWith("snowycropsizeboost") -> Color(0xFF228B22)
        id.startsWith("plantgrowthboost") || id.startsWith("snowyplantgrowthboost") ||
            id.startsWith("dawnplantgrowthboost") || id.startsWith("amberplantgrowthboost") -> Color(0xFF008080)
        id.startsWith("egggrowthboost") || id.startsWith("snowyegggrowthboost") -> Color(0xFFB45AF0)
        id.startsWith("petageboost") -> Color(0xFF9370DB)
        id.startsWith("pethatchsizeboost") -> Color(0xFF800080)
        id.startsWith("petxpboost") || id.startsWith("snowypetxpboost") -> Color(0xFF1E90FF)
        id.startsWith("hungerboost") || id.startsWith("snowyhungerboost") -> Color(0xFFFF1493)
        id.startsWith("hungerrestore") || id.startsWith("snowyhungerrestore") -> Color(0xFFFF69B4)
        id.startsWith("sellboost") -> Color(0xFFDC143C)
        id.startsWith("coinfinder") || id.startsWith("snowycoinfinder") -> Color(0xFFB49600)
        id.startsWith("seedfinder") -> Color(0xFFA86626)
        id.startsWith("producemutationboost") || id.startsWith("snowycropmutationboost") ||
            id.startsWith("dawnboost") || id.startsWith("ambermoonboost") -> Color(0xFF8C0F46)
        id.startsWith("petmutationboost") -> Color(0xFFA03264)
        id.startsWith("doubleharvest") -> Color(0xFF0078B4)
        id.startsWith("doublehatch") -> Color(0xFF3C5AB4)
        id.startsWith("produceeater") -> Color(0xFFFF4500)
        id.startsWith("producerefund") -> Color(0xFFFF6347)
        id.startsWith("petrefund") -> Color(0xFF005078)
        id.startsWith("copycat") -> Color(0xFFFF8C00)
        id.startsWith("goldgranter") -> Color(0xFFE1C837)
        id.startsWith("rainbowgranter") -> Color(0xFF50AAAA)
        id.startsWith("raindance") -> Color(0xFF4CCCCC)
        id.startsWith("snowgranter") -> Color(0xFF90B8CC)
        id.startsWith("frostgranter") -> Color(0xFF94A0CC)
        id.startsWith("dawnlitgranter") -> Color(0xFFC47CB4)
        id.startsWith("amberlitgranter") -> Color(0xFFCC9060)
        else -> Color(0xFF646464)
    }
}

// ── Plant (unpot) dialog ──

@Composable
private fun PlantUnpotDialog(
    plant: InventoryPlantItem,
    apiReady: Boolean,
    freePlantTiles: Int,
    isLocked: Boolean,
    onPlant: () -> Unit,
    onToggleLock: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entry = remember(plant.species, apiReady) { MgApi.findItem(plant.species) }
    val name = entry?.name?.removeSuffix(" Seed") ?: plant.species
    val color = rarityColor(entry?.rarity)
    val maxScale = entry?.maxScale ?: 1.0
    val canPlant = freePlantTiles > 0

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            // Header
            SpriteImage(url = entry?.cropSprite, size = 56.dp, contentDescription = name)

            Spacer(modifier = Modifier.height(10.dp))

            Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

            if (entry?.rarity != null) {
                Text(entry.rarity, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
            }

            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${plant.growSlots} slots",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Accent,
                )
                if (plant.totalPrice > 0) {
                    Text(
                        PriceCalculator.formatPrice(plant.totalPrice),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Crop slots list
            if (plant.slots.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    plant.slots.forEachIndexed { index, slot ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = SurfaceBorder.copy(alpha = 0.4f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                        }
                        PlantSlotRow(slot = slot, index = index, color = color, maxScale = maxScale, apiReady = apiReady)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Free tiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Free tiles", fontSize = 12.sp, color = TextSecondary)
                Text(
                    "$freePlantTiles",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (canPlant) StatusConnected else Color(0xFFEF4444),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onPlant,
                enabled = canPlant,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusConnected,
                    disabledContainerColor = StatusConnected.copy(alpha = 0.2f),
                    disabledContentColor = Color.White.copy(alpha = 0.4f),
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    if (canPlant) "Plant in Garden" else "No free tiles",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canPlant) Color.White else Color.White.copy(alpha = 0.4f),
                )
            }

        }
            LockToggleIcon(isLocked = isLocked, onClick = onToggleLock,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
    }
}

@Composable
private fun PlantSlotRow(
    slot: InventoryPlantSlot,
    index: Int,
    color: Color,
    maxScale: Double,
    apiReady: Boolean,
) {
    val sizePercent = sizePercent(slot.targetScale, maxScale)
    val fraction = (sizePercent / 100.0).toFloat().coerceIn(0f, 1f)
    val price = remember(slot.species, slot.targetScale, slot.mutations, apiReady) {
        PriceCalculator.calculateCropSellPrice(slot.species, slot.targetScale, slot.mutations)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // Slot label + mutations
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Slot ${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            if (slot.mutations.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    sortMutations(slot.mutations).forEach { mutation ->
                        SpriteImage(url = mutationSpriteUrl(mutation), size = 14.dp, contentDescription = mutation)
                    }
                }
            }
        }

        // Size bar + price
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("${sizePercent.toInt()}%", fontSize = 10.sp, color = TextSecondary, modifier = Modifier.width(28.dp))
            Box(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = 0.15f)),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(fraction).height(4.dp)
                            .background(color.copy(alpha = 0.8f)),
                    )
                }
            }
            if (price != null) {
                Text(
                    PriceCalculator.formatPrice(price),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700),
                )
            }
        }
    }
}

// ── Egg grow dialog ──

@Composable
private fun EggGrowDialog(
    egg: InventoryEggItem,
    apiReady: Boolean,
    freePlantTiles: Int,
    isLocked: Boolean,
    onGrowEgg: () -> Unit,
    onToggleLock: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entry = remember(egg.eggId, apiReady) { MgApi.findItem(egg.eggId) }
    val name = entry?.name ?: egg.eggId
    val color = rarityColor(entry?.rarity)
    val canGrow = freePlantTiles > 0

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            SpriteImage(url = entry?.sprite, size = 56.dp, contentDescription = name)

            Spacer(modifier = Modifier.height(10.dp))

            Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

            if (entry?.rarity != null) {
                Text(entry.rarity, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Quantity", fontSize = 12.sp, color = TextSecondary)
                    Text(fmtQty(egg.quantity), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Free tiles", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        "$freePlantTiles",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canGrow) StatusConnected else Color(0xFFEF4444),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onGrowEgg,
                enabled = canGrow,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8B5CF6),
                    disabledContainerColor = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                    disabledContentColor = Color.White.copy(alpha = 0.4f),
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    if (canGrow) "Grow Egg" else "No free tiles",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canGrow) Color.White else Color.White.copy(alpha = 0.4f),
                )
            }

        }
            LockToggleIcon(isLocked = isLocked, onClick = onToggleLock,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
    }
}

// ── Seed detail dialog ──

@Composable
private fun SeedDetailDialog(
    seed: InventorySeedItem,
    apiReady: Boolean,
    freePlantTiles: Int,
    isLocked: Boolean,
    onPlantSeed: () -> Unit,
    onToggleLock: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entry = remember(seed.species, apiReady) { MgApi.findItem(seed.species) }
    val name = entry?.name ?: seed.species
    val color = rarityColor(entry?.rarity)
    val canPlant = freePlantTiles > 0

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            // Sprite
            SpriteImage(url = entry?.sprite, size = 56.dp, contentDescription = name)

            Spacer(modifier = Modifier.height(10.dp))

            // Name
            Text(
                name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )

            // Rarity
            if (entry?.rarity != null) {
                Text(
                    entry.rarity,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Details
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Quantity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Quantity", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        fmtQty(seed.quantity),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                }

                // Free tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Free tiles", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        "$freePlantTiles",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canPlant) StatusConnected else Color(0xFFEF4444),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Plant seed button
            Button(
                onClick = onPlantSeed,
                enabled = canPlant,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusConnected,
                    disabledContainerColor = StatusConnected.copy(alpha = 0.2f),
                    disabledContentColor = Color.White.copy(alpha = 0.4f),
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    if (canPlant) "Plant Seed" else "No free tiles",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canPlant) Color.White else Color.White.copy(alpha = 0.4f),
                )
            }

        }
            LockToggleIcon(isLocked = isLocked, onClick = onToggleLock,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
    }
}

// ── Sell all pets dialog ──

@Composable
private fun SellAllPetsDialog(
    pets: List<InventoryPetItem>,
    apiReady: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val petsWithPrices = remember(pets, apiReady) {
        pets.map { pet ->
            val entry = MgApi.findPet(pet.petSpecies)
            val name = pet.name?.ifBlank { null } ?: entry?.name ?: pet.petSpecies
            val price = PriceCalculator.calculatePetSellPrice(pet.petSpecies, pet.xp, pet.targetScale, pet.mutations) ?: 0L
            Triple(pet, name, price)
        }
    }
    val totalValue = remember(petsWithPrices) { petsWithPrices.sumOf { it.third } }

    var confirmed by remember { mutableStateOf(false) }

    if (confirmed) {
        // Show result
        Dialog(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Pets Sold!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = StatusConnected)
                Spacer(modifier = Modifier.height(8.dp))
                Text("${pets.size} pets", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SpriteImage(url = COIN_BAG_URL, size = 20.dp, contentDescription = "coins")
                    Text(
                        PriceCalculator.formatPrice(totalValue),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusConnected),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Nice!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Sell All Unlocked Pets",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("${pets.size} pets", fontSize = 12.sp, color = TextSecondary)

            Spacer(modifier = Modifier.height(12.dp))

            // Pet grid (scrollable, same style as inventory but with price)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                GridOf(pets.size) { i ->
                    PetTileWithPrice(pets[i], apiReady, petsWithPrices[i].third)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SpriteImage(url = COIN_BAG_URL, size = 16.dp, contentDescription = "coins")
                    Text(
                        PriceCalculator.formatPrice(totalValue),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Button(
                    onClick = {
                        onConfirm()
                        confirmed = true
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Sell All", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// ── Sell all crops dialog ──

@Composable
private fun SellAllCropsDialog(
    produce: List<InventoryProduceItem>,
    apiReady: Boolean,
    playerCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val produceWithPrices = remember(produce, apiReady, playerCount) {
        produce.map { item ->
            val entry = MgApi.findItem(item.species)
            val name = entry?.name?.removeSuffix(" Seed") ?: item.species
            val price = PriceCalculator.calculateCropSellPrice(item.species, item.scale, item.mutations, playerCount) ?: 0L
            Triple(item, name, price)
        }
    }
    val totalValue = remember(produceWithPrices) { produceWithPrices.sumOf { it.third } }
    val friendsBonus = PriceCalculator.friendsMultiplier(playerCount)

    var confirmed by remember { mutableStateOf(false) }

    if (confirmed) {
        Dialog(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Crops Sold!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = StatusConnected)
                Spacer(modifier = Modifier.height(8.dp))
                Text("${produce.size} crops", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SpriteImage(url = COIN_BAG_URL, size = 20.dp, contentDescription = "coins")
                    Text(
                        PriceCalculator.formatPrice(totalValue),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusConnected),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Nice!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Sell All Crops", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${produce.size} crops", fontSize = 12.sp, color = TextSecondary)
                if (friendsBonus > 1.0) {
                    Text(
                        "x%.1f friends".format(friendsBonus),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusConnected,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Crop list (scrollable)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                produceWithPrices.forEach { (item, name, price) ->
                    val entry = remember(item.species, apiReady) { MgApi.findItem(item.species) }
                    val color = rarityColor(entry?.rarity)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceDark)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            SpriteImage(url = entry?.cropSprite, size = 22.dp, contentDescription = name)
                            Text(name, fontSize = 10.sp, color = TextPrimary, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            if (item.mutations.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    sortMutations(item.mutations).take(3).forEach { mutation ->
                                        SpriteImage(url = mutationSpriteUrl(mutation), size = 10.dp, contentDescription = mutation)
                                    }
                                }
                            }
                        }
                        if (price > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                SpriteImage(url = COIN_BAG_URL, size = 10.dp, contentDescription = "coins")
                                Text(
                                    PriceCalculator.formatPrice(price),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFD700),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SpriteImage(url = COIN_BAG_URL, size = 16.dp, contentDescription = "coins")
                    Text(
                        PriceCalculator.formatPrice(totalValue),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Button(
                    onClick = {
                        onConfirm()
                        confirmed = true
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Sell All", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// ── Lock badge overlay ──

private const val LOCK_SPRITE_URL = "https://mg-api.ariedam.fr/assets/sprites/ui/Locked.png"
private const val UNLOCK_SPRITE_URL = "https://mg-api.ariedam.fr/assets/sprites/ui/Unlocked.png"

@Composable
private fun LockOverlay(isLocked: Boolean, content: @Composable () -> Unit) {
    Box {
        content()
        if (isLocked) {
            SpriteImage(
                url = LOCK_SPRITE_URL,
                size = 12.dp,
                contentDescription = "Locked",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp),
            )
        }
    }
}

// ── Lock toggle icon (top-right of detail dialogs) ──

@Composable
private fun LockToggleIcon(isLocked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SpriteImage(
        url = if (isLocked) LOCK_SPRITE_URL else UNLOCK_SPRITE_URL,
        size = 28.dp,
        contentDescription = if (isLocked) "Locked" else "Unlocked",
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(2.dp),
    )
}

// ── Generic item detail dialog ──

@Composable
private fun ItemDetailDialog(
    itemId: String,
    apiReady: Boolean,
    quantity: Int? = null,
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    onDismiss: () -> Unit,
    extraContent: @Composable (() -> Unit)? = null,
) {
    val entry = remember(itemId, apiReady) { MgApi.findItem(itemId) ?: MgApi.findPet(itemId) }
    val name = entry?.name ?: itemId
    val sprite = entry?.sprite
    val color = rarityColor(entry?.rarity)

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            SpriteImage(url = sprite, size = 56.dp, contentDescription = name)

            Spacer(modifier = Modifier.height(10.dp))

            Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

            if (entry?.rarity != null) {
                Text(entry.rarity, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (quantity != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Quantity", fontSize = 12.sp, color = TextSecondary)
                        Text(fmtQty(quantity), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                }
            }

            if (extraContent != null) {
                Spacer(modifier = Modifier.height(6.dp))
                extraContent()
            }

        }
            LockToggleIcon(isLocked = isLocked, onClick = onToggleLock,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
    }
}

// ── Pet detail dialog ──

private const val COIN_BAG_URL = "https://mg-api.ariedam.fr/assets/sprites/ui/CoinBag.png"

@Composable
private fun PetDetailDialog(
    pet: InventoryPetItem,
    apiReady: Boolean,
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    onSell: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entry = remember(pet.petSpecies, apiReady) { MgApi.findPet(pet.petSpecies) }
    val name = pet.name?.ifBlank { null } ?: entry?.name ?: pet.petSpecies
    val color = rarityColor(entry?.rarity)
    val ms = maxStr(pet.petSpecies, pet.targetScale)
    val cs = curStr(pet.petSpecies, pet.xp, ms)
    val sellPrice = remember(pet.petSpecies, pet.xp, pet.targetScale, pet.mutations, apiReady) {
        PriceCalculator.calculatePetSellPrice(pet.petSpecies, pet.xp, pet.targetScale, pet.mutations)
    }

    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        // Confirmation dialog
        Dialog(onDismissRequest = { showConfirm = false }) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Sell $name?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                if (sellPrice != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SpriteImage(url = COIN_BAG_URL, size = 16.dp, contentDescription = "coins")
                        Text(
                            PriceCalculator.formatPrice(sellPrice),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700),
                        )
                    }
                }
                if (isLocked) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("This pet is locked and will be unlocked before selling.",
                        fontSize = 10.sp, color = TextSecondary, textAlign = TextAlign.Center)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { showConfirm = false },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Button(
                        onClick = {
                            showConfirm = false
                            onSell()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Sell", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SpriteImage(category = "pets", name = pet.petSpecies, size = 56.dp, contentDescription = pet.petSpecies)

                Spacer(modifier = Modifier.height(10.dp))

                Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                if (entry?.rarity != null) {
                    Text(entry.rarity, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (ms > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("STR", fontSize = 12.sp, color = TextSecondary)
                            val isMax = cs >= ms
                            Text(
                                if (isMax) "$cs" else "$cs/$ms",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = if (isMax) Color(0xFFFBBF24) else Accent,
                            )
                        }
                    }

                    // Sell value
                    if (sellPrice != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Value", fontSize = 12.sp, color = TextSecondary)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                SpriteImage(url = COIN_BAG_URL, size = 14.dp, contentDescription = "coins")
                                Text(
                                    PriceCalculator.formatPrice(sellPrice),
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFD700),
                                )
                            }
                        }
                    }

                    if (pet.mutations.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Mutations", fontSize = 12.sp, color = TextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                sortMutations(pet.mutations).forEach { mutation ->
                                    SpriteImage(url = mutationSpriteUrl(mutation), size = 16.dp, contentDescription = mutation)
                                }
                            }
                        }
                    }

                    if (pet.abilities.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Abilities", fontSize = 12.sp, color = TextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                pet.abilities.forEach { abilityId ->
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(abilityColor(abilityId)),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sell button
                Button(
                    onClick = { showConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Sell", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            LockToggleIcon(isLocked = isLocked, onClick = onToggleLock,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
    }
}

// ── Produce detail dialog ──

@Composable
private fun ProduceDetailDialog(
    item: InventoryProduceItem,
    apiReady: Boolean,
    isLocked: Boolean,
    playerCount: Int = 1,
    onToggleLock: () -> Unit,
    onSell: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entry = remember(item.species, apiReady) { MgApi.findItem(item.species) }
    val name = entry?.name?.removeSuffix(" Seed") ?: item.species
    val color = rarityColor(entry?.rarity)
    val maxS = entry?.maxScale ?: 1.0
    val pct = sizePercent(item.scale, maxS)
    val fraction = (pct / 100.0).toFloat().coerceIn(0f, 1f)
    val price = remember(item.species, item.scale, item.mutations, apiReady, playerCount) {
        PriceCalculator.calculateCropSellPrice(item.species, item.scale, item.mutations, playerCount)
    }

    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        Dialog(onDismissRequest = { showConfirm = false }) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Sell $name?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                if (price != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SpriteImage(url = COIN_BAG_URL, size = 16.dp, contentDescription = "coins")
                        Text(PriceCalculator.formatPrice(price), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { showConfirm = false },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary) }
                    Button(
                        onClick = { showConfirm = false; onSell() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("Sell", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                }
            }
        }
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SpriteImage(url = entry?.cropSprite, size = 56.dp, contentDescription = name)

                Spacer(modifier = Modifier.height(10.dp))

                Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                if (entry?.rarity != null) {
                    Text(entry.rarity, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Size
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Size", fontSize = 12.sp, color = TextSecondary)
                        Text("${pct.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(color.copy(0.15f))) {
                        Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(color.copy(0.8f)))
                    }

                    // Price
                    if (price != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Sell price", fontSize = 12.sp, color = TextSecondary)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                SpriteImage(url = COIN_BAG_URL, size = 14.dp, contentDescription = "coins")
                                Text(PriceCalculator.formatPrice(price), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                            }
                        }
                    }

                    // Mutations
                    if (item.mutations.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Mutations", fontSize = 12.sp, color = TextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                sortMutations(item.mutations).forEach { mutation ->
                                    SpriteImage(url = mutationSpriteUrl(mutation), size = 16.dp, contentDescription = mutation)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sell button
                Button(
                    onClick = { showConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Sell", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            LockToggleIcon(isLocked = isLocked, onClick = onToggleLock,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
    }
}

// ── Adaptive grid ──

@Composable
private fun GridOf(count: Int, content: @Composable (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = ((maxWidth + TILE_SPACING) / (TILE_MIN_WIDTH + TILE_SPACING)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(TILE_SPACING)) {
            (0 until count).chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TILE_SPACING)) {
                    row.forEach { i -> Box(Modifier.weight(1f)) { content(i) } }
                    repeat(cols - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}
