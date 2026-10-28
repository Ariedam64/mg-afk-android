package com.mgafk.app.ui.screens.garden

import com.mgafk.app.ui.theme.AppTheme
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.GardenEggSnapshot
import com.mgafk.app.data.model.GardenPlantSnapshot
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.MultiSelectChip
import com.mgafk.app.ui.components.RarityFilterRow
import com.mgafk.app.ui.components.SearchFilterBar
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextSecondary
import com.mgafk.app.ui.components.mutationSpriteUrl
import com.mgafk.app.ui.components.sortMutations
import com.mgafk.app.ui.components.TILE_MIN_WIDTH
import com.mgafk.app.ui.components.TILE_SPACING

private val RARITY_TIERS = listOf("Common", "Uncommon", "Rare", "Legendary", "Mythic", "Divine", "Celestial")

/** Growth progress 0..100 based on startTime/endTime vs now. */
private fun growthPercent(startTime: Long, endTime: Long): Int {
    if (endTime <= startTime) return 100
    val now = System.currentTimeMillis()
    if (now >= endTime) return 100
    return ((now - startTime).toDouble() / (endTime - startTime) * 100).toInt().coerceIn(0, 100)
}

/** A tick that increments every second to force recomposition of time-dependent UI. */
@Composable
internal fun rememberSecondTick(): Long {
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick = System.currentTimeMillis()
        }
    }
    return tick
}

/** Pre-resolved plant data - computed once per plants change, reused by filters + tiles. */
internal data class ResolvedPlant(
    val snapshot: GardenPlantSnapshot,
    val rarity: String?,
    val cropSprite: String?,
    val displayName: String,
    val sellPrice: Long?,
)

