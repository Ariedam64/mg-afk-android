package com.mgafk.app.ui.screens.garden

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.GardenPlantSnapshot
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.PlantCompositeSprite
import com.mgafk.app.ui.components.PlantSlotRender
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary
import com.mgafk.app.ui.theme.rarityBorder
import androidx.compose.ui.window.Dialog
import com.mgafk.app.ui.components.mutationSpriteUrl
import com.mgafk.app.ui.components.sortMutations

// Game-authentic rarity colors
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

private val RARITY_TIERS = listOf("Common", "Uncommon", "Rare", "Legendary", "Mythic", "Divine", "Celestial")

private val TILE_MIN_WIDTH = 76.dp
private val TILE_SPACING = 6.dp

/** Growth progress 0..100 based on startTime/endTime vs now. */
private fun growthPercent(startTime: Long, endTime: Long): Int {
    if (endTime <= startTime) return 100
    val now = System.currentTimeMillis()
    if (now >= endTime) return 100
    return ((now - startTime).toDouble() / (endTime - startTime) * 100).toInt().coerceIn(0, 100)
}

/** Format remaining time as compact string (e.g. "2m 30s", "1h 5m"). */
private fun formatTimeRemaining(endTime: Long): String {
    val remaining = endTime - System.currentTimeMillis()
    if (remaining <= 0) return ""
    val totalSec = remaining / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

/** A tick that increments every second to force recomposition of time-dependent UI. */
@Composable
private fun rememberSecondTick(): Long {
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick = System.currentTimeMillis()
        }
    }
    return tick
}

private fun fmtQty(q: Int): String = when {
    q >= 1_000_000 -> "%.1fM".format(q / 1_000_000.0)
    q >= 10_000 -> "${q / 1000}K"
    q >= 1_000 -> "%.1fK".format(q / 1000.0)
    else -> "$q"
}

private fun computeSizePercent(targetScale: Double, maxScale: Double): Double {
    if (maxScale <= 1.0) return if (targetScale >= 1.0) 100.0 else targetScale * 100.0
    return if (targetScale <= 1.0) {
        targetScale * 50.0
    } else {
        50.0 + (targetScale - 1.0) / (maxScale - 1.0) * 50.0
    }.coerceIn(0.0, 100.0)
}

/** Pre-resolved plant data — computed once per plants change, reused by filters + tiles. */
private data class ResolvedPlant(
    val snapshot: GardenPlantSnapshot,
    val rarity: String?,
    val cropSprite: String?,
    val maxScale: Double,
    val displayName: String,
    val sellPrice: Long?,
)

/** A garden entry is either a single crop or a multi-slot plant grouping multiple crops. */
private sealed class GardenEntry {
    abstract val tileId: Int
    abstract val rarity: String?
    abstract val displayName: String
    abstract val totalValue: Long

    data class SingleCrop(val plant: ResolvedPlant) : GardenEntry() {
        override val tileId get() = plant.snapshot.tileId
        override val rarity get() = plant.rarity
        override val displayName get() = plant.displayName
        override val totalValue get() = plant.sellPrice ?: 0L
    }

