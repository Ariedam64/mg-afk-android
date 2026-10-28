package com.mgafk.app.ui.screens.storage

import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.mgafk.app.data.model.InventoryProduceItem
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.ui.theme.rarityColor
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.rarityBorder
import com.mgafk.app.ui.components.mutationSpriteUrl
import com.mgafk.app.ui.components.sortMutations
import com.mgafk.app.data.repository.RaritySort
import com.mgafk.app.ui.components.GridOf
import com.mgafk.app.ui.components.TILE_MIN_WIDTH
import com.mgafk.app.ui.components.TILE_SPACING

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
    val sorted = remember(crops, apiReady) { crops.sortedBy { RaritySort.item(it.species) } }
    val troughIds = remember(crops) { crops.map { it.id }.toSet() }
    val availableProduce = remember(produce, troughIds) { produce.filter { it.id !in troughIds } }
    val slotsLeft = (FEEDING_TROUGH_MAX - crops.size).coerceAtLeast(0)
    var showPicker by remember { mutableStateOf(false) }

    AppCard(title = "Feeding Trough", collapsible = true, persistKey = "storage.feedingTrough", trailing = {
        Text("${crops.size}/$FEEDING_TROUGH_MAX", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = AppTheme.colors.accent.copy(0.7f))
    }) {
        // First-time tip
        AnimatedVisibility(visible = showTip, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    .clip(AppTheme.shapes.smallShape)
                    .background(AppTheme.colors.accent.copy(alpha = 0.1f))
                    .border(1.dp, AppTheme.colors.accent.copy(alpha = 0.25f), AppTheme.shapes.smallShape)
                    .clickable { onDismissTip() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tap a crop to remove it from the feeding trough.",
                        fontSize = 11.sp, color = AppTheme.colors.accent, lineHeight = 15.sp, modifier = Modifier.weight(1f))
                    Text("OK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.accent,
                        modifier = Modifier.clickable { onDismissTip() })
                }
            }
        }

        val totalTiles = sorted.size + if (slotsLeft > 0 && availableProduce.isNotEmpty()) 1 else 0
        if (totalTiles == 0) {
            Text("Empty", fontSize = 12.sp, color = AppTheme.colors.textMuted)
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
            .clip(AppTheme.shapes.smallShape)
            .border(1.5.dp, AppTheme.colors.accent.copy(alpha = 0.3f), AppTheme.shapes.smallShape)
            .background(AppTheme.colors.surfaceHigh)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.accent)
        Text("Add", fontSize = 8.sp, color = AppTheme.colors.textMuted, lineHeight = 10.sp)
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
    val sorted = remember(produce, apiReady) { produce.sortedBy { RaritySort.item(it.species) } }
    var selected by remember { mutableStateOf(setOf<String>()) } // item IDs

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(AppTheme.shapes.mediumShape)
                .background(AppTheme.colors.surface)
                .padding(16.dp),
        ) {
            Text(
                "Add to Feeding Trough",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary,
            )
            Text(
                "${selected.size}/$maxSelectable selected",
                fontSize = 11.sp,
                color = if (selected.size == maxSelectable) AppTheme.colors.good else AppTheme.colors.textMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )

            if (sorted.isEmpty()) {
                Text("No produce in inventory.", fontSize = 12.sp, color = AppTheme.colors.textMuted)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(TILE_MIN_WIDTH),
                    horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
                    verticalArrangement = Arrangement.spacedBy(TILE_SPACING),
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
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.surfaceHigh),
                ) {
                    Text("Cancel", fontSize = 12.sp, color = AppTheme.colors.textSecondary)
                }
                Button(
                    onClick = {
                        val items = sorted.filter { it.id in selected }
                        onConfirm(items)
                    },
                    enabled = selected.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.accent),
                ) {
                    Text("Add ${selected.size}", fontSize = 12.sp, color = AppTheme.colors.onAccent)
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
    val borderColor = if (isSelected) AppTheme.colors.good else color.copy(alpha = 0.5f)
    val borderWidth = if (isSelected) 2.5.dp else 1.5.dp
    val price = remember(item.species, item.size, item.mutations, apiReady) {
        PriceCalculator.calculateCropSellPrice(item.species, item.size, item.mutations)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (item.mutations.isNotEmpty() || price != null) 0.8f else 1f)
            .clip(AppTheme.shapes.smallShape)
            .border(borderWidth, borderColor, AppTheme.shapes.smallShape)
            .background(if (isSelected) AppTheme.colors.good.copy(0.1f) else AppTheme.colors.surfaceHigh)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SpriteImage(category = "plants", name = item.species, size = 28.dp, contentDescription = name, mutations = item.mutations)
        Text(name, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = AppTheme.colors.textPrimary,
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
            Text("✓", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.good, lineHeight = 12.sp)
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
            .clip(AppTheme.shapes.smallShape)
            .rarityBorder(rarity = entry?.rarity, width = 1.5.dp, shape = AppTheme.shapes.smallShape, alpha = 0.5f)
            .background(AppTheme.colors.surfaceHigh)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SpriteImage(category = "plants", name = item.species, size = 28.dp, contentDescription = name, mutations = item.mutations)
        Text(name, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = AppTheme.colors.textPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 10.sp)
        if (item.mutations.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                sortMutations(item.mutations).take(3).forEach { SpriteImage(url = mutationSpriteUrl(it), size = 12.dp, contentDescription = it) }
            }
        }
    }
}
