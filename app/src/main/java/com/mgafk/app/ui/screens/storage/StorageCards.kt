package com.mgafk.app.ui.screens.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import com.mgafk.app.data.model.InventoryCropsItem
import com.mgafk.app.data.model.InventoryDecorItem
import com.mgafk.app.data.model.InventoryEggItem
import com.mgafk.app.data.model.InventoryPetItem
import com.mgafk.app.data.model.InventoryPlantItem
import com.mgafk.app.data.model.InventoryProduceItem
import com.mgafk.app.data.model.InventorySeedItem
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary
import com.mgafk.app.ui.components.mutationSpriteUrl
import com.mgafk.app.ui.components.sortMutations

private val RarityCommon = Color(0xFFE7E7E7)
private val RarityUncommon = Color(0xFF67BD4D)
private val RarityRare = Color(0xFF0071C6)
private val RarityLegendary = Color(0xFFFFC734)
private val RarityMythical = Color(0xFF9944A7)
private val RarityDivine = Color(0xFFFF7835)
private val RarityCelestial = Color(0xFFFF00FF)

private fun rarityColor(rarity: String?): Color = when (rarity?.lowercase()) {
    "common" -> RarityCommon; "uncommon" -> RarityUncommon; "rare" -> RarityRare
    "legendary" -> RarityLegendary; "mythical", "mythic" -> RarityMythical
    "divine" -> RarityDivine; "celestial" -> RarityCelestial; else -> TextMuted
}

private val TILE_MIN = 76.dp
private val GAP = 6.dp

private const val XP_H = 3600.0; private const val BASE_S = 80; private const val MAX_S = 100; private const val S_GAIN = 30
private fun maxStr(sp: String, sc: Double): Int {
    val ms = MgApi.findPet(sp)?.maxScale ?: return BASE_S
    if (sc <= 1.0) return BASE_S; if (sc >= ms) return MAX_S
    return (BASE_S + 20 * (sc - 1.0) / (ms - 1.0)).toInt()
}
private fun curStr(sp: String, xp: Double, max: Int): Int {
    val htm = MgApi.findPet(sp)?.hoursToMature ?: return max - S_GAIN
    return ((max - S_GAIN) + minOf(S_GAIN / htm * (xp / XP_H), S_GAIN.toDouble())).toInt()
}

private val RARITY_ORDER = listOf("Celestial", "Divine", "Mythic", "Mythical", "Legendary", "Rare", "Uncommon", "Common")
private fun raritySort(id: String): Int {
    val r = MgApi.findItem(id)?.rarity ?: return RARITY_ORDER.size
    return RARITY_ORDER.indexOfFirst { it.equals(r, ignoreCase = true) }.let { if (it < 0) RARITY_ORDER.size else it }
}
private fun raritySortPet(sp: String): Int {
    val r = MgApi.findPet(sp)?.rarity ?: return RARITY_ORDER.size
    return RARITY_ORDER.indexOfFirst { it.equals(r, ignoreCase = true) }.let { if (it < 0) RARITY_ORDER.size else it }
}

private fun fmtQty(q: Int): String = when {
    q >= 1_000_000 -> "%.1fM".format(q / 1_000_000.0)
    q >= 10_000 -> "${q / 1000}K"
    q >= 1_000 -> "%.1fK".format(q / 1000.0)
    else -> "$q"
}

// ── Seed Silo ──