    data class MultiSlotPlant(
        override val tileId: Int,
        override val rarity: String?,
        override val displayName: String,
        val cropSprite: String?,
        val crops: List<ResolvedPlant>,
    ) : GardenEntry() {
        override val totalValue get() = crops.sumOf { it.sellPrice ?: 0L }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GardenCard(
    plants: List<GardenPlantSnapshot>,
    apiReady: Boolean = false,
    onHarvest: (slot: Int, slotIndex: Int) -> Unit = { _, _ -> },
    onWater: (slot: Int) -> Unit = {},
    onPot: (slot: Int) -> Unit = {},
    wateringCans: Int = 0,
    planterPots: Int = 0,
) {
    var selectedRarity by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMutation by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCropKey by remember { mutableStateOf<Pair<Int, Int>?>(null) } // tileId to slotIndex
    var selectedMultiPlantTileId by remember { mutableStateOf<Int?>(null) }

    // Pre-resolve all API lookups once when plants/apiReady change
    val resolved = remember(plants, apiReady) {
        plants.map { plant ->
            val entry = MgApi.findItem(plant.species)
            ResolvedPlant(
                snapshot = plant,
                rarity = entry?.rarity,
                cropSprite = entry?.cropSprite,
                maxScale = entry?.maxScale ?: 1.0,
                displayName = entry?.name?.removeSuffix(" Seed") ?: plant.species,
                sellPrice = PriceCalculator.calculateCropSellPrice(plant.species, plant.targetScale, plant.mutations),
            )
        }
    }

    // Group into garden entries: single crops stay as-is, multi-slot get grouped
    val entries = remember(resolved) {
        val byTile = resolved.groupBy { it.snapshot.tileId }
        byTile.map { (tileId, crops) ->
            if (crops.size == 1) {
                GardenEntry.SingleCrop(crops.first())
            } else {
                val first = crops.first()
                GardenEntry.MultiSlotPlant(
                    tileId = tileId,
                    rarity = first.rarity,
                    displayName = first.displayName,
                    cropSprite = first.cropSprite,
                    crops = crops.sortedBy { it.snapshot.slotIndex },
                )
            }
        }
    }

    val allMutations = remember(plants) {
        sortMutations(plants.flatMap { it.mutations }.distinct())
    }

    val allRarities = remember(resolved) {
        resolved.mapNotNull { it.rarity }
            .distinct()
            .sortedBy { RARITY_TIERS.indexOf(it).let { i -> if (i < 0) 99 else i } }
    }

    // Reset filters if they no longer match available options
    val safeRarity = selectedRarity?.takeIf { it in allRarities }
    val safeMutation = selectedMutation?.takeIf { it in allMutations }
    if (safeRarity != selectedRarity) selectedRarity = safeRarity
    if (safeMutation != selectedMutation) selectedMutation = safeMutation

    // Filter entries
    val filtered = remember(entries, safeRarity, safeMutation) {
        if (safeRarity == null && safeMutation == null) entries
        else entries.filter { entry ->
            when (entry) {
                is GardenEntry.SingleCrop -> {
                    (safeRarity == null || entry.rarity == safeRarity) &&
                        (safeMutation == null || safeMutation in entry.plant.snapshot.mutations)
                }
                is GardenEntry.MultiSlotPlant -> {
                    (safeRarity == null || entry.rarity == safeRarity) &&
                        (safeMutation == null || entry.crops.any { safeMutation in it.snapshot.mutations })
                }
            }
        }
    }

    val totalValue = remember(filtered) {
        filtered.sumOf { it.totalValue }
    }

    // Count total individual crops for header
    val totalCropCount = remember(entries) {
        entries.sumOf { when (it) {
            is GardenEntry.SingleCrop -> 1
            is GardenEntry.MultiSlotPlant -> it.crops.size
        } }
    }
    val filteredCropCount = remember(filtered) {
        filtered.sumOf { when (it) {
            is GardenEntry.SingleCrop -> 1
            is GardenEntry.MultiSlotPlant -> it.crops.size
        } }
    }

    AppCard(
        title = "Plants",
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (totalValue > 0) {
                    Text(
                        text = PriceCalculator.formatPrice(totalValue),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFFD700),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(
                    text = if (safeRarity != null || safeMutation != null)
                        "$filteredCropCount/$totalCropCount" else "$totalCropCount plants",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Accent.copy(alpha = 0.7f),
                )
            }
        },
        collapsible = true,
        persistKey = "garden.plants",
    ) {
        if (plants.isEmpty()) {
            Text("No plants in the garden.", fontSize = 12.sp, color = TextMuted)
        } else {
            // ── Filters ──
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                allRarities.forEach { rarity ->
                    val isSelected = rarity == safeRarity
                    val color = rarityColor(rarity)
                    Text(
                        text = rarity,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) color else TextSecondary,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                if (isSelected) color.copy(alpha = 0.5f) else SurfaceBorder,
                                RoundedCornerShape(12.dp),
                            )
                            .background(if (isSelected) color.copy(alpha = 0.18f) else SurfaceCard)
                            .clickable { selectedRarity = if (isSelected) null else rarity }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                allMutations.forEach { mutation ->
                    val isSelected = mutation == safeMutation
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .border(
                                1.5.dp,
                                if (isSelected) Accent else SurfaceBorder,
                                CircleShape,
                            )
                            .background(if (isSelected) Accent.copy(alpha = 0.18f) else SurfaceCard)
                            .clickable { selectedMutation = if (isSelected) null else mutation },
                        contentAlignment = Alignment.Center,
                    ) {
                        SpriteImage(
                            url = mutationSpriteUrl(mutation),
                            size = 18.dp,
                            contentDescription = mutation,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Garden grid ──
            if (filtered.isEmpty()) {
                Text("No plants match filters.", fontSize = 12.sp, color = TextMuted)
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val columns = ((maxWidth + TILE_SPACING) / (TILE_MIN_WIDTH + TILE_SPACING))
                        .toInt().coerceAtLeast(1)
                    val rows = filtered.chunked(columns)

                    Column(verticalArrangement = Arrangement.spacedBy(TILE_SPACING)) {
                        rows.forEach { rowEntries ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
                            ) {
                                rowEntries.forEach { entry ->
                                    when (entry) {
                                        is GardenEntry.SingleCrop -> {
                                            Box(modifier = Modifier.weight(1f).clickable {
                                                selectedCropKey = entry.plant.snapshot.tileId to entry.plant.snapshot.slotIndex
                                            }) {
                                                GardenPlantTile(entry.plant)
                                            }
                                        }
                                        is GardenEntry.MultiSlotPlant -> {
                                            Box(modifier = Modifier.weight(1f).clickable {
                                                selectedMultiPlantTileId = entry.tileId
                                            }) {
                                                MultiSlotPlantTile(entry)
                                            }
                                        }
                                    }
                                }
                                repeat(columns - rowEntries.size) {
                                    Box(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Single crop detail dialog — look up live data so timestamps update in real-time
    selectedCropKey?.let { (tileId, slotIndex) ->
        val liveCrop = resolved.find { it.snapshot.tileId == tileId && it.snapshot.slotIndex == slotIndex }
        if (liveCrop != null) {
            PlantDetailDialog(
                plant = liveCrop,
                wateringCans = wateringCans,
                planterPots = planterPots,
                onHarvest = {
                    onHarvest(tileId, slotIndex)
                    selectedCropKey = null
                },
                onWater = { onWater(tileId) },
                onPot = {
                    onPot(tileId)
                    selectedCropKey = null
                },
                onDismiss = { selectedCropKey = null },
            )
        } else {
            selectedCropKey = null
        }
    }

    // Multi-slot plant detail dialog — look up live data
    selectedMultiPlantTileId?.let { tileId ->
        val liveEntry = entries.firstOrNull { it.tileId == tileId } as? GardenEntry.MultiSlotPlant
        if (liveEntry != null) {
            MultiSlotPlantDetailDialog(
                plant = liveEntry,
                wateringCans = wateringCans,
                planterPots = planterPots,
                onHarvest = { slot, slotIndex -> onHarvest(slot, slotIndex) },
                onWater = { slot -> onWater(slot) },
                onPot = { slot -> onPot(slot) },
                onDismiss = { selectedMultiPlantTileId = null },
            )
        } else {
            selectedMultiPlantTileId = null
        }
    }
}

// ── Single crop tile (unchanged) ──

@Composable
private fun GardenPlantTile(rp: ResolvedPlant) {
    val color = rarityColor(rp.rarity)
    val sizePercent = computeSizePercent(rp.snapshot.targetScale, rp.maxScale)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(10.dp))
            .rarityBorder(rarity = rp.rarity, width = 1.5.dp, shape = RoundedCornerShape(10.dp), alpha = 0.5f)
            .background(SurfaceDark)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        SpriteImage(
            category = "plants",
            name = rp.snapshot.species,
            size = 28.dp,
            contentDescription = rp.displayName,
            mutations = rp.snapshot.mutations,
        )

        Text(
            text = rp.displayName,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 10.sp,
        )

        SizeBar(percent = sizePercent, color = color)

        if (rp.sellPrice != null) {
            Text(
                text = PriceCalculator.formatPrice(rp.sellPrice),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD700),
                lineHeight = 10.sp,
            )
        }

        if (rp.snapshot.mutations.isNotEmpty()) {
            MutationIcons(mutations = rp.snapshot.mutations)
        }
    }
}

// ── Multi-slot plant tile (like inventory PlantTile) ──

@Composable
private fun MultiSlotPlantTile(entry: GardenEntry.MultiSlotPlant) {
    val color = rarityColor(entry.rarity)
    val species = remember(entry.tileId) { entry.crops.firstOrNull()?.snapshot?.species ?: "" }
    val slots = remember(entry.crops) {
        entry.crops.map { PlantSlotRender(it.snapshot.species, it.snapshot.mutations, it.snapshot.targetScale) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (entry.totalValue > 0) 0.85f else 1f)
            .clip(RoundedCornerShape(10.dp))
            .rarityBorder(rarity = entry.rarity, width = 1.5.dp, shape = RoundedCornerShape(10.dp), alpha = 0.5f)
            .background(SurfaceDark)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PlantCompositeSprite(species = species, slots = slots, size = 40.dp, contentDescription = entry.displayName)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            entry.displayName,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 10.sp,
        )
        Text(
            "${entry.crops.size} slots",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Accent,
            lineHeight = 11.sp,
        )
        if (entry.totalValue > 0) {
            Text(
                PriceCalculator.formatPrice(entry.totalValue),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD700),
                lineHeight = 10.sp,
            )
        }
    }
}

// ── Size bar ──

@Composable
private fun SizeBar(percent: Double, color: Color, showLabel: Boolean = true) {
    val fraction = (percent / 100.0).toFloat().coerceIn(0f, 1f)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color.copy(alpha = 0.15f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .background(color.copy(alpha = 0.8f)),
            )
        }
        if (showLabel) {
            Spacer(modifier = Modifier.size(3.dp))
            Text(
                text = "${percent.toInt()}%",
                fontSize = 7.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium,
                lineHeight = 8.sp,
            )
        }
    }
}

// ── Mutation icons ──

@Composable
private fun MutationIcons(mutations: List<String>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sortMutations(mutations).take(4).forEach { mutation ->
            SpriteImage(
                url = mutationSpriteUrl(mutation),
                size = 12.dp,
                contentDescription = mutation,
            )
        }
    }
}

// ── Single crop detail dialog ──

@Composable
private fun PlantDetailDialog(
    plant: ResolvedPlant,
    wateringCans: Int,
    planterPots: Int,
    onHarvest: () -> Unit,
    onWater: () -> Unit,
    onPot: () -> Unit,
    onDismiss: () -> Unit,
) {
    val color = rarityColor(plant.rarity)
    val sizePercent = computeSizePercent(plant.snapshot.targetScale, plant.maxScale)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Sprite
            SpriteImage(
                category = "plants",
                name = plant.snapshot.species,
                size = 56.dp,
                contentDescription = plant.displayName,
                mutations = plant.snapshot.mutations,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Name
            Text(
                plant.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )

            // Rarity
            if (plant.rarity != null) {
                Text(
                    plant.rarity,
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
                // Size
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Size", fontSize = 12.sp, color = TextSecondary)
                    Text("${sizePercent.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                SizeBar(percent = sizePercent, color = color, showLabel = false)

                // Sell price
                if (plant.sellPrice != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Sell price", fontSize = 12.sp, color = TextSecondary)
                        Text(
                            PriceCalculator.formatFull(plant.sellPrice),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700),
                        )
                    }
                }

                // Tile
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Tile", fontSize = 12.sp, color = TextSecondary)
                    Text("#${plant.snapshot.tileId}", fontSize = 12.sp, color = TextPrimary)
                }

                // Mutations
                if (plant.snapshot.mutations.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Mutations", fontSize = 12.sp, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            sortMutations(plant.snapshot.mutations).forEach { mutation ->
                                SpriteImage(url = mutationSpriteUrl(mutation), size = 16.dp, contentDescription = mutation)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tick every second for live countdown
            val now = rememberSecondTick()
            val isMature = plant.snapshot.endTime > 0 && now >= plant.snapshot.endTime
            val canWater = !isMature && wateringCans > 0

            // Remaining time info (shown above buttons when growing)
            if (!isMature) {
                val remaining = formatTimeRemaining(plant.snapshot.endTime)
                if (remaining.isNotEmpty()) {
                    Text(
                        remaining,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    )
                }
            }

            val canPot = planterPots > 0
            val potSprite = remember { MgApi.findItem("PlanterPot")?.sprite }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Water button
                Button(
                    onClick = onWater,
                    enabled = canWater,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6),
                        disabledContainerColor = Color(0xFF3B82F6).copy(alpha = 0.2f),
                        disabledContentColor = Color.White.copy(alpha = 0.4f),
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        if (!isMature && wateringCans > 0) "Water ($wateringCans)"
                        else if (isMature) "Mature"
                        else "No cans",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canWater) Color.White else Color.White.copy(alpha = 0.4f),
                    )
                }

                // Harvest button
                Button(
                    onClick = onHarvest,
                    enabled = isMature,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusConnected,
                        disabledContainerColor = StatusConnected.copy(alpha = 0.2f),
                        disabledContentColor = Color.White.copy(alpha = 0.4f),
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        if (isMature) "Harvest" else "Growing…",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMature) Color.White else Color.White.copy(alpha = 0.4f),
                    )
                }
            }

            // Pot button (separate row)
            Button(
                onClick = onPot,
                enabled = canPot,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD97706),
                    disabledContainerColor = Color(0xFFD97706).copy(alpha = 0.2f),
                    disabledContentColor = Color.White.copy(alpha = 0.4f),
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                SpriteImage(url = potSprite, size = 18.dp, contentDescription = "pot")
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    if (canPot) "Pot x${fmtQty(planterPots)}" else "No pots",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canPot) Color.White else Color.White.copy(alpha = 0.4f),
                )
            }
        }
    }
}

