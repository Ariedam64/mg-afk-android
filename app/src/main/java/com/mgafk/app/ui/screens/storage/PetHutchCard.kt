package com.mgafk.app.ui.screens.storage

import com.mgafk.app.ui.theme.OnAccent
import com.mgafk.app.ui.theme.OnStatusError
import com.mgafk.app.ui.theme.StatusConnecting
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import com.mgafk.app.data.model.InventoryPetItem
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.data.repository.StorageCapacity
import com.mgafk.app.ui.theme.rarityColor
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.abilityBrush
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.RarityFilterRow
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary
import com.mgafk.app.ui.components.mutationSpriteUrl
import com.mgafk.app.ui.components.sortMutations
import com.mgafk.app.data.repository.PetTileStrength
import com.mgafk.app.data.repository.RaritySort
import com.mgafk.app.ui.components.GridOf
import com.mgafk.app.ui.components.LockOverlay
import com.mgafk.app.ui.components.LockToggleIcon
import com.mgafk.app.ui.components.PetTile

// ── Pet Hutch ──

@Composable
fun PetHutchCard(
    pets: List<InventoryPetItem>,
    apiReady: Boolean,
    favoritedItemIds: Set<String> = emptySet(),
    magicDust: Double = 0.0,
    capacitySlots: Int = PriceCalculator.HUTCH_BASE_CAPACITY,
    inventoryItemCount: Int = 0,
    onToggleLock: (String) -> Unit = {},
    onSellPet: (String) -> Unit = {},
    onUpgrade: () -> Unit = {},
    onMoveToInventory: (String) -> Unit = {},
) {
    val sorted = remember(pets, apiReady) { pets.sortedBy { RaritySort.pet(it.petSpecies) } }
    var selectedPetId by remember { mutableStateOf<String?>(null) }
    var selectedRarity by remember { mutableStateOf<String?>(null) }
    val availableRarities = remember(sorted, apiReady) {
        MgApi.RARITY_ORDER.filter { r -> sorted.any { MgApi.findPet(it.petSpecies)?.rarity.equals(r, ignoreCase = true) } }
    }
    val visible = remember(sorted, selectedRarity) {
        if (selectedRarity == null) sorted
        else sorted.filter { MgApi.findPet(it.petSpecies)?.rarity.equals(selectedRarity, ignoreCase = true) }
    }
    val maxItems = capacitySlots
    val capacityLevel = remember(capacitySlots, apiReady) { PriceCalculator.hutchLevelForCapacity(capacitySlots) }
    val nextUpgrade = remember(capacityLevel, apiReady) { PriceCalculator.getNextHutchUpgrade(capacityLevel) }

    AppCard(title = "Pet Hutch", collapsible = true, persistKey = "storage.petHutch", trailing = {
        Text("${pets.size}/$maxItems", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Accent.copy(0.7f))
    }) {
        StorageUpgradePanel(
            entityLabel = "Pet Hutch",
            magicDust = magicDust,
            capacityLevel = capacityLevel,
            maxLevel = remember(apiReady) { PriceCalculator.storageMaxLevel("PetHutch") },
            currentCapacity = maxItems,
            nextUpgrade = nextUpgrade,
            onUpgrade = onUpgrade,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (sorted.isEmpty()) {
            Text("Empty", fontSize = 12.sp, color = TextMuted)
        } else {
            RarityFilterRow(rarities = availableRarities, selected = selectedRarity, onSelect = { selectedRarity = it })
            Spacer(modifier = Modifier.height(8.dp))
            GridOf(visible.size) { i ->
                val pet = visible[i]
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
                canMoveToInventory = StorageCapacity.hasFreeSlot(inventoryItemCount, StorageCapacity.INVENTORY_LIMIT),
                onToggleLock = { onToggleLock(petLockId) },
                onSell = {
                    onSellPet(livePet.id)
                    selectedPetId = null
                },
                onMoveToInventory = {
                    onMoveToInventory(livePet.id)
                    selectedPetId = null
                },
                onDismiss = { selectedPetId = null },
            )
        } else {
            selectedPetId = null
        }
    }
}

// ── Storage pet detail dialog ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoragePetDetailDialog(
    pet: InventoryPetItem,
    apiReady: Boolean,
    isLocked: Boolean,
    canMoveToInventory: Boolean,
    onToggleLock: () -> Unit,
    onSell: () -> Unit,
    onMoveToInventory: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entry = remember(pet.petSpecies, apiReady) { MgApi.findPet(pet.petSpecies) }
    val name = pet.name?.ifBlank { null } ?: entry?.name ?: pet.petSpecies
    val color = rarityColor(entry?.rarity)
    val ms = PetTileStrength.maxStr(pet.petSpecies, pet.targetScale)
    val cs = PetTileStrength.curStr(pet.petSpecies, pet.xp, ms)
    val sellPrice = remember(pet.petSpecies, pet.xp, pet.targetScale, pet.mutations, apiReady) {
        PriceCalculator.calculatePetSellPrice(pet.petSpecies, pet.xp, pet.targetScale, pet.mutations)
    }
    val dustValue = remember(pet.petSpecies, pet.sourceEggId, pet.xp, pet.targetScale, pet.mutations, apiReady) {
        PriceCalculator.calculatePetDustValue(pet.petSpecies, pet.sourceEggId, pet.xp, pet.targetScale, pet.mutations)
    }

    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        Dialog(onDismissRequest = { showConfirm = false }) {
            Column(
                modifier = Modifier
                    .clip(AppTheme.shapes.mediumShape)
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
                        SpriteImage(url = MgApi.coinBagUrl, size = 16.dp, contentDescription = "coins")
                        Text(PriceCalculator.formatFull(sellPrice), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
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
                        shape = AppTheme.shapes.smallShape,
                    ) { Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary) }
                    Button(
                        onClick = { showConfirm = false; onSell() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                        shape = AppTheme.shapes.smallShape,
                    ) { Text("Sell", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnStatusError) }
                }
            }
        }
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(AppTheme.shapes.mediumShape)
                .background(SurfaceCard),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SpriteImage(category = "pets", name = pet.petSpecies, size = 56.dp, contentDescription = pet.petSpecies, mutations = pet.mutations)
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
                                color = if (isMax) StatusConnecting else Accent)
                        }
                    }
                    if (sellPrice != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Value", fontSize = 12.sp, color = TextSecondary)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                SpriteImage(url = MgApi.coinBagUrl, size = 14.dp, contentDescription = "coins")
                                Text(PriceCalculator.formatFull(sellPrice), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                            }
                        }
                    }
                    if (dustValue != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Dust", fontSize = 12.sp, color = TextSecondary)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                SpriteImage(url = MgApi.magicDustUrl, size = 14.dp, contentDescription = "dust")
                                Text(PriceCalculator.formatFull(dustValue), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC084FC))
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
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("Abilities", fontSize = 12.sp, color = TextSecondary)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                pet.abilities.forEach { abilityId ->
                                    val entry = remember(abilityId, apiReady) { MgApi.getAbilities()[abilityId] }
                                    val displayName = entry?.name ?: abilityId
                                    val bg = remember(abilityId, apiReady) { abilityBrush(abilityId) }
                                    Text(
                                        displayName,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(bg, alpha = 0.85f)
                                            .padding(horizontal = 6.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onMoveToInventory,
                    enabled = canMoveToInventory,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        disabledContainerColor = SurfaceDark,
                    ),
                    shape = AppTheme.shapes.smallShape,
                ) {
                    Text(
                        if (canMoveToInventory) "Move to Inventory" else "Inventory full",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (canMoveToInventory) OnAccent else TextMuted,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                    shape = AppTheme.shapes.smallShape,
                ) { Text("Sell", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnStatusError) }
            }
            LockToggleIcon(isLocked = isLocked, onClick = onToggleLock,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
    }
}