@Composable
fun SeedSiloCard(
    seeds: List<InventorySeedItem>,
    apiReady: Boolean,
    favoritedItemIds: Set<String> = emptySet(),
    onToggleLock: (String) -> Unit = {},
) {
    val sorted = remember(seeds, apiReady) { seeds.sortedBy { raritySort(it.species) } }
    var selectedSpecies by remember { mutableStateOf<String?>(null) }

    AppCard(title = "Seed Silo", collapsible = true, persistKey = "storage.seedSilo", trailing = {
        Text("${seeds.size}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Accent.copy(0.7f))
    }) {
        if (sorted.isEmpty()) {
            Text("Empty", fontSize = 12.sp, color = TextMuted)
        } else {
            GridOf(sorted.size) { i ->
                Box(modifier = Modifier.clickable { selectedSpecies = sorted[i].species }) {
                    LockOverlay(isLocked = sorted[i].species in favoritedItemIds) {
                        QtyTile(sorted[i].species, sorted[i].quantity, apiReady)
                    }
                }
            }
        }
    }

    selectedSpecies?.let { species ->
        val liveSeed = seeds.find { it.species == species }
        if (liveSeed != null) {
            StorageItemDetailDialog(
                itemId = species,
                apiReady = apiReady,
                quantity = liveSeed.quantity,
                isLocked = species in favoritedItemIds,
                onToggleLock = { onToggleLock(species) },
                onDismiss = { selectedSpecies = null },
            )
        } else {
            selectedSpecies = null
        }
    }
}

// ── Decor Shed ──

@Composable
fun DecorShedCard(
    decors: List<InventoryDecorItem>,
    apiReady: Boolean,
    favoritedItemIds: Set<String> = emptySet(),
    onToggleLock: (String) -> Unit = {},
) {
    val sorted = remember(decors, apiReady) { decors.sortedBy { raritySort(it.decorId) } }
    var selectedDecorId by remember { mutableStateOf<String?>(null) }

    AppCard(title = "Decor Shed", collapsible = true, persistKey = "storage.decorShed", trailing = {
        Text("${decors.size}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Accent.copy(0.7f))
    }) {
        if (sorted.isEmpty()) {
            Text("Empty", fontSize = 12.sp, color = TextMuted)
        } else {
            GridOf(sorted.size) { i ->
                Box(modifier = Modifier.clickable { selectedDecorId = sorted[i].decorId }) {
                    LockOverlay(isLocked = sorted[i].decorId in favoritedItemIds) {
                        QtyTile(sorted[i].decorId, sorted[i].quantity, apiReady)
                    }
                }
            }
        }
    }

    selectedDecorId?.let { decorId ->
        val liveDecor = decors.find { it.decorId == decorId }
        if (liveDecor != null) {
            StorageItemDetailDialog(
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
}

// ── Feeding Trough ──

private const val FEEDING_TROUGH_MAX = 9

@Composable
fun FeedingTroughCard(
    crops: List<InventoryCropsItem>,
    produce: List<InventoryProduceItem>,
    apiReady: Boolean,
    showTip: Boolean = false,
    onDismissTip: () -> Unit = {},
    onAddItems: (List<InventoryProduceItem>) -> Unit = {},
    onRemoveItem: (String) -> Unit = {},
) {
    val sorted = remember(crops, apiReady) { crops.sortedBy { raritySort(it.species) } }
    val troughIds = remember(crops) { crops.map { it.id }.toSet() }
    val availableProduce = remember(produce, troughIds) { produce.filter { it.id !in troughIds } }
    val slotsLeft = (FEEDING_TROUGH_MAX - crops.size).coerceAtLeast(0)
    var showPicker by remember { mutableStateOf(false) }

    AppCard(title = "Feeding Trough", collapsible = true, persistKey = "storage.feedingTrough", trailing = {
        Text("${crops.size}/$FEEDING_TROUGH_MAX", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Accent.copy(0.7f))
    }) {
        // First-time tip
        AnimatedVisibility(visible = showTip, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Accent.copy(alpha = 0.1f))
                    .border(1.dp, Accent.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                    .clickable { onDismissTip() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tap a crop to remove it from the feeding trough.",
                        fontSize = 11.sp, color = Accent, lineHeight = 15.sp, modifier = Modifier.weight(1f))
                    Text("OK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Accent,
                        modifier = Modifier.clickable { onDismissTip() })
                }
            }
        }

        val totalTiles = sorted.size + if (slotsLeft > 0 && availableProduce.isNotEmpty()) 1 else 0
        if (totalTiles == 0) {
            Text("Empty", fontSize = 12.sp, color = TextMuted)
        } else {
            GridOf(totalTiles) { i ->
                if (i < sorted.size) {
                    CropTile(sorted[i], apiReady, onClick = { onRemoveItem(sorted[i].id) })
                } else {
                    AddTile { showPicker = true }
                }
            }
        }
    }

    if (showPicker) {
        FeedingTroughPickerDialog(
            produce = availableProduce,
            maxSelectable = slotsLeft,
            apiReady = apiReady,
            onConfirm = { selected ->
                showPicker = false
                if (selected.isNotEmpty()) onAddItems(selected)
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, Accent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Accent)
        Text("Add", fontSize = 8.sp, color = TextMuted, lineHeight = 10.sp)
    }
}

@Composable
private fun FeedingTroughPickerDialog(
    produce: List<InventoryProduceItem>,
    maxSelectable: Int,
    apiReady: Boolean,
    onConfirm: (List<InventoryProduceItem>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sorted = remember(produce, apiReady) { produce.sortedBy { raritySort(it.species) } }
    var selected by remember { mutableStateOf(setOf<String>()) } // item IDs

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .padding(16.dp),
        ) {
            Text(
                "Add to Feeding Trough",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Text(
                "${selected.size}/$maxSelectable selected",
                fontSize = 11.sp,
                color = if (selected.size == maxSelectable) StatusConnected else TextMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )

            if (sorted.isEmpty()) {
                Text("No produce in inventory.", fontSize = 12.sp, color = TextMuted)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(TILE_MIN),
                    horizontalArrangement = Arrangement.spacedBy(GAP),
                    verticalArrangement = Arrangement.spacedBy(GAP),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 100.dp)
                        .height(320.dp),
                ) {
                    items(sorted, key = { it.id }) { item ->
                        val isSelected = item.id in selected
                        val canSelect = selected.size < maxSelectable
                        PickerProduceTile(
                            item = item,
                            apiReady = apiReady,
                            isSelected = isSelected,
                            onClick = {
                                selected = if (isSelected) {
                                    selected - item.id
                                } else if (canSelect) {
                                    selected + item.id
                                } else selected
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                ) {
                    Text("Cancel", fontSize = 12.sp, color = TextSecondary)
                }
                Button(
                    onClick = {
                        val items = sorted.filter { it.id in selected }
                        onConfirm(items)
                    },
                    enabled = selected.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) {
                    Text("Add ${selected.size}", fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PickerProduceTile(
    item: InventoryProduceItem,
    apiReady: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val entry = remember(item.species, apiReady) { MgApi.findItem(item.species) }
    val name = entry?.name?.removeSuffix(" Seed") ?: item.species
    val color = rarityColor(entry?.rarity)
    val borderColor = if (isSelected) StatusConnected else color.copy(alpha = 0.5f)
    val borderWidth = if (isSelected) 2.5.dp else 1.5.dp
    val price = remember(item.species, item.scale, item.mutations, apiReady) {
        PriceCalculator.calculateCropSellPrice(item.species, item.scale, item.mutations)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (item.mutations.isNotEmpty() || price != null) 0.8f else 1f)
            .clip(RoundedCornerShape(10.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .background(if (isSelected) StatusConnected.copy(0.1f) else SurfaceDark)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SpriteImage(url = entry?.cropSprite, size = 28.dp, contentDescription = name)
        Text(name, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 10.sp)
        if (price != null) {
            Text(PriceCalculator.formatPrice(price), fontSize = 8.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD700), lineHeight = 10.sp)
        }
        if (item.mutations.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                sortMutations(item.mutations).take(3).forEach { SpriteImage(url = mutationSpriteUrl(it), size = 12.dp, contentDescription = it) }
            }
        }
        if (isSelected) {
            Text("✓", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StatusConnected, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun CropTile(item: InventoryCropsItem, apiReady: Boolean, onClick: () -> Unit = {}) {
    val entry = remember(item.species, apiReady) { MgApi.findItem(item.species) }
    val name = entry?.name?.removeSuffix(" Seed") ?: item.species
    val color = rarityColor(entry?.rarity)

    Column(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SpriteImage(url = entry?.cropSprite, size = 28.dp, contentDescription = name)
        Text(name, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 10.sp)
        if (item.mutations.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                sortMutations(item.mutations).take(3).forEach { SpriteImage(url = mutationSpriteUrl(it), size = 12.dp, contentDescription = it) }
            }
        }
    }
}

// ── Pet Hutch ──

@Composable
fun PetHutchCard(
    pets: List<InventoryPetItem>,
    apiReady: Boolean,
    favoritedItemIds: Set<String> = emptySet(),
    onToggleLock: (String) -> Unit = {},
    onSellPet: (String) -> Unit = {},
) {
    val sorted = remember(pets, apiReady) { pets.sortedBy { raritySortPet(it.petSpecies) } }
    var selectedPetId by remember { mutableStateOf<String?>(null) }

    AppCard(title = "Pet Hutch", collapsible = true, persistKey = "storage.petHutch", trailing = {
        Text("${pets.size}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Accent.copy(0.7f))
    }) {
        if (sorted.isEmpty()) {
            Text("Empty", fontSize = 12.sp, color = TextMuted)
        } else {
            GridOf(sorted.size) { i ->
                val pet = sorted[i]
                Box(modifier = Modifier.clickable { selectedPetId = pet.id }) {
                    LockOverlay(isLocked = pet.id in favoritedItemIds || pet.petSpecies in favoritedItemIds) {
                        PetTile(pet, apiReady)
                    }
                }
            }
        }
    }

    selectedPetId?.let { petId ->
        val livePet = pets.find { it.id == petId }
        if (livePet != null) {
            val petLockId = if (livePet.id in favoritedItemIds) livePet.id
                else if (livePet.petSpecies in favoritedItemIds) livePet.petSpecies
                else livePet.id
            StoragePetDetailDialog(
                pet = livePet,
                apiReady = apiReady,
                isLocked = petLockId in favoritedItemIds,
                onToggleLock = { onToggleLock(petLockId) },
                onSell = {
                    onSellPet(livePet.id)
                    selectedPetId = null
                },
                onDismiss = { selectedPetId = null },
            )
        } else {
            selectedPetId = null
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

// ── Lock toggle icon ──

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

// ── Storage item detail dialog ──

@Composable
private fun StorageItemDetailDialog(
    itemId: String,
    apiReady: Boolean,
    quantity: Int,
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entry = remember(itemId, apiReady) { MgApi.findItem(itemId) }
    val name = entry?.name ?: itemId
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
                SpriteImage(url = entry?.sprite, size = 56.dp, contentDescription = name)
                Spacer(modifier = Modifier.height(10.dp))
                Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                if (entry?.rarity != null) {
                    Text(entry.rarity, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Quantity", fontSize = 12.sp, color = TextSecondary)
                    Text(fmtQty(quantity), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
            }
            LockToggleIcon(isLocked = isLocked, onClick = onToggleLock,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
    }
}

// ── Storage pet detail dialog ──

private const val COIN_BAG_URL = "https://mg-api.ariedam.fr/assets/sprites/ui/CoinBag.png"

@Composable
private fun StoragePetDetailDialog(
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
                        Text(PriceCalculator.formatPrice(sellPrice), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                    }
                }
                if (isLocked) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("This pet is locked and will be unlocked before selling.",
                        fontSize = 10.sp, color = TextSecondary, textAlign = TextAlign.Center)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            Text(if (isMax) "$cs" else "$cs/$ms", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = if (isMax) Color(0xFFFBBF24) else Accent)
                        }
                    }
                    if (sellPrice != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Value", fontSize = 12.sp, color = TextSecondary)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                SpriteImage(url = COIN_BAG_URL, size = 14.dp, contentDescription = "coins")
                                Text(PriceCalculator.formatPrice(sellPrice), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                            }
                        }
                    }
                    if (pet.mutations.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Mutations", fontSize = 12.sp, color = TextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                sortMutations(pet.mutations).forEach { mutation ->
                                    SpriteImage(url = mutationSpriteUrl(mutation), size = 16.dp, contentDescription = mutation)
                                }
                            }
                        }
                    }
                    if (pet.abilities.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Abilities", fontSize = 12.sp, color = TextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                pet.abilities.forEach { abilityId ->
                                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(abilityColor(abilityId)))
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Sell", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            }
            LockToggleIcon(isLocked = isLocked, onClick = onToggleLock,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
    }
}

// ── Shared: quantity tile ──

@Composable
private fun QtyTile(itemId: String, quantity: Int, apiReady: Boolean) {
    val entry = remember(itemId, apiReady) { MgApi.findItem(itemId) }
    val name = entry?.name ?: itemId
    val color = rarityColor(entry?.rarity)

    Column(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .background(SurfaceDark).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SpriteImage(url = entry?.sprite, size = 28.dp, contentDescription = name)
        Text(name, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 10.sp)
        Text(fmtQty(quantity), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Accent, lineHeight = 11.sp)
    }
}

// ── Shared: pet tile (compact card, same style as pet selector) ──

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
        // STR top-right
        if (ms > 0) {
            val strText = if (isMax) "$cs" else "$cs/$ms"
            val strColor = if (isMax) Color(0xFFFBBF24) else Accent
            Text(
                strText, fontSize = 7.sp, fontWeight = FontWeight.Bold,
                color = strColor, lineHeight = 9.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
            )
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

// ── Adaptive grid ──

@Composable
private fun GridOf(count: Int, content: @Composable (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = ((maxWidth + GAP) / (TILE_MIN + GAP)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
            (0 until count).chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    row.forEach { i -> Box(Modifier.weight(1f)) { content(i) } }
                    repeat(cols - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}