// ── Multi-slot plant detail dialog ──

@Composable
private fun MultiSlotPlantDetailDialog(
    plant: GardenEntry.MultiSlotPlant,
    wateringCans: Int,
    planterPots: Int,
    onHarvest: (slot: Int, slotIndex: Int) -> Unit,
    onWater: (slot: Int) -> Unit,
    onPot: (slot: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val color = rarityColor(plant.rarity)
    val species = remember(plant.tileId) { plant.crops.firstOrNull()?.snapshot?.species ?: "" }
    val headerSlots = remember(plant.crops) {
        plant.crops.map { PlantSlotRender(it.snapshot.species, it.snapshot.mutations, it.snapshot.targetScale) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            PlantCompositeSprite(species = species, slots = headerSlots, size = 80.dp, contentDescription = plant.displayName)

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                plant.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )

            if (plant.rarity != null) {
                Text(
                    plant.rarity,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
            }

            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Tile #${plant.tileId}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
                Text(
                    "${plant.crops.size} slots",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Accent,
                )
                if (plant.totalValue > 0) {
                    Text(
                        PriceCalculator.formatFull(plant.totalValue),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tick every second for live countdown
            val now = rememberSecondTick()

            // Scrollable crop list — capped so the Pot button stays visible on
            // plants with many slots (e.g. 8-slot FavaBean).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                plant.crops.forEachIndexed { index, crop ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = SurfaceBorder.copy(alpha = 0.4f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    CropSlotRow(
                        crop = crop,
                        slotLabel = "Slot ${crop.snapshot.slotIndex + 1}",
                        color = color,
                        wateringCans = wateringCans,
                        now = now,
                        onHarvest = { onHarvest(crop.snapshot.tileId, crop.snapshot.slotIndex) },
                        onWater = { onWater(crop.snapshot.tileId) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pot button for the whole plant
            val canPot = planterPots > 0
            val potSprite = remember { MgApi.findItem("PlanterPot")?.sprite }
            Button(
                onClick = { onPot(plant.tileId) },
                enabled = canPot,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD97706),
                    disabledContainerColor = Color(0xFFD97706).copy(alpha = 0.2f),
                    disabledContentColor = Color.White.copy(alpha = 0.4f),
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                SpriteImage(url = potSprite, size = 18.dp, contentDescription = "pot")
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    if (canPot) "Pot x${fmtQty(planterPots)}" else "No pots",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canPot) Color.White else Color.White.copy(alpha = 0.4f),
                )
            }
        }
    }
}

// ── Individual crop row inside multi-slot dialog ──

@Composable
private fun CropSlotRow(
    crop: ResolvedPlant,
    slotLabel: String,
    color: Color,
    wateringCans: Int,
    now: Long,
    onHarvest: () -> Unit,
    onWater: () -> Unit,
) {
    val sizePercent = computeSizePercent(crop.snapshot.targetScale, crop.maxScale)
    val isMature = crop.snapshot.endTime > 0 && now >= crop.snapshot.endTime
    val canWater = !isMature && wateringCans > 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SpriteImage(
            category = "plants",
            name = crop.snapshot.species,
            size = 32.dp,
            contentDescription = crop.displayName,
            mutations = crop.snapshot.mutations,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
        // Slot label + mutations + remaining time
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(slotLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isMature) {
                    val remaining = formatTimeRemaining(crop.snapshot.endTime)
                    if (remaining.isNotEmpty()) {
                        Text(remaining, fontSize = 9.sp, color = TextSecondary)
                    }
                }
                if (crop.snapshot.mutations.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        sortMutations(crop.snapshot.mutations).forEach { mutation ->
                            SpriteImage(url = mutationSpriteUrl(mutation), size = 14.dp, contentDescription = mutation)
                        }
                    }
                }
            }
        }

        // Size bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("${sizePercent.toInt()}%", fontSize = 10.sp, color = TextSecondary, modifier = Modifier.width(28.dp))
            Box(modifier = Modifier.weight(1f)) {
                SizeBar(percent = sizePercent, color = color, showLabel = false)
            }
            if (crop.sellPrice != null) {
                Text(
                    PriceCalculator.formatFull(crop.sellPrice),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700),
                )
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(
                onClick = onWater,
                enabled = canWater,
                modifier = Modifier.weight(1f).height(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3B82F6),
                    disabledContainerColor = Color(0xFF3B82F6).copy(alpha = 0.2f),
                    disabledContentColor = Color.White.copy(alpha = 0.4f),
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text(
                    if (canWater) "Water" else if (isMature) "Mature" else "No cans",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canWater) Color.White else Color.White.copy(alpha = 0.4f),
                )
            }
            Button(
                onClick = onHarvest,
                enabled = isMature,
                modifier = Modifier.weight(1f).height(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusConnected,
                    disabledContainerColor = StatusConnected.copy(alpha = 0.2f),
                    disabledContentColor = Color.White.copy(alpha = 0.4f),
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text(
                    if (isMature) "Harvest" else "Growing…",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isMature) Color.White else Color.White.copy(alpha = 0.4f),
                )
            }
        }
        }
    }
}
