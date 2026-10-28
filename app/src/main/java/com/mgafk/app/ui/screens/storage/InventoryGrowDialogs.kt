package com.mgafk.app.ui.screens.storage

import com.mgafk.app.ui.theme.OnStatusConnected
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.InventoryEggItem
import com.mgafk.app.data.model.InventoryPlantItem
import com.mgafk.app.data.model.InventoryPlantSlot
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.ui.components.PlantCompositeSprite
import com.mgafk.app.ui.components.PlantSlotRender
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.rarityColor
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary
import com.mgafk.app.ui.components.mutationSpriteUrl
import com.mgafk.app.ui.components.sortMutations
import androidx.compose.ui.window.Dialog
import com.mgafk.app.ui.components.LockToggleIcon

// ── Plant (unpot) dialog ──

@Composable
internal fun PlantUnpotDialog(
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
    val headerSlots = remember(plant.slots) {
        plant.slots.map { PlantSlotRender(it.species, it.mutations, MgApi.cropSizeMultiplier(it.species, it.size)) }
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
            // Header
            PlantCompositeSprite(species = plant.species, slots = headerSlots, size = 80.dp, contentDescription = name)

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
                        PriceCalculator.formatFull(plant.totalPrice),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Crop slots list - capped so the "Plant in Garden" button stays visible
            // on plants with many slots (e.g. 8-slot FavaBean).
            if (plant.slots.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
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
                    color = if (canPlant) StatusConnected else StatusError,
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
                shape = AppTheme.shapes.smallShape,
            ) {
                Text(
                    if (canPlant) "Plant in Garden" else "No free tiles",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canPlant) OnStatusConnected else OnStatusConnected.copy(alpha = 0.4f),
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
    val sizePercent = slot.size.toDouble()
    val fraction = (sizePercent / 100.0).toFloat().coerceIn(0f, 1f)
    val price = remember(slot.species, slot.size, slot.mutations, apiReady) {
        PriceCalculator.calculateCropSellPrice(slot.species, slot.size, slot.mutations)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SpriteImage(
            category = "plants",
            name = slot.species,
            size = 32.dp,
            contentDescription = slot.species,
            mutations = slot.mutations,
        )
        Column(
            modifier = Modifier.weight(1f),
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
                Text("${sizePercent.toInt()}", fontSize = 10.sp, color = TextSecondary, modifier = Modifier.width(28.dp))
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
                        PriceCalculator.formatFull(price),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                    )
                }
            }
        }
    }
}

// ── Egg grow dialog ──

@Composable
internal fun EggGrowDialog(
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

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Quantity", fontSize = 12.sp, color = TextSecondary)
                    Text(PriceCalculator.formatFull(egg.quantity.toLong()), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
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
                        color = if (canGrow) StatusConnected else StatusError,
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
                shape = AppTheme.shapes.smallShape,
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
