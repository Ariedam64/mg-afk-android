package com.mgafk.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Narrowest a tile may get before the grid drops a column. */
internal val TILE_MIN_WIDTH = 76.dp

/** Gap between tiles, both across and down. */
internal val TILE_SPACING = 6.dp

/** Adaptive grid of [count] equal-width tiles, as many per row as [TILE_MIN_WIDTH] allows. */
@Composable
internal fun GridOf(count: Int, content: @Composable (Int) -> Unit) {
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
