package com.mgafk.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.ui.theme.AppTheme

/** Height of the bar plus its bottom margin; screens pad their content by this much. */
val FloatingNavBarClearance = 96.dp

/** What a tap on the floating bar lands on. */
internal sealed interface NavBarTouch {
    /** The tab at [index] in [Destination.bottomBar] takes the tap. */
    data class Tab(val index: Int) : NavBarTouch

    /** The pill takes the tap without any tab reacting. */
    data object Pill : NavBarTouch

    /** The tap goes through to the screen drawn behind the bar. */
    data object Behind : NavBarTouch
}

/**
 * The geometry of [FloatingNavBar], in dp, and where a tap on it ends up. The composable is
 * built from these values, so the hit test below follows what is on screen.
 */
internal object FloatingNavBarLayout {
    /** Gap between the screen edges and the pill. */
    const val SIDE_MARGIN = 16f
    const val HEIGHT = 64f
    /** Padding inside the pill, before the first tab and after the last. */
    const val INNER_PADDING = 8f
    const val ITEM_MIN_HEIGHT = 48f
    const val ICON_SIZE = 22f
    const val ITEM_PADDING = 12f
    const val SELECTED_ITEM_PADDING = 16f
    /** Space between the icon and the label of the selected tab. */
    const val LABEL_GAP = 8f

    /** Width of a tab: icon and padding, plus the label when it is the selected one. */
    fun itemWidth(selected: Boolean, labelWidth: Float = 0f): Float =
        if (selected) ICON_SIZE + 2 * SELECTED_ITEM_PADDING + LABEL_GAP + labelWidth
        else ICON_SIZE + 2 * ITEM_PADDING

    /**
     * Where a tap at ([x], [y]) lands. [x] runs from the left edge of the screen, [y] from the
     * top of the pill; [itemWidths] are the tabs' widths in order.
     *
     * Tabs are laid out with [Arrangement.SpaceBetween] and centered vertically. The pill is a
     * touch target of its own, so a tap no tab claims stops on it instead of reaching the
     * screen behind.
     */
    fun touchAt(x: Float, y: Float, screenWidth: Float, itemWidths: List<Float>): NavBarTouch {
        val pillLeft = SIDE_MARGIN
        val pillRight = screenWidth - SIDE_MARGIN
        if (x < pillLeft || x > pillRight || y < 0f || y > HEIGHT) return NavBarTouch.Behind

        val contentLeft = pillLeft + INNER_PADDING
        val contentWidth = (pillRight - INNER_PADDING) - contentLeft
        val gap = if (itemWidths.size > 1) {
            ((contentWidth - itemWidths.sum()) / (itemWidths.size - 1)).coerceAtLeast(0f)
        } else {
            0f
        }
        val itemHeight = maxOf(ITEM_MIN_HEIGHT, ICON_SIZE)
        val itemTop = (HEIGHT - itemHeight) / 2
        val itemBottom = itemTop + itemHeight

        var left = contentLeft
        itemWidths.forEachIndexed { index, width ->
            if (x >= left && x <= left + width && y >= itemTop && y <= itemBottom) {
                return NavBarTouch.Tab(index)
            }
            left += width + gap
        }
        return NavBarTouch.Pill
    }
}

/**
 * The floating pill at the bottom of the screen. The selected item expands into an accent
 * pill with its label; the others are icon-only. Items that need a connection are dimmed and
 * inert while offline.
 */
@Composable
fun FloatingNavBar(
    selected: Destination,
    connected: Boolean,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FloatingNavBarLayout.SIDE_MARGIN.dp)
            // Makes the whole pill a hit target: a tap between or around the tabs stops here
            // instead of falling through to the screen scrolling behind the bar. Nothing is
            // consumed, so the tabs' own clicks behave as before.
            .pointerInput(Unit) {}
            .shadow(16.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(colors.surfaceHigh)
            .height(FloatingNavBarLayout.HEIGHT.dp)
            .padding(horizontal = FloatingNavBarLayout.INNER_PADDING.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A screen reached from More keeps More highlighted.
        val highlighted = selected.parent ?: selected
        Destination.bottomBar.forEach { destination ->
            val enabled = connected || !destination.requiresConnection
            NavItem(
                destination = destination,
                selected = destination == highlighted,
                enabled = enabled,
                onClick = { onSelect(destination) },
            )
        }
    }
}

@Composable
private fun NavItem(
    destination: Destination,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val background by animateColorAsState(if (selected) colors.accent else Color.Transparent, label = "navBg")
    val content = when {
        selected -> colors.onAccent
        enabled -> colors.textSecondary
        else -> colors.textMuted.copy(alpha = 0.45f)
    }
    Row(
        modifier = Modifier
            .heightIn(min = FloatingNavBarLayout.ITEM_MIN_HEIGHT.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .semantics { this.selected = selected }
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(
                horizontal = if (selected) {
                    FloatingNavBarLayout.SELECTED_ITEM_PADDING.dp
                } else {
                    FloatingNavBarLayout.ITEM_PADDING.dp
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            destination.icon,
            contentDescription = if (selected) null else destination.label,
            tint = content,
            modifier = Modifier.size(FloatingNavBarLayout.ICON_SIZE.dp),
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Row {
                Spacer(Modifier.width(FloatingNavBarLayout.LABEL_GAP.dp))
                Text(destination.label, color = content, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
