package com.mgafk.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a thin thumb over the trailing edge of a [Modifier.verticalScroll] container,
 * sized/positioned from [state] - a persistent visual hint that the content scrolls, since a
 * flush list with no visible cutoff otherwise gives no indication more items are hidden below.
 */
fun Modifier.verticalScrollbar(
    state: ScrollState,
    width: Dp = 3.dp,
    color: Color = Color.White.copy(alpha = 0.35f),
    minThumbHeight: Dp = 24.dp,
): Modifier = drawWithContent {
    drawContent()
    if (state.maxValue <= 0) return@drawWithContent

    val viewportHeight = size.height
    val contentHeight = viewportHeight + state.maxValue
    val thumbHeight = (viewportHeight * viewportHeight / contentHeight)
        .coerceAtLeast(minThumbHeight.toPx())
        .coerceAtMost(viewportHeight)
    val scrollableTrack = viewportHeight - thumbHeight
    val thumbTop = if (state.maxValue == 0) 0f
        else (state.value.toFloat() / state.maxValue) * scrollableTrack

    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - width.toPx(), thumbTop),
        size = Size(width.toPx(), thumbHeight),
        cornerRadius = CornerRadius(width.toPx() / 2),
    )
}
