package com.mgafk.app.ui.screens.storage

import com.mgafk.app.ui.theme.OnAccent
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import com.mgafk.app.data.model.InventoryDecorItem
import com.mgafk.app.data.model.InventorySeedItem
import com.mgafk.app.data.model.InventoryToolItem
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.data.repository.StorageCapacity
import com.mgafk.app.ui.theme.rarityColor
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.RarityFilterRow
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary
import com.mgafk.app.ui.theme.rarityBorder
import com.mgafk.app.data.repository.RaritySort
import com.mgafk.app.ui.components.GridOf
import com.mgafk.app.ui.components.LockOverlay
import com.mgafk.app.ui.components.LockToggleIcon
import com.mgafk.app.ui.components.formatQuantity

// ── Seed Silo ──

@Composable
fun SeedSiloCard(
    seeds: List<InventorySeedItem>,
    apiReady: Boolean,
    favoritedItemIds: Set<String> = emptySet(),
    inventorySeedSpecies: Set<String> = emptySet(),
    inventoryItemCount: Int = 0,
    magicDust: Double = 0.0,
    capacitySlots: Int = PriceCalculator.SILO_BASE_CAPACITY,
    onToggleLock: (String) -> Unit = {},
    onMoveToInventory: (String) -> Unit = {},
    onUpgrade: () -> Unit = {},
) {
    val sorted = remember(seeds, apiReady) { seeds.sortedBy { RaritySort.item(it.species) } }
    var selectedSpecies by remember { mutableStateOf<String?>(null) }
    var selectedRarity by remember { mutableStateOf<String?>(null) }
    val availableRarities = remember(sorted, apiReady) {
        MgApi.RARITY_ORDER.filter { r -> sorted.any { MgApi.findItem(it.species)?.rarity.equals(r, ignoreCase = true) } }
    }
    val visible = remember(sorted, selectedRarity) {
        if (selectedRarity == null) sorted
        else sorted.filter { MgApi.findItem(it.species)?.rarity.equals(selectedRarity, ignoreCase = true) }
    }
    val maxItems = capacitySlots
    val capacityLevel = remember(capacitySlots, apiReady) { PriceCalculator.siloLevelForCapacity(capacitySlots) }
    val nextUpgrade = remember(capacityLevel, apiReady) { PriceCalculator.getNextSiloUpgrade(capacityLevel) }

    AppCard(title = "Seed Silo", collapsible = true, persistKey = "storage.seedSilo", trailing = {
        Text("${seeds.size}/$maxItems", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Accent.copy(0.7f))
    }) {
        StorageUpgradePanel(
            entityLabel = "Seed Silo",
            magicDust = magicDust,
            capacityLevel = capacityLevel,
            maxLevel = remember(apiReady) { PriceCalculator.storageMaxLevel("SeedSilo") },
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
                Box(modifier = Modifier.clickable { selectedSpecies = visible[i].species }) {
                    LockOverlay(isLocked = visible[i].species in favoritedItemIds) {
                        QtyTile(visible[i].species, visible[i].quantity, apiReady)
                    }
                }
            }
        }
    }

    selectedSpecies?.let { species ->
        val liveSeed = seeds.find { it.species == species }
        if (liveSeed != null) {
            val canMoveBack = StorageCapacity.canAddStackable(
                currentCount = inventoryItemCount,
                max = StorageCapacity.INVENTORY_LIMIT,
                stackExists = species in inventorySeedSpecies,
            )
            StorageItemDetailDialog(
                itemId = species,
                apiReady = apiReady,
                quantity = liveSeed.quantity,
                isLocked = species in favoritedItemIds,
                canMoveToInventory = canMoveBack,
                onToggleLock = { onToggleLock(species) },
                onMoveToInventory = {
                    onMoveToInventory(species)
                    selectedSpecies = null
                },
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
    inventoryDecorIds: Set<String> = emptySet(),
    inventoryItemCount: Int = 0,
    magicDust: Double = 0.0,
    capacitySlots: Int = PriceCalculator.DECOR_SHED_BASE_CAPACITY,
    onToggleLock: (String) -> Unit = {},
    onMoveToInventory: (String) -> Unit = {},
    onUpgrade: () -> Unit = {},
) {
    val sorted = remember(decors, apiReady) { decors.sortedBy { RaritySort.item(it.decorId) } }
    var selectedDecorId by remember { mutableStateOf<String?>(null) }
    var selectedRarity by remember { mutableStateOf<String?>(null) }
    val availableRarities = remember(sorted, apiReady) {
        MgApi.RARITY_ORDER.filter { r -> sorted.any { MgApi.findItem(it.decorId)?.rarity.equals(r, ignoreCase = true) } }
    }
    val visible = remember(sorted, selectedRarity) {
        if (selectedRarity == null) sorted
        else sorted.filter { MgApi.findItem(it.decorId)?.rarity.equals(selectedRarity, ignoreCase = true) }
    }
    val maxItems = capacitySlots
    val capacityLevel = remember(capacitySlots, apiReady) { PriceCalculator.decorShedLevelForCapacity(capacitySlots) }
    val nextUpgrade = remember(capacityLevel, apiReady) { PriceCalculator.getNextDecorShedUpgrade(capacityLevel) }

    AppCard(title = "Decor Shed", collapsible = true, persistKey = "storage.decorShed", trailing = {
        Text("${decors.size}/$maxItems", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Accent.copy(0.7f))
    }) {
        StorageUpgradePanel(
            entityLabel = "Decor Shed",
            magicDust = magicDust,
            capacityLevel = capacityLevel,
            maxLevel = remember(apiReady) { PriceCalculator.storageMaxLevel("DecorShed") },
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
                Box(modifier = Modifier.clickable { selectedDecorId = visible[i].decorId }) {
                    LockOverlay(isLocked = visible[i].decorId in favoritedItemIds) {
                        QtyTile(visible[i].decorId, visible[i].quantity, apiReady)
                    }
                }
            }
        }
    }

    selectedDecorId?.let { decorId ->
        val liveDecor = decors.find { it.decorId == decorId }
        if (liveDecor != null) {
            val canMoveBack = StorageCapacity.canAddStackable(
                currentCount = inventoryItemCount,
                max = StorageCapacity.INVENTORY_LIMIT,
                stackExists = decorId in inventoryDecorIds,
            )
            StorageItemDetailDialog(
                itemId = decorId,
                apiReady = apiReady,
                quantity = liveDecor.quantity,
                isLocked = decorId in favoritedItemIds,
                canMoveToInventory = canMoveBack,
                onToggleLock = { onToggleLock(decorId) },
                onMoveToInventory = {
                    onMoveToInventory(decorId)
                    selectedDecorId = null
                },
                onDismiss = { selectedDecorId = null },
            )
        } else {
            selectedDecorId = null
        }
    }
}

// ── Tool Shack ──

@Composable
fun ToolShackCard(
    tools: List<InventoryToolItem>,
    apiReady: Boolean,
    favoritedItemIds: Set<String> = emptySet(),
    inventoryToolIds: Set<String> = emptySet(),
    inventoryItemCount: Int = 0,
    magicDust: Double = 0.0,
    capacitySlots: Int = PriceCalculator.TOOL_SHACK_BASE_CAPACITY,
    onToggleLock: (String) -> Unit = {},
    onMoveToInventory: (String) -> Unit = {},
    onUpgrade: () -> Unit = {},
) {
    val sorted = remember(tools, apiReady) { tools.sortedBy { RaritySort.item(it.toolId) } }
    var selectedToolId by remember { mutableStateOf<String?>(null) }
    var selectedRarity by remember { mutableStateOf<String?>(null) }
    val availableRarities = remember(sorted, apiReady) {
        MgApi.RARITY_ORDER.filter { r -> sorted.any { MgApi.findItem(it.toolId)?.rarity.equals(r, ignoreCase = true) } }
    }
    val visible = remember(sorted, selectedRarity) {
        if (selectedRarity == null) sorted
        else sorted.filter { MgApi.findItem(it.toolId)?.rarity.equals(selectedRarity, ignoreCase = true) }
    }
    val maxItems = capacitySlots
    val capacityLevel = remember(capacitySlots, apiReady) { PriceCalculator.toolShackLevelForCapacity(capacitySlots) }
    val nextUpgrade = remember(capacityLevel, apiReady) { PriceCalculator.getNextToolShackUpgrade(capacityLevel) }

    AppCard(title = "Tool Shack", collapsible = true, persistKey = "storage.toolShack", trailing = {
        Text("${tools.size}/$maxItems", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Accent.copy(0.7f))
    }) {
        StorageUpgradePanel(
            entityLabel = "Tool Shack",
            magicDust = magicDust,
            capacityLevel = capacityLevel,
            maxLevel = remember(apiReady) { PriceCalculator.storageMaxLevel("ToolShack") },
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
                Box(modifier = Modifier.clickable { selectedToolId = visible[i].storageKey }) {
                    LockOverlay(isLocked = visible[i].toolId in favoritedItemIds) {
                        QtyTile(visible[i].toolId, visible[i].quantity, apiReady)
                    }
                }
            }
        }
    }

    selectedToolId?.let { toolKey ->
        val liveTool = tools.find { it.storageKey == toolKey }
        if (liveTool != null) {
            val toolId = liveTool.toolId
            val canMoveBack = StorageCapacity.canAddStackable(
                currentCount = inventoryItemCount,
                max = StorageCapacity.INVENTORY_LIMIT,
                // A tool the game tracks individually needs an inventory slot of its own.
                stackExists = liveTool.isStackable && toolId in inventoryToolIds,
            )
            StorageItemDetailDialog(
                itemId = toolId,
                apiReady = apiReady,
                quantity = liveTool.quantity,
                isLocked = toolId in favoritedItemIds,
                canMoveToInventory = canMoveBack,
                onToggleLock = { onToggleLock(toolId) },
                onMoveToInventory = {
                    onMoveToInventory(liveTool.storageKey)
                    selectedToolId = null
                },
                onDismiss = { selectedToolId = null },
            )
        } else {
            selectedToolId = null
        }
    }
}

