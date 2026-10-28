package com.mgafk.app.ui.screens.garden

import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.background
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
import com.mgafk.app.data.repository.MgApi
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

// ── Single crop tile (unchanged) ──

@Composable
internal fun GardenPlantTile(rp: ResolvedPlant) {
    val color = rarityColor(rp.rarity)
    val sizePercent = rp.snapshot.size.toDouble()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clip(AppTheme.shapes.smallShape)
            .rarityBorder(rarity = rp.rarity, width = 1.5.dp, shape = AppTheme.shapes.smallShape, alpha = 0.5f)
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

        if (rp.snapshot.mutations.isNotEmpty() || rp.snapshot.preserved) {
            MutationIcons(mutations = rp.snapshot.mutations, preserved = rp.snapshot.preserved)
        }
    }
}

// ── Multi-slot plant tile (like inventory PlantTile) ──

@Composable
internal fun MultiSlotPlantTile(entry: GardenEntry.MultiSlotPlant) {
    val color = rarityColor(entry.rarity)
    // Keyed on the crops, not the tile: a tile keeps its id across a harvest and
    // replant, so remembering the base per tile drew every later plant on the first
    // species ever grown there - a garden of trees all sitting on a cacao base.
    val species = entry.crops.firstOrNull()?.snapshot?.species ?: ""
    val slots = remember(entry.crops) {
        entry.crops.map { PlantSlotRender(it.snapshot.species, it.snapshot.mutations, MgApi.cropSizeMultiplier(it.snapshot.species, it.snapshot.size)) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (entry.totalValue > 0) 0.85f else 1f)
            .clip(AppTheme.shapes.smallShape)
            .rarityBorder(rarity = entry.rarity, width = 1.5.dp, shape = AppTheme.shapes.smallShape, alpha = 0.5f)
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
internal fun SizeBar(percent: Double, color: Color, showLabel: Boolean = true) {
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
private fun MutationIcons(mutations: List<String>, preserved: Boolean = false) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // First, and outside the four-mutation cap: preservation is what decides whether those
        // mutations can still change, so it is the one badge that must never be crowded out.
        if (preserved) {
            SpriteImage(
                url = MgApi.preservationSpriteUrl,
                size = 12.dp,
                contentDescription = "Preserved",
            )
        }
        sortMutations(mutations).take(4).forEach { mutation ->
            SpriteImage(
                url = mutationSpriteUrl(mutation),
                size = 12.dp,
                contentDescription = mutation,
            )
        }
    }
}
