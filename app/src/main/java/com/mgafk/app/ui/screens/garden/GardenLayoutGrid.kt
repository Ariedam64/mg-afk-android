package com.mgafk.app.ui.screens.garden

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.components.BOARDWALK_VISUAL_COL
import com.mgafk.app.ui.components.BoardwalkCell
import com.mgafk.app.ui.components.CELL_SIZE
import com.mgafk.app.ui.components.CELL_SPACING
import com.mgafk.app.ui.components.GRID_COLS
import com.mgafk.app.ui.components.GRID_ROWS
import com.mgafk.app.ui.components.VIEWPORT_HEIGHT
import com.mgafk.app.ui.components.ZoomableGardenGrid
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnecting
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceDark

/** Opacity applied to a plant that fails the active filters: dimmed rather than hidden, so the
 * garden keeps its shape and the matches still stand out. */
private const val FILTERED_OUT_ALPHA = 0.25f

/**
 * The Plants card's map view: the garden's real dirt layout instead of a list of tiles.
 *
 * Reuses the shared grid machinery the auto-plant and auto-dawn-capture tile pickers already run
 * on (see components/GardenGrid), so panning, pinch-zoom and the boardwalk column between the
 * two 10-wide blocks behave identically everywhere.
 *
 * Eggs and decor are drawn too, greyed and inert: a tile holding one is occupied, and leaving it
 * blank would read as free space. Only plants are clickable, opening the same detail dialogs the
 * list view uses.
 */
@Composable
internal fun GardenLayoutGrid(
    entriesByTile: Map<Int, GardenEntry>,
    matchingTileIds: Set<Int>,
    filtersActive: Boolean,
    eggSpriteByTile: Map<Int, String?>,
    decorSpriteByTile: Map<Int, String?>,
    onSelectPlant: (GardenEntry) -> Unit,
) {
    ZoomableGardenGrid(
        modifier = Modifier
            .fillMaxWidth()
            .height(VIEWPORT_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceDark),
    ) {
        for (row in 0 until GRID_ROWS) {
            Row(horizontalArrangement = Arrangement.spacedBy(CELL_SPACING)) {
                for (visualCol in 0 until GRID_COLS + 1) {
                    if (visualCol == BOARDWALK_VISUAL_COL) {
                        BoardwalkCell()
                        continue
                    }
                    val col = if (visualCol < BOARDWALK_VISUAL_COL) visualCol else visualCol - 1
                    val tileId = row * GRID_COLS + col
                    val entry = entriesByTile[tileId]
                    GardenGridCell(
                        entry = entry,
                        dimmed = entry != null && filtersActive && tileId !in matchingTileIds,
                        occupantSprite = eggSpriteByTile[tileId] ?: decorSpriteByTile[tileId],
                        onClick = { entry?.let(onSelectPlant) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GardenGridCell(
    entry: GardenEntry?,
    dimmed: Boolean,
    occupantSprite: String?,
    onClick: () -> Unit,
) {
    val plantSprite = when (entry) {
        is GardenEntry.SingleCrop -> entry.plant.cropSprite
        is GardenEntry.MultiSlotPlant -> entry.cropSprite
        null -> null
    }
    val bgColor = when {
        entry != null -> Accent.copy(alpha = if (dimmed) 0.08f else 0.22f)
        occupantSprite != null -> StatusConnecting.copy(alpha = 0.25f)
        else -> SurfaceDark
    }
    val borderColor = when {
        entry != null -> Accent.copy(alpha = if (dimmed) 0.25f else 0.7f)
        occupantSprite != null -> StatusConnecting.copy(alpha = 0.5f)
        else -> SurfaceBorder
    }

    Box(
        modifier = Modifier
            .size(CELL_SIZE)
            .clip(RoundedCornerShape(3.dp))
            .background(bgColor)
            .border(0.75.dp, borderColor, RoundedCornerShape(3.dp))
            .then(if (entry != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val sprite = plantSprite ?: occupantSprite
        if (sprite != null) {
            SpriteImage(
                url = sprite,
                size = CELL_SIZE - 4.dp,
                contentDescription = entry?.displayName,
                modifier = Modifier.alpha(if (dimmed) FILTERED_OUT_ALPHA else 1f),
            )
        }
    }
}
