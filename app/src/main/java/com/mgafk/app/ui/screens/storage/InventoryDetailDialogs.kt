package com.mgafk.app.ui.screens.storage

import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.InventoryProduceItem
import com.mgafk.app.data.model.InventorySeedItem
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.rarityColor
import com.mgafk.app.ui.components.mutationSpriteUrl
import com.mgafk.app.ui.components.sortMutations
import androidx.compose.ui.window.Dialog
import com.mgafk.app.ui.components.LockToggleIcon

// ── Seed detail dialog ──

@Composable
internal fun SeedDetailDialog(
    seed: InventorySeedItem,
    apiReady: Boolean,
    freePlantTiles: Int,
    isLocked: Boolean,
    hasSeedSilo: Boolean,
    canMoveToSilo: Boolean,
    onPlantSeed: () -> Unit,
    onToggleLock: () -> Unit,
    onMoveToSilo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entry = remember(seed.species, apiReady) { MgApi.findItem(seed.species) }
    val name = entry?.name ?: seed.species
    val color = rarityColor(entry?.rarity)
    val canPlant = freePlantTiles > 0

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(AppTheme.shapes.mediumShape)
                .background(AppTheme.colors.surface),
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
                color = AppTheme.colors.textPrimary,
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
                    Text("Quantity", fontSize = 12.sp, color = AppTheme.colors.textSecondary)
                    Text(
                        PriceCalculator.formatFull(seed.quantity.toLong()),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.colors.textPrimary,
                    )
                }

                // Free tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Free tiles", fontSize = 12.sp, color = AppTheme.colors.textSecondary)
                    Text(
                        "$freePlantTiles",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canPlant) AppTheme.colors.good else AppTheme.colors.bad,
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
                    containerColor = AppTheme.colors.good,
                    disabledContainerColor = AppTheme.colors.good.copy(alpha = 0.2f),
                    disabledContentColor = Color.White.copy(alpha = 0.4f),
                ),
                shape = AppTheme.shapes.smallShape,
            ) {
                Text(
                    if (canPlant) "Plant Seed" else "No free tiles",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canPlant) AppTheme.colors.onGood else AppTheme.colors.onGood.copy(alpha = 0.4f),
                )
            }

            // Only when the player owns a Seed Silo
            if (hasSeedSilo) {
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onMoveToSilo,
                    enabled = canMoveToSilo,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.accent,
                        disabledContainerColor = AppTheme.colors.surfaceHigh,
                    ),
                    shape = AppTheme.shapes.smallShape,
                ) {
                    Text(
                        if (canMoveToSilo) "Move to Seed Silo" else "Seed Silo full",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (canMoveToSilo) AppTheme.colors.onAccent else AppTheme.colors.textMuted,
                    )
                }
            }

        }
            LockToggleIcon(isLocked = isLocked, onClick = onToggleLock,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
    }
}

// ── Generic item detail dialog ──

@Composable
internal fun ItemDetailDialog(
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
                .clip(AppTheme.shapes.mediumShape)
                .background(AppTheme.colors.surface),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            SpriteImage(url = sprite, size = 56.dp, contentDescription = name)

            Spacer(modifier = Modifier.height(10.dp))

            Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)

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
                        Text("Quantity", fontSize = 12.sp, color = AppTheme.colors.textSecondary)
                        Text(PriceCalculator.formatFull(quantity.toLong()), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textPrimary)
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

// ── Produce detail dialog ──

@Composable
internal fun ProduceDetailDialog(
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
    val pct = item.size.toDouble()
    val fraction = (pct / 100.0).toFloat().coerceIn(0f, 1f)
    val price = remember(item.species, item.size, item.mutations, apiReady, playerCount) {
        PriceCalculator.calculateCropSellPrice(item.species, item.size, item.mutations, playerCount)
    }

    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        Dialog(onDismissRequest = { showConfirm = false }) {
            Column(
                modifier = Modifier
                    .clip(AppTheme.shapes.mediumShape)
                    .background(AppTheme.colors.surface)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Sell $name?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                if (price != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SpriteImage(url = MgApi.coinBagUrl, size = 16.dp, contentDescription = "coins")
                        Text(PriceCalculator.formatFull(price), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
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
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.surfaceHigh),
                        shape = AppTheme.shapes.smallShape,
                    ) { Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary) }
                    Button(
                        onClick = { showConfirm = false; onSell() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.bad),
                        shape = AppTheme.shapes.smallShape,
                    ) { Text("Sell", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.onBad) }
                }
            }
        }
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(AppTheme.shapes.mediumShape)
                .background(AppTheme.colors.surface),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SpriteImage(category = "plants", name = item.species, size = 56.dp, contentDescription = name, mutations = item.mutations)

                Spacer(modifier = Modifier.height(10.dp))

                Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)

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
                        Text("Size", fontSize = 12.sp, color = AppTheme.colors.textSecondary)
                        Text("${pct.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textPrimary)
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
                            Text("Sell price", fontSize = 12.sp, color = AppTheme.colors.textSecondary)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                SpriteImage(url = MgApi.coinBagUrl, size = 14.dp, contentDescription = "coins")
                                Text(PriceCalculator.formatFull(price), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
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
                            Text("Mutations", fontSize = 12.sp, color = AppTheme.colors.textSecondary)
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
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.bad),
                    shape = AppTheme.shapes.smallShape,
                ) {
                    Text("Sell", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.onBad)
                }
            }
            LockToggleIcon(isLocked = isLocked, onClick = onToggleLock,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
    }
}
