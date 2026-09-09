package com.mgafk.app.ui.screens.garden

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mgafk.app.data.model.GardenEggSnapshot
import com.mgafk.app.data.model.GardenPlantSnapshot
import com.mgafk.app.data.repository.GardenTiles
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.GardenTilePicker
import com.mgafk.app.ui.components.GardenTileState
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.components.VIEWPORT_HEIGHT
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary

/** What the player asked to plant by hand, while [ManualPlantTileDialog] is open. */
sealed interface ManualPlantTarget {
    /** A seed stack: one unit per tap, until the stack runs out. */
    data class Seed(val species: String) : ManualPlantTarget

    /** A single potted plant, which takes a whole tile and closes the popup once placed. */
    data class Pot(val itemId: String) : ManualPlantTarget
}

/**
 * Popup for planting by hand on a chosen tile, opened from the inventory when the placement
 * setting is [com.mgafk.app.data.model.PlantPlacementMode.GRID].
 *
 * This is not auto-plant: nothing is saved and nothing replants later. Each tap plants one unit
 * on the tile touched, the grid redraws from the session state, and the popup stays open so a
 * stack can be placed in one go. It closes on its own once [remaining] runs out.
 *
 * @param species the seed being planted, or null for a potted plant, which needs an empty tile.
 * @param remaining how many are left to plant, for the counter and the auto-close.
 */
@Composable
fun ManualPlantTileDialog(
    species: String?,
    displayName: String,
    spriteUrl: String?,
    remaining: Int,
    garden: List<GardenPlantSnapshot>,
    gardenEggs: List<GardenEggSnapshot>,
    /**
     * Sprite per tile holding decor, so those tiles read as taken. A map rather than the decor
     * snapshots themselves: not every build tracks garden decor, and the ones that do not pass
     * an empty map instead of carrying a type they have no use for.
     */
    decorSpriteByTile: Map<Int, String?>,
    onPlantTile: (tileId: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(remaining) {
        if (remaining <= 0) currentDismiss()
    }

    val maxSlots = remember(species) {
        species?.let { MgApi.findItem(it)?.plantMaxGrowSlots } ?: 1
    }
    val countByTile = remember(garden) { garden.groupingBy { it.tileId }.eachCount() }
    val speciesByTile = remember(garden) { garden.associate { it.tileId to it.species } }
    val eggSpriteByTile = remember(gardenEggs) {
        gardenEggs.associate { it.tileId to MgApi.findItem(it.eggId)?.sprite }
    }
    val plantableTiles = remember(garden, gardenEggs, decorSpriteByTile, species, maxSlots) {
        GardenTiles.plantableTiles(
            species = species,
            speciesByTile = speciesByTile,
            plantCountByTile = countByTile,
            blockedTileIds = eggSpriteByTile.keys + decorSpriteByTile.keys,
            maxGrowSlots = maxSlots,
        )
    }
    val stateByTile = remember(garden, gardenEggs, decorSpriteByTile, species) {
        (0 until GardenTiles.DIRT_TILES_PER_GARDEN).associateWith { tileId ->
            val tileSpecies = speciesByTile[tileId]
            when {
                tileId in eggSpriteByTile -> GardenTileState.Occupied(eggSpriteByTile[tileId])
                tileId in decorSpriteByTile -> GardenTileState.Occupied(decorSpriteByTile[tileId])
                tileSpecies != null -> GardenTileState.Occupied(
                    spriteUrl = MgApi.findItem(tileSpecies)?.cropSprite ?: MgApi.findItem(tileSpecies)?.sprite,
                    full = tileId !in plantableTiles,
                )
                else -> GardenTileState.Free
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpriteImage(url = spriteUrl, size = 40.dp, contentDescription = displayName)
            Spacer(modifier = Modifier.height(6.dp))
            Text(displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

            Spacer(modifier = Modifier.height(4.dp))
            Text("$remaining left", fontSize = 12.sp, color = TextMuted)

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                if (plantableTiles.isEmpty()) "No tile can take this right now."
                else "Tap a tile to plant. Drag to move, pinch to zoom.",
                fontSize = 11.sp,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.height(6.dp))

            GardenTilePicker(
                stateByTile = stateByTile,
                selectedTiles = emptySet(),
                isSelectable = { tileId -> tileId in plantableTiles },
                onTileClick = onPlantTile,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(VIEWPORT_HEIGHT)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark),
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text("Close", fontSize = 13.sp, color = TextMuted, modifier = Modifier.clickable { onDismiss() })
        }
    }
}