// ── Storage upgrade panel (PetHutch / SeedSilo / DecorShed / ToolShack - same pattern) ──

@Composable
internal fun StorageUpgradePanel(
    entityLabel: String,
    magicDust: Double,
    capacityLevel: Int,
    maxLevel: Int,
    currentCapacity: Int,
    nextUpgrade: PriceCalculator.StorageUpgradeInfo?,
    onUpgrade: () -> Unit,
) {
    var showConfirm by remember { mutableStateOf(false) }
    val dustLong = magicDust.toLong()
    val canAfford = nextUpgrade != null && dustLong >= nextUpgrade.dustCost

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.smallShape)
            .background(SurfaceDark)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Level $capacityLevel / $maxLevel",
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                SpriteImage(url = MgApi.magicDustUrl, size = 14.dp, contentDescription = "dust")
                Text(PriceCalculator.formatPrice(dustLong),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC084FC))
            }
        }

        if (nextUpgrade == null) {
            Text("$entityLabel maxed out, capacity $currentCapacity", fontSize = 11.sp, color = TextMuted,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Next: $currentCapacity → ${nextUpgrade.capacityAfter}",
                    fontSize = 11.sp, color = TextSecondary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    SpriteImage(url = MgApi.magicDustUrl, size = 12.dp, contentDescription = "cost")
                    Text(PriceCalculator.formatPrice(nextUpgrade.dustCost),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = if (canAfford) Color(0xFFC084FC) else StatusError)
                }
            }
            Button(
                onClick = { if (canAfford) showConfirm = true },
                enabled = canAfford,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7C3AED),
                    disabledContainerColor = SurfaceCard,
                ),
                shape = AppTheme.shapes.smallShape,
            ) {
                Text(
                    if (canAfford) "Upgrade to level ${nextUpgrade.targetLevel}"
                    else "Need ${PriceCalculator.formatPrice(nextUpgrade.dustCost - dustLong)} more dust",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (canAfford) Color.White else TextMuted,
                )
            }
        }
    }

    if (showConfirm && nextUpgrade != null) {
        Dialog(onDismissRequest = { showConfirm = false }) {
            Column(
                modifier = Modifier
                    .clip(AppTheme.shapes.mediumShape)
                    .background(SurfaceCard)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Upgrade $entityLabel?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Level $capacityLevel → ${nextUpgrade.targetLevel}",
                    fontSize = 12.sp, color = TextSecondary)
                Text("Capacity $currentCapacity → ${nextUpgrade.capacityAfter}",
                    fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Cost", fontSize = 12.sp, color = TextSecondary)
                    SpriteImage(url = MgApi.magicDustUrl, size = 14.dp, contentDescription = "dust")
                    Text(PriceCalculator.formatFull(nextUpgrade.dustCost), fontSize = 14.sp,
                        fontWeight = FontWeight.Bold, color = Color(0xFFC084FC))
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
                        onClick = { showConfirm = false; onUpgrade() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                        shape = AppTheme.shapes.smallShape,
                    ) { Text("Upgrade", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                }
            }
        }
    }
}

// ── Storage item detail dialog ──

@Composable
private fun StorageItemDetailDialog(
    itemId: String,
    apiReady: Boolean,
    quantity: Int,
    isLocked: Boolean,
    canMoveToInventory: Boolean,
    onToggleLock: () -> Unit,
    onMoveToInventory: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entry = remember(itemId, apiReady) { MgApi.findItem(itemId) }
    val name = entry?.name ?: itemId
    val color = rarityColor(entry?.rarity)

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
                    Text(PriceCalculator.formatFull(quantity.toLong()), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
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
            .clip(AppTheme.shapes.smallShape)
            .rarityBorder(rarity = entry?.rarity, width = 1.5.dp, shape = AppTheme.shapes.smallShape, alpha = 0.5f)
            .background(SurfaceDark).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SpriteImage(url = entry?.sprite, size = 28.dp, contentDescription = name)
        Text(name, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 10.sp)
        Text(formatQuantity(quantity), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Accent, lineHeight = 11.sp)
    }
}
