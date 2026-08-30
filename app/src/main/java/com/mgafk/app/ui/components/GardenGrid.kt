package com.mgafk.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs
import kotlin.math.hypot
import androidx.compose.ui.unit.dp
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextPrimary

/**
 * Shared dirt-grid layout/zoom machinery for every view that draws the garden as a map: the
 * Plants card's map view and the tile-selector popups (Auto-Plant, Auto-Dawn-Capture, ...).
 *
 * 10 rows, two 10-wide blocks side by side (dirtTileIdx = row*20 + col, col 0-9 = left block,
 * col 10-19 = right block) - see GardenMap for how this was derived from the game's own map
 * atom. Rendered with one extra cosmetic boardwalk column between the blocks, matching how the
 * garden actually looks in-game.
 */
internal const val GRID_ROWS = 10
internal const val GRID_COLS = 20
internal const val BOARDWALK_VISUAL_COL = 10
internal val CELL_SIZE = 18.dp
internal val CELL_SPACING = 1.dp
internal val VIEWPORT_HEIGHT = 340.dp
private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 3.5f

/** Pinch-zoom + drag-pan viewport, plus explicit zoom/pan buttons for anyone who'd rather not
 * fight the gesture: the garden grid is bigger than a phone screen at a legible tile size, so
 * it's rendered at natural size inside this and navigated instead of shrunk. */
@Composable
internal fun ZoomableGardenGrid(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(Unit) {
                    detectPinchZoomAndPan { pan, zoom ->
                        scale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                        offset += pan
                    }
                },
        ) {
            // wrapContentSize(unbounded = true) lets the grid measure at its full natural size
            // (wider than the viewport) instead of being cropped down to the incoming bounded
            // constraints - graphicsLayer only transforms how an already-measured layout is
            // drawn, it can't un-crop content that was never given room to lay out in the
            // first place.
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .wrapContentSize(unbounded = true)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
                verticalArrangement = Arrangement.spacedBy(CELL_SPACING),
            ) {
                content()
            }
        }

        GridControls(
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
            onZoomIn = { scale = (scale * 1.25f).coerceIn(MIN_ZOOM, MAX_ZOOM) },
            onZoomOut = { scale = (scale / 1.25f).coerceIn(MIN_ZOOM, MAX_ZOOM) },
        )
    }
}

/**
 * Like [androidx.compose.foundation.gestures.detectTransformGestures], but watches pointer
 * events in [PointerEventPass.Initial] instead of the default `Main` pass. Each grid cell has
 * its own `clickable`, which claims its finger's move events in the `Main` pass before they'd
 * reach this parent - starving a two-finger pinch of the movement data it needs to register as
 * zoom (single-finger pan still worked, since only one clickable was ever fighting for it).
 * Watching `Initial` lets this see raw movement first; it only consumes once actual pan/zoom
 * motion clears touch slop, so a plain tap still reaches the cell underneath untouched.
 */
private suspend fun PointerInputScope.detectPinchZoomAndPan(onGesture: (pan: Offset, zoom: Float) -> Unit) {
    awaitEachGesture {
        var zoom = 1f
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val panMotion = hypot(panChange.x, panChange.y)
                    if (zoomMotion > touchSlop || panMotion > touchSlop) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    if (zoomChange != 1f || panChange != Offset.Zero) {
                        onGesture(panChange, zoomChange)
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}

@Composable
private fun GridControls(
    modifier: Modifier = Modifier,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceCard.copy(alpha = 0.85f))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ControlButton(Icons.Default.Add, "Zoom in", onZoomIn)
        ControlButton(Icons.Default.Remove, "Zoom out", onZoomOut)
    }
}

@Composable
private fun ControlButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(SurfaceDark)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = TextPrimary, modifier = Modifier.size(15.dp))
    }
}

@Composable
internal fun BoardwalkCell() {
    Box(
        modifier = Modifier
            .size(CELL_SIZE)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFF8B6F47).copy(alpha = 0.5f)),
    )
}
