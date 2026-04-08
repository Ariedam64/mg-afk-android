package com.mgafk.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.ui.theme.TextMuted

/**
 * Styled card container with optional title, trailing content, and collapsible support.
 *
 * When collapsed, the content stays composed (not removed from the tree) but is clipped
 * to zero height. This makes expand/collapse instant for heavy content (100+ items).
 *
 * @param collapsible If true, the card can be collapsed/expanded by tapping the header.
 * @param initiallyExpanded Initial state when collapsible is true (used for uncontrolled mode).
 * @param expanded Controlled expanded state. When non-null, overrides internal state.
 * @param onExpandedChange Called when the user toggles collapsed state (controlled mode).
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    collapsible: Boolean = false,
    initiallyExpanded: Boolean = true,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var internalExpanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val isExpanded = expanded ?: internalExpanded
    val toggle = {
        val newValue = !isExpanded
        if (expanded != null && onExpandedChange != null) {
            onExpandedChange(newValue)
        } else {
            internalExpanded = newValue
        }
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        animationSpec = tween(200),
        label = "chevron",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(150),
        label = "contentAlpha",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, SurfaceBorder),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (collapsible) Modifier.clickable { toggle() }
                            else Modifier
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    trailing?.invoke()
                    if (collapsible) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = TextMuted,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(chevronRotation),
                        )
                    }
                }
            }

            if (collapsible) {
                // Content stays composed but is clipped to 0 height when collapsed.
                // This avoids recomposing 100+ items on every expand.
                Column(
                    modifier = Modifier
                        .clipToBounds()
                        .then(
                            if (!isExpanded) Modifier.heightIn(max = 0.dp)
                            else Modifier
                        )
                        .alpha(contentAlpha),
                ) {
                    if (title != null) Spacer(modifier = Modifier.height(12.dp))
                    content()
                }
            } else {
                if (title != null) Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
    }
}
