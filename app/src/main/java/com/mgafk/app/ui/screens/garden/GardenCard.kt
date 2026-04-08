package com.mgafk.app.ui.screens.garden

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.GardenPlantSnapshot
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary

// Game-authentic rarity colors
private val RarityCommon = Color(0xFFE7E7E7)
private val RarityUncommon = Color(0xFF67BD4D)
private val RarityRare = Color(0xFF0071C6)
private val RarityLegendary = Color(0xFFFFC734)
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

private const val MUTATION_SPRITE_BASE = "https://mg-api.ariedam.fr/assets/sprites/ui/Mutation"

private val MUTATION_SPRITE_NAME = mapOf(
    "Ambershine" to "Amberlit",
)

private fun mutationSpriteUrl(mutation: String): String {
    val spriteName = MUTATION_SPRITE_NAME[mutation] ?: mutation
    return "$MUTATION_SPRITE_BASE$spriteName.png"
}

private val TILE_MIN_WIDTH = 76.dp
private val TILE_SPACING = 6.dp

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
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GardenCard(plants: List<GardenPlantSnapshot>, apiReady: Boolean = false) {
    var selectedRarity by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMutation by rememberSaveable { mutableStateOf<String?>(null) }

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
            )
        }
    }

    val allMutations = remember(plants) {
        plants.flatMap { it.mutations }.distinct().sorted()
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

    // Fast filter — no API calls, just string comparisons on pre-resolved data
    val filtered = remember(resolved, safeRarity, safeMutation) {
        if (safeRarity == null && safeMutation == null) resolved
        else resolved.filter { rp ->
            (safeRarity == null || rp.rarity == safeRarity) &&
                (safeMutation == null || safeMutation in rp.snapshot.mutations)
        }
    }

    AppCard(
        title = "Plants",
        trailing = {
            Text(
                text = if (safeRarity != null || safeMutation != null)
                    "${filtered.size}/${plants.size}" else "${plants.size} plants",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Accent.copy(alpha = 0.7f),
            )
        },
        collapsible = true,
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

            // ── Grid ──
            if (filtered.isEmpty()) {
                Text("No plants match filters.", fontSize = 12.sp, color = TextMuted)
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val columns = ((maxWidth + TILE_SPACING) / (TILE_MIN_WIDTH + TILE_SPACING))
                        .toInt().coerceAtLeast(1)
                    val rows = filtered.chunked(columns)

                    Column(verticalArrangement = Arrangement.spacedBy(TILE_SPACING)) {
                        rows.forEach { rowPlants ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
                            ) {
                                rowPlants.forEach { rp ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        GardenPlantTile(rp)
                                    }
                                }
                                repeat(columns - rowPlants.size) {
                                    Box(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Plant tile ──

@Composable
private fun GardenPlantTile(rp: ResolvedPlant) {
    val color = rarityColor(rp.rarity)
    val sizePercent = computeSizePercent(rp.snapshot.targetScale, rp.maxScale)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        SpriteImage(url = rp.cropSprite, size = 28.dp, contentDescription = rp.displayName)

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

        if (rp.snapshot.mutations.isNotEmpty()) {
            MutationIcons(mutations = rp.snapshot.mutations)
        }
    }
}

// ── Size bar ──

@Composable
private fun SizeBar(percent: Double, color: Color) {
    val fraction = (percent / 100.0).toFloat().coerceIn(0f, 1f)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
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
        Text(
            text = "${percent.toInt()}%",
            fontSize = 7.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
            lineHeight = 8.sp,
        )
    }
}

// ── Mutation icons ──

@Composable
private fun MutationIcons(mutations: List<String>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        mutations.take(4).forEach { mutation ->
            SpriteImage(
                url = mutationSpriteUrl(mutation),
                size = 12.dp,
                contentDescription = mutation,
            )
        }
    }
}
