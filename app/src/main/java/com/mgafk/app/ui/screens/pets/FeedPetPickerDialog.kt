package com.mgafk.app.ui.screens.pets

import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mgafk.app.data.model.InventoryProduceItem
import com.mgafk.app.data.model.PetSnapshot
import com.mgafk.app.data.model.REPLENISH_POTION_ID
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.rarityColor
import com.mgafk.app.ui.components.mutationSpriteUrl
import com.mgafk.app.ui.components.sortMutations

@Composable
internal fun FeedPetPickerDialog(
    pet: PetSnapshot,
    produce: List<InventoryProduceItem>,
    apiReady: Boolean,
    potionsInInventory: Int,
    potionsInShack: Int,
    onConfirm: (List<String>) -> Unit,
    onUsePotion: () -> Unit,
    onDismiss: () -> Unit,
) {
    val petEntry = remember(pet.species, apiReady) { MgApi.findPet(pet.species) }
    val diet = remember(petEntry) { petEntry?.diet ?: emptyList() }
    val compatible = remember(produce, diet, apiReady) {
        if (diet.isEmpty()) emptyList()
        else produce.filter { it.species in diet }
    }
    var selected by remember { mutableStateOf(setOf<String>()) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(AppTheme.shapes.mediumShape)
                .background(AppTheme.colors.surface)
                .padding(16.dp),
        ) {
            Text(
                "Feed ${pet.name.ifBlank { pet.species }}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary,
            )

            if (diet.isNotEmpty()) {
                val dietNames = remember(diet, apiReady) {
                    diet.mapNotNull { MgApi.findItem(it)?.name?.removeSuffix(" Seed") }
                }
                Text(
                    "Diet: ${dietNames.joinToString(", ")}",
                    fontSize = 10.sp,
                    color = AppTheme.colors.textMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Text(
                "${selected.size} selected",
                fontSize = 11.sp,
                color = if (selected.isNotEmpty()) AppTheme.colors.good else AppTheme.colors.textMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )

            // A potion restores hunger in full on its own, so it is an immediate action
            // rather than another entry in the multi-select grid below.
            if (potionsInInventory + potionsInShack > 0) {
                HungerPotionRow(
                    inInventory = potionsInInventory,
                    inShack = potionsInShack,
                    apiReady = apiReady,
                    onClick = onUsePotion,
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (compatible.isEmpty()) {
                Text(
                    "No compatible produce in inventory.",
                    fontSize = 12.sp,
                    color = AppTheme.colors.textMuted,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
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
                    items(compatible, key = { it.id }) { item ->
                        val isSelected = item.id in selected
                        FeedProduceTile(
                            item = item,
                            apiReady = apiReady,
                            isSelected = isSelected,
                            onClick = {
                                selected = if (isSelected) selected - item.id else selected + item.id
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
                    onClick = { onConfirm(selected.toList()) },
                    enabled = selected.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.accent),
                ) {
                    Text("Feed ${selected.size}", fontSize = 12.sp, color = AppTheme.colors.onAccent)
                }
            }
        }
    }
}

/**
 * One-tap Hunger Potion action inside the feed picker. Shown only when the player owns at
 * least one, counting both the inventory and the Tool Shack: a potion still in the shack is
 * retrieved before use, which the caller handles.
 */
@Composable
private fun HungerPotionRow(
    inInventory: Int,
    inShack: Int,
    apiReady: Boolean,
    onClick: () -> Unit,
) {
    val entry = remember(apiReady) { MgApi.findItem(REPLENISH_POTION_ID) }
    val name = entry?.name ?: "Hunger Potion"
    val stockText = when {
        inInventory > 0 && inShack > 0 -> "x$inInventory in inventory, x$inShack in Tool Shack"
        inInventory > 0 -> "x$inInventory in inventory"
        else -> "x$inShack in Tool Shack, retrieved on use"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.smallShape)
            .border(1.5.dp, AppTheme.colors.accent.copy(alpha = 0.5f), AppTheme.shapes.smallShape)
            .background(AppTheme.colors.accent.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SpriteImage(url = entry?.sprite, size = 28.dp, contentDescription = name)
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textPrimary)
            Text(stockText, fontSize = 10.sp, color = AppTheme.colors.textMuted, lineHeight = 13.sp)
        }
        Text("Use", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.accent)
    }
}

@Composable
private fun FeedProduceTile(
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
            Text("\u2713", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.good, lineHeight = 12.sp)
        }
    }
}
