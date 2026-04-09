package com.mgafk.app.ui.screens.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.mgafk.app.data.model.InventoryProduceItem
import com.mgafk.app.data.model.InventorySeedItem
import com.mgafk.app.data.model.InventorySnapshot
import com.mgafk.app.data.model.InventoryToolItem
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary

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

private const val MUTATION_SPRITE_BASE = "https://mg-api.ariedam.fr/assets/sprites/ui/Mutation"
private val MUTATION_SPRITE_NAME = mapOf("Ambershine" to "Amberlit")
private fun mutationSpriteUrl(m: String) = "$MUTATION_SPRITE_BASE${MUTATION_SPRITE_NAME[m] ?: m}.png"

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
fun InventoryCard(inventory: InventorySnapshot, apiReady: Boolean = false) {
    val totalItems = inventory.seeds.size + inventory.eggs.size + inventory.produce.size +
        inventory.plants.size + inventory.pets.size + inventory.tools.size + inventory.decors.size

    AppCard(title = "Inventory", collapsible = true, trailing = {
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
                    GridOf(sortedSeeds.size) { i -> QuantityTile(sortedSeeds[i].species, sortedSeeds[i].quantity, apiReady) }
                }
                if (sortedTools.isNotEmpty()) SubSection("Tools", sortedTools.size) {
                    GridOf(sortedTools.size) { i -> QuantityTile(sortedTools[i].toolId, sortedTools[i].quantity, apiReady) }
                }
                if (sortedEggs.isNotEmpty()) SubSection("Eggs", sortedEggs.size) {
                    GridOf(sortedEggs.size) { i -> QuantityTile(sortedEggs[i].eggId, sortedEggs[i].quantity, apiReady) }
                }
                if (sortedPlants.isNotEmpty()) {
                    val totalPlantsValue = remember(sortedPlants) { sortedPlants.sumOf { it.totalPrice } }
                    SubSection("Plants", sortedPlants.size, extraInfo = if (totalPlantsValue > 0) PriceCalculator.formatPrice(totalPlantsValue) else null) {
                        GridOf(sortedPlants.size) { i -> PlantTile(sortedPlants[i], apiReady) }
                    }
                }
                if (sortedProduce.isNotEmpty()) {
                    val totalProduceValue = remember(sortedProduce, apiReady) {
                        sortedProduce.sumOf { p ->
                            PriceCalculator.calculateCropSellPrice(p.species, p.targetScale, p.mutations) ?: 0L
                        }
                    }
                    SubSection("Produce", sortedProduce.size, extraInfo = if (totalProduceValue > 0) PriceCalculator.formatPrice(totalProduceValue) else null) {
                        GridOf(sortedProduce.size) { i -> ProduceTile(sortedProduce[i], apiReady) }
                    }
                }
                if (sortedDecors.isNotEmpty()) SubSection("Decors", sortedDecors.size) {
                    GridOf(sortedDecors.size) { i -> QuantityTile(sortedDecors[i].decorId, sortedDecors[i].quantity, apiReady) }
                }
                if (sortedPets.isNotEmpty()) SubSection("Pets", sortedPets.size) {
                    PetsList(sortedPets, apiReady)
                }
            }
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
private fun ProduceTile(item: InventoryProduceItem, apiReady: Boolean) {
    val entry = remember(item.species, apiReady) { MgApi.findItem(item.species) }
    val color = rarityColor(entry?.rarity)
    val maxS = entry?.maxScale ?: 1.0
    val pct = sizePercent(item.targetScale, maxS)
    val fraction = (pct / 100.0).toFloat().coerceIn(0f, 1f)
    val name = entry?.name?.removeSuffix(" Seed") ?: item.species
    val price = remember(item.species, item.targetScale, item.mutations, apiReady) {
        PriceCalculator.calculateCropSellPrice(item.species, item.targetScale, item.mutations)
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
                item.mutations.take(4).forEach { SpriteImage(url = mutationSpriteUrl(it), size = 16.dp, contentDescription = it) }
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

// ── Pets list ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PetsList(pets: List<InventoryPetItem>, apiReady: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        pets.forEach { pet ->
            val e = remember(pet.petSpecies, apiReady) { MgApi.findPet(pet.petSpecies) }
            val name = pet.name ?: e?.name ?: pet.petSpecies
            val color = rarityColor(e?.rarity)
            val ms = maxStr(pet.petSpecies, pet.targetScale)
            val cs = curStr(pet.petSpecies, pet.xp, ms)
            val isMax = cs >= ms

            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, color.copy(0.25f), RoundedCornerShape(10.dp))
                    .background(SurfaceDark)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpriteImage(url = e?.sprite, size = 32.dp, contentDescription = name)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    // Name + mutations + STR inline
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                        pet.mutations.forEach { SpriteImage(url = mutationSpriteUrl(it), size = 13.dp, contentDescription = it) }
                        // STR inline, small
                        val strText = if (isMax) "MAX $ms" else "$cs/$ms"
                        val strCol = if (isMax) StatusConnected else TextMuted
                        Text(strText, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = strCol)
                    }
                    // Ability badges
                    if (pet.abilities.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            pet.abilities.forEach { id ->
                                AbilityBadge(id, apiReady)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AbilityBadge(abilityId: String, apiReady: Boolean) {
    val entry = remember(abilityId, apiReady) { MgApi.getAbilities()[abilityId] }
    val name = entry?.name ?: abilityId
    val bg = remember(entry?.color) {
        entry?.color?.let {
            try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
        } ?: Color(0xFF646464)
    }
    Text(name, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(bg.copy(0.85f)).padding(horizontal = 6.dp, vertical = 2.dp))
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