/** A garden entry is either a single crop or a multi-slot plant grouping multiple crops. */
internal sealed class GardenEntry {
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

private enum class SortMode(val label: String) {
    NONE("Default"),
    SIZE_ASC("Size ↑"),
    SIZE_DESC("Size ↓"),
    PRICE_ASC("Price ↑"),
    PRICE_DESC("Price ↓"),
}

private fun GardenEntry.sizePercent(): Double = when (this) {
    is GardenEntry.SingleCrop -> plant.snapshot.size.toDouble()
    is GardenEntry.MultiSlotPlant -> {
        if (crops.isEmpty()) 0.0
        else crops.map { it.snapshot.size.toDouble() }.average()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GardenCard(
    plants: List<GardenPlantSnapshot>,
    apiReady: Boolean = false,
    /** Only used by the map view, to mark the tiles they occupy. */
    gardenEggs: List<GardenEggSnapshot> = emptyList(),
    onHarvest: (slot: Int, slotIndex: Int) -> Unit = { _, _ -> },
    onWater: (slot: Int) -> Unit = {},
    onPot: (slot: Int) -> Unit = {},
    onCleanse: (tileId: Int, slotIndex: Int) -> Unit = { _, _ -> },
    wateringCans: Int = 0,
    planterPots: Int = 0,
    cropCleansers: Int = 0,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedRarity by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMutations by rememberSaveable(
        stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() }),
    ) { mutableStateOf(emptySet<String>()) }
    var selectedCropKey by remember { mutableStateOf<Pair<Int, Int>?>(null) } // tileId to slotIndex
    var selectedMultiPlantTileId by remember { mutableStateOf<Int?>(null) }
    var sortModeName by rememberSaveable { mutableStateOf("NONE") }
    var mapView by rememberSaveable { mutableStateOf(false) }
    val sortMode = remember(sortModeName) { SortMode.entries.find { it.name == sortModeName } ?: SortMode.NONE }
    var minSizePercent by rememberSaveable { mutableStateOf(0f) }

    // Pre-resolve all API lookups once when plants/apiReady change
    val resolved = remember(plants, apiReady) {
        plants.map { plant ->
            val entry = MgApi.findItem(plant.species)
            ResolvedPlant(
                snapshot = plant,
                rarity = entry?.rarity,
                cropSprite = entry?.cropSprite,
                displayName = entry?.name?.removeSuffix(" Seed") ?: plant.species,
                sellPrice = PriceCalculator.calculateCropSellPrice(plant.species, plant.size, plant.mutations),
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
    val safeMutations = selectedMutations.filterTo(mutableSetOf()) { it in allMutations }
    if (safeRarity != selectedRarity) selectedRarity = safeRarity
    if (safeMutations != selectedMutations) selectedMutations = safeMutations

    // See GardenFilter: a patch plant is narrowed to its matching crops rather than kept
    // whole, so a filtered Clover tile shows only the crops that match.
    val minSize = minSizePercent.toDouble()
    val filtered = remember(entries, safeRarity, safeMutations, minSize, searchQuery) {
        GardenFilter.apply(entries, safeRarity, safeMutations, minSize, searchQuery)
    }

    // Sort entries
    val sorted = remember(filtered, sortMode) {
        when (sortMode) {
            SortMode.NONE -> filtered
            SortMode.SIZE_ASC -> filtered.sortedBy { it.sizePercent() }
            SortMode.SIZE_DESC -> filtered.sortedByDescending { it.sizePercent() }
            SortMode.PRICE_ASC -> filtered.sortedBy { it.totalValue }
            SortMode.PRICE_DESC -> filtered.sortedByDescending { it.totalValue }
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
                val hasFilter = safeRarity != null || safeMutations.isNotEmpty() || minSizePercent > 0f || sortMode != SortMode.NONE
                Text(
                    text = if (hasFilter)
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
            val activeFilterCount = listOfNotNull(
                safeRarity?.let { 1 },
                if (safeMutations.isNotEmpty()) 1 else null,
                if (minSizePercent > 0f) 1 else null,
                if (sortMode != SortMode.NONE) 1 else null,
            ).sum()

            SearchFilterBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search plants...",
                activeFilterCount = activeFilterCount,
                filtersExpanded = filtersExpanded,
                onFiltersExpandedChange = { filtersExpanded = it },
                onClearFilters = {
                    selectedRarity = null
                    selectedMutations = emptySet()
                    minSizePercent = 0f
                    sortModeName = SortMode.NONE.name
                },
            ) {
                // ── Rarity filter ──
                RarityFilterRow(rarities = allRarities, selected = safeRarity, onSelect = { selectedRarity = it })

                // ── Mutation filter (multi-select, combines with AND) ──
                if (allMutations.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        allMutations.forEach { mutation ->
                            val isSelected = mutation in safeMutations
                            MultiSelectChip(
                                selected = isSelected,
                                onClick = {
                                    selectedMutations = if (isSelected) safeMutations - mutation else safeMutations + mutation
                                },
                            ) {
                                SpriteImage(
                                    url = mutationSpriteUrl(mutation),
                                    size = 18.dp,
                                    contentDescription = mutation,
                                )
                            }
                        }
                    }
                }

                // ── Sort chips ──
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Sort:", fontSize = 10.sp, color = TextMuted, modifier = Modifier.align(Alignment.CenterVertically))
                    SortMode.entries.forEach { mode ->
                        if (mode == SortMode.NONE) return@forEach
                        val isSelected = sortMode == mode
                        Text(
                            text = mode.label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Accent else TextSecondary,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(AppTheme.shapes.smallShape)
                                .border(
                                    1.dp,
                                    if (isSelected) Accent.copy(alpha = 0.5f) else SurfaceBorder,
                                    AppTheme.shapes.smallShape,
                                )
                                .background(if (isSelected) Accent.copy(alpha = 0.18f) else SurfaceCard)
                                .clickable { sortModeName = if (isSelected) SortMode.NONE.name else mode.name }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                // ── Min size slider ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Min size:", fontSize = 10.sp, color = TextMuted)
                    Slider(
                        value = minSizePercent,
                        onValueChange = { minSizePercent = it },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f).height(24.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Accent,
                            activeTrackColor = Accent,
                            inactiveTrackColor = SurfaceBorder,
                        ),
                    )
                    Text(
                        text = "${minSizePercent.toInt()}%",
                        fontSize = 10.sp,
                        color = if (minSizePercent > 0f) Accent else TextSecondary,
                        fontWeight = if (minSizePercent > 0f) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── View switch ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ViewModeChip(label = "List", selected = !mapView) { mapView = false }
                ViewModeChip(label = "Map", selected = mapView) { mapView = true }
                if (mapView) {
                    Text(
                        "Drag to move, pinch to zoom.",
                        fontSize = 10.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (mapView) {
                // Filters dim tiles here instead of removing them: the point of this view is
                // where things sit, and a hole would read as free dirt.
                val entriesByTile = remember(entries) { entries.associateBy { it.tileId } }
                val matchingTileIds = remember(sorted) { sorted.map { it.tileId }.toSet() }
                val eggSpriteByTile = remember(gardenEggs, apiReady) {
                    gardenEggs.associate { it.tileId to MgApi.findItem(it.eggId)?.sprite }
                }
                GardenLayoutGrid(
                    entriesByTile = entriesByTile,
                    matchingTileIds = matchingTileIds,
                    filtersActive = activeFilterCount > 0 || searchQuery.isNotBlank(),
                    eggSpriteByTile = eggSpriteByTile,
                    // This build does not track garden decor, so decor tiles read as free here.
                    decorSpriteByTile = emptyMap(),
                    onSelectPlant = { entry ->
                        when (entry) {
                            is GardenEntry.SingleCrop ->
                                selectedCropKey = entry.plant.snapshot.tileId to entry.plant.snapshot.slotIndex
                            is GardenEntry.MultiSlotPlant -> selectedMultiPlantTileId = entry.tileId
                        }
                    },
                )
            } else if (sorted.isEmpty()) {
                Text("No plants match filters.", fontSize = 12.sp, color = TextMuted)
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val columns = ((maxWidth + TILE_SPACING) / (TILE_MIN_WIDTH + TILE_SPACING))
                        .toInt().coerceAtLeast(1)
                    val rows = sorted.chunked(columns)

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

    // Single crop detail dialog - look up live data so timestamps update in real-time
    selectedCropKey?.let { (tileId, slotIndex) ->
        val liveCrop = resolved.find { it.snapshot.tileId == tileId && it.snapshot.slotIndex == slotIndex }
        if (liveCrop != null) {
            PlantDetailDialog(
                plant = liveCrop,
                wateringCans = wateringCans,
                planterPots = planterPots,
                cropCleansers = cropCleansers,
                onHarvest = {
                    onHarvest(tileId, slotIndex)
                    selectedCropKey = null
                },
                onWater = { onWater(tileId) },
                onPot = {
                    onPot(tileId)
                    selectedCropKey = null
                },
                onCleanse = { onCleanse(tileId, slotIndex) },
                onDismiss = { selectedCropKey = null },
            )
        } else {
            selectedCropKey = null
        }
    }

    // Multi-slot plant detail dialog - look up live data
    selectedMultiPlantTileId?.let { tileId ->
        val liveEntry = entries.firstOrNull { it.tileId == tileId } as? GardenEntry.MultiSlotPlant
        if (liveEntry != null) {
            MultiSlotPlantDetailDialog(
                plant = liveEntry,
                wateringCans = wateringCans,
                planterPots = planterPots,
                cropCleansers = cropCleansers,
                onHarvest = { slot, slotIndex -> onHarvest(slot, slotIndex) },
                onWater = { slot -> onWater(slot) },
                onPot = { slot -> onPot(slot) },
                onCleanse = { slot, slotIndex -> onCleanse(slot, slotIndex) },
                onDismiss = { selectedMultiPlantTileId = null },
            )
        } else {
            selectedMultiPlantTileId = null
        }
    }
}

/** List/Map switch above the plants, styled like the sort chips next to it. */
@Composable
private fun ViewModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 10.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) Accent else TextSecondary,
        maxLines = 1,
        modifier = Modifier
            .clip(AppTheme.shapes.smallShape)
            .border(
                1.dp,
                if (selected) Accent.copy(alpha = 0.5f) else SurfaceBorder,
                AppTheme.shapes.smallShape,
            )
            .background(if (selected) Accent.copy(alpha = 0.18f) else SurfaceCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
