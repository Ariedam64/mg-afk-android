package com.mgafk.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary

/**
 * Search bar + collapsible filter panel, shared by any grid/list screen that needs
 * name search plus a handful of togglable filters (rarity, mutations, sort, ...).
 * The filter panel starts collapsed - [activeFilterCount] surfaces as a badge on the
 * toggle button so an active filter is never invisible just because the panel is shut.
 */
@Composable
fun SearchFilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    activeFilterCount: Int,
    filtersExpanded: Boolean,
    onFiltersExpandedChange: (Boolean) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
    filterContent: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = placeholder,
                modifier = Modifier.weight(1f),
            )
            FilterToggleButton(
                expanded = filtersExpanded,
                activeCount = activeFilterCount,
                onClick = { onFiltersExpandedChange(!filtersExpanded) },
            )
        }

        Column(modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(200))) {
            if (filtersExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceDark.copy(alpha = 0.5f))
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filterContent()
                    if (activeFilterCount > 0) {
                        Text(
                            text = "Clear all filters",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Accent,
                            modifier = Modifier.clickable { onClearFilters() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(17.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(text = placeholder, fontSize = 13.sp, color = TextMuted)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = TextPrimary),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear search",
                tint = TextMuted,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onQueryChange("") },
            )
        }
    }
}

@Composable
private fun FilterToggleButton(
    expanded: Boolean,
    activeCount: Int,
    onClick: () -> Unit,
) {
    val highlighted = expanded || activeCount > 0
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "filterChevron",
    )
    val shape = RoundedCornerShape(10.dp)

    // Badge is a sibling of the clipped button surface (not a child of it) so it can sit
    // outside the button's rounded-rect bounds without being cut off by that clip.
    Box {
        Box(
            modifier = Modifier
                .height(40.dp)
                .clip(shape)
                .background(if (highlighted) Accent.copy(alpha = 0.12f) else SurfaceDark)
                .border(1.dp, if (highlighted) Accent.copy(alpha = 0.4f) else SurfaceBorder, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Filters",
                    tint = if (highlighted) Accent else TextMuted,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Filters",
                    fontSize = 12.sp,
                    fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (highlighted) Accent else TextMuted,
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = if (highlighted) Accent else TextMuted,
                    modifier = Modifier.size(16.dp).rotate(chevronRotation),
                )
            }
        }

        if (activeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$activeCount",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

/** Toggle chip for multi-select filters (e.g. mutations) - unlike a radio-style filter,
 * multiple chips can be active at once and combine with AND semantics. */
@Composable
fun MultiSelectChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(shape)
            .border(1.5.dp, if (selected) Accent else SurfaceBorder, shape)
            .background(if (selected) Accent.copy(alpha = 0.18f) else SurfaceDark)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
