package com.mgafk.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mgafk.app.data.repository.MgApi

/** Game tile size in world pixels — slot offsets are in tile units, see sprite-utils. */
private const val TILE_SIZE_WORLD_PX = 256.0

/**
 * A single slot for [PlantCompositeSprite]: the crop species, its mutations, and the
 * grown `scale` value from the game state (multiplier on the crop's base sprite size,
 * typically in `[0, maxScale]` where 1.0 means "just mature").
 */
data class PlantSlotRender(
    val species: String,
    val mutations: List<String>,
    val scale: Double = 1.0,
)

/**
 * Renders a multi-slot plant in the same style as the in-game tile.
 *
 * Rendering math (replicating `PlantBodyVisual` + `GrowingCropVisual` from the game):
 * 1. Plant canvas is drawn at its texture center. We use Compose `Alignment.Center`.
 * 2. Each slot has a tile-unit offset `(x, y)`; the crop is placed at
 *    `slotOffset * TILE_SIZE_WORLD_PX` pixels from the plant texture center.
 * 3. The crop sprite also has its own anchor (e.g. `MoonCelestialCrop` anchors at
 *    (0.5, 0.88) — near the bottom). The game places the *anchor point* at the slot
 *    position, not the crop center. In Compose we center the crop on its offset, so
 *    we need to shift by `(0.5 - cropAnchor) * cropSize` to get the anchor at the
 *    slot position.
 *
 * Falls back to the plain crop sprite if the plant has no slot offsets
 * (single-harvest plants).
 */
@Composable
fun PlantCompositeSprite(
    species: String,
    slots: List<PlantSlotRender>,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val entry = remember(species) { MgApi.findItem(species) }
    val hasComposite = entry?.plantSprite != null && entry.plantSlotOffsets.isNotEmpty()

    if (!hasComposite) {
        SpriteImage(
            url = entry?.cropSprite,
            size = size,
            modifier = modifier,
            contentDescription = contentDescription,
        )
        return
    }

    val plantBaseTileScale = (entry.plantBaseTileScale ?: 1.0).coerceAtLeast(0.1)
    val plantSpriteName = spriteNameFromUrl(entry.plantSprite)
    val plantMeta = plantSpriteName?.let { MgApi.getPlantSpriteMetadata(it) }
    val cropSpriteName = spriteNameFromUrl(entry.cropSprite)
    val cropMeta = cropSpriteName?.let { MgApi.getPlantSpriteMetadata(it) }

    // Fit preserves aspect: 1 game pixel = size / max(sourceW, sourceH) dp.
    val longestSidePx = plantMeta?.let { maxOf(it.sourceWidth, it.sourceHeight).toDouble() }
        ?: (plantBaseTileScale * TILE_SIZE_WORLD_PX)
    val dpPerGamePx = size.value / longestSidePx
    val offsetDpPerTile = TILE_SIZE_WORLD_PX * dpPerGamePx
    // Crop size at targetScale=1 (minimum mature size), relative to the plant sprite.
    val cropBaseRelScale = (entry.cropBaseTileScale ?: 0.4) / plantBaseTileScale
    // Crop anchor within its own texture (fallback: center). Used to shift the crop
    // so its anchor lands at the slot position instead of its center.
    val cropAnchorY = cropMeta?.anchorY ?: 0.5
    val cropAnchorX = cropMeta?.anchorX ?: 0.5

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        SpriteImage(
            url = entry.plantSprite,
            size = size,
            contentDescription = contentDescription,
        )
        slots.forEachIndexed { index, slot ->
            val off = entry.plantSlotOffsets.getOrNull(index) ?: return@forEachIndexed
            // targetScale goes from 1.0 (just mature, minimum) up to maxScale.
            // The rendered crop size is directly proportional to it.
            val cropSize = size * (cropBaseRelScale * slot.scale).toFloat()
            val cropAnchorShiftX = ((0.5 - cropAnchorX) * cropSize.value).toFloat()
            val cropAnchorShiftY = ((0.5 - cropAnchorY) * cropSize.value).toFloat()
            val dx = (off.x * offsetDpPerTile + cropAnchorShiftX).dp
            val dy = (off.y * offsetDpPerTile + cropAnchorShiftY).dp
            SpriteImage(
                category = "plants",
                name = slot.species,
                mutations = slot.mutations,
                size = cropSize,
                contentDescription = null,
                modifier = Modifier
                    .offset(dx, dy)
                    .rotate(off.rotation.toFloat()),
            )
        }
    }
}

private fun spriteNameFromUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val file = url.substringAfterLast('/').substringBefore('?')
    return file.removeSuffix(".png").ifBlank { null }
}
