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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.ui.theme.AppTheme

/** Height of the bar plus its bottom margin; screens pad their content by this much. */
val FloatingNavBarClearance = 96.dp

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
            .padding(horizontal = 16.dp)
            .shadow(16.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(colors.surfaceHigh)
            .height(64.dp)
            .padding(horizontal = 8.dp),
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
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .semantics { this.selected = selected }
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(horizontal = if (selected) 16.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            destination.icon,
            contentDescription = if (selected) null else destination.label,
            tint = content,
            modifier = Modifier.size(22.dp),
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Text(destination.label, color = content, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
