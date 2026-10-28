package com.mgafk.app.ui.screens.storage

import com.mgafk.app.ui.theme.StatusConnecting
import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.mgafk.app.data.model.InventoryPetItem
import com.mgafk.app.data.model.InventoryPlantItem
import com.mgafk.app.data.model.InventoryProduceItem
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.abilityColor
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.ui.components.PlantCompositeSprite
import com.mgafk.app.ui.components.PlantSlotRender
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.rarityColor
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary
import com.mgafk.app.ui.theme.rarityBorder
import com.mgafk.app.ui.components.mutationSpriteUrl
import com.mgafk.app.ui.components.sortMutations
import com.mgafk.app.data.repository.PetTileStrength
import com.mgafk.app.ui.components.GridOf
import com.mgafk.app.ui.components.LockOverlay
import com.mgafk.app.ui.components.PetTile

// ── Quantity tile (seeds, eggs, tools, decors) ──

@Composable
internal fun QuantityTile(itemId: String, quantity: Int, apiReady: Boolean) {
    val entry = remember(itemId, apiReady) { MgApi.findItem(itemId) }
    val name = entry?.name ?: itemId
    val sprite = entry?.sprite
    val color = rarityColor(entry?.rarity)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(AppTheme.shapes.smallShape)
            .rarityBorder(rarity = entry?.rarity, width = 1.5.dp, shape = AppTheme.shapes.smallShape, alpha = 0.5f)
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
internal fun ProduceTile(item: InventoryProduceItem, apiReady: Boolean, playerCount: Int = 1) {
    val entry = remember(item.species, apiReady) { MgApi.findItem(item.species) }
    val color = rarityColor(entry?.rarity)
    val pct = item.size.toDouble()
    val fraction = (pct / 100.0).toFloat().coerceIn(0f, 1f)
    val name = entry?.name?.removeSuffix(" Seed") ?: item.species
    val price = remember(item.species, item.size, item.mutations, apiReady, playerCount) {
        PriceCalculator.calculateCropSellPrice(item.species, item.size, item.mutations, playerCount)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth().aspectRatio(0.85f)
            .clip(AppTheme.shapes.smallShape)
            .rarityBorder(rarity = entry?.rarity, width = 1.5.dp, shape = AppTheme.shapes.smallShape, alpha = 0.5f)
            .background(SurfaceDark)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        SpriteImage(category = "plants", name = item.species, size = 28.dp, contentDescription = name, mutations = item.mutations)
        Text(name, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 10.sp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color.copy(0.15f))) {
                Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(color.copy(0.8f)))
            }
            Spacer(Modifier.width(3.dp))
            Text("${pct.toInt()}", fontSize = 7.sp, color = TextSecondary, fontWeight = FontWeight.Medium, lineHeight = 8.sp)
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
internal fun PlantTile(item: InventoryPlantItem, apiReady: Boolean) {
    val entry = remember(item.species, apiReady) { MgApi.findItem(item.species) }
    val name = entry?.name?.removeSuffix(" Seed") ?: item.species
    val color = rarityColor(entry?.rarity)
    val slots = remember(item.slots) {
        item.slots.map { PlantSlotRender(it.species, it.mutations, MgApi.cropSizeMultiplier(it.species, it.size)) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (item.totalPrice > 0) 0.85f else 1f)
            .clip(AppTheme.shapes.smallShape)
            .rarityBorder(rarity = entry?.rarity, width = 1.5.dp, shape = AppTheme.shapes.smallShape, alpha = 0.5f)
            .background(SurfaceDark)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PlantCompositeSprite(species = item.species, slots = slots, size = 40.dp, contentDescription = name)
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
internal fun PetsList(
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
internal fun PetTileWithPrice(pet: InventoryPetItem, apiReady: Boolean, price: Long, dust: Long = 0L) {
    val entry = remember(pet.petSpecies, apiReady) { MgApi.findPet(pet.petSpecies) }
    val name = pet.name?.ifBlank { null } ?: entry?.name ?: pet.petSpecies
    val rarityId = entry?.rarity
    val ms = PetTileStrength.maxStr(pet.petSpecies, pet.targetScale)
    val cs = PetTileStrength.curStr(pet.petSpecies, pet.xp, ms)
    val isMax = cs >= ms

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(AppTheme.shapes.smallShape)
            .rarityBorder(rarity = rarityId, width = 1.5.dp, shape = AppTheme.shapes.smallShape, alpha = 0.5f)
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
            SpriteImage(category = "pets", name = pet.petSpecies, size = 24.dp, contentDescription = pet.petSpecies, mutations = pet.mutations)
            Text(
                name, fontSize = 7.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 9.sp,
            )
            if (ms > 0) {
                val strText = if (isMax) "$cs" else "$cs/$ms"
                val strColor = if (isMax) StatusConnecting else Accent
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
                    SpriteImage(url = MgApi.coinBagUrl, size = 9.dp, contentDescription = "coins")
                    Text(
                        PriceCalculator.formatPrice(price),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                        lineHeight = 9.sp,
                    )
                }
            }
            if (dust > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    SpriteImage(url = MgApi.magicDustUrl, size = 9.dp, contentDescription = "dust")
                    Text(
                        PriceCalculator.formatPrice(dust),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC084FC),
                        lineHeight = 9.sp,
                    )
                }
            }
        }
    }
}
