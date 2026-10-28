package com.mgafk.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.ui.theme.AppTheme

// The building blocks of the redesign. Screens compose these instead of styling raw
// Boxes, so corners, spacing and colors all follow the user's Customize choices.

/** A card: tonal surface, no border, corners from the theme. */
@Composable
fun TonalCard(
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.surface,
    contentPadding: PaddingValues = PaddingValues(AppTheme.spacing.card),
    onClick: (() -> Unit)? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(AppTheme.spacing.item),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.largeShape)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

enum class PillStyle { FILLED, TONAL, OUTLINE }

/** Full-height pill button. The main action of a screen is a FILLED one. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.FILLED,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    height: Dp = 52.dp,
) {
    val colors = AppTheme.colors
    val (container, content) = when {
        // A faded accent reads as "almost pressable"; a plain raised surface reads as off.
        !enabled -> (if (style == PillStyle.OUTLINE) Color.Transparent else colors.surfaceHigh) to colors.textMuted
        style == PillStyle.FILLED -> colors.accent to colors.onAccent
        style == PillStyle.TONAL -> colors.accentContainer to colors.onAccentContainer
        else -> Color.Transparent to colors.textPrimary
    }
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .heightIn(min = height)
            .clip(shape)
            .background(container)
            .then(if (style == PillStyle.OUTLINE) Modifier.border(1.dp, colors.outline, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = content
        if (leadingIcon != null) Icon(leadingIcon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text, color = tint, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (trailingIcon != null) Icon(trailingIcon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Small rounded label with a colored dot, e.g. "Online". */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Section title above a group of cards, with an optional text action on the right. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = AppTheme.colors.textPrimary,
        )
        if (action != null && onAction != null) {
            Text(
                action,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/** Large screen title, with a back arrow on screens reached from More. */
@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = AppTheme.colors.textPrimary,
                )
            }
        }
        Text(
            title,
            modifier = Modifier.weight(1f).padding(start = if (onBack == null) 0.dp else 4.dp),
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            color = AppTheme.colors.textPrimary,
        )
        trailing()
    }
}

/** Pill-shaped segmented control. */
@Composable
fun SegmentedPills(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) colors.accent else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (selected) colors.onAccent else colors.textSecondary,
                )
            }
        }
    }
}

/**
 * Circular progress ring, the Home screen's way of showing pet hunger.
 * [fraction] is clamped to 0..1.
 */
@Composable
fun ProgressRing(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    stroke: Dp = 5.dp,
    trackColor: Color = AppTheme.colors.surfaceHigh,
    content: @Composable () -> Unit = {},
) {
    val sweep = 360f * fraction.coerceIn(0f, 1f)
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(strokePx))
            drawArc(color, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Good above half, warning above a quarter, bad below. Used for hunger everywhere. */
@Composable
@ReadOnlyComposable
fun levelColor(percent: Float): Color = when {
    percent > 50f -> AppTheme.colors.good
    percent > 25f -> AppTheme.colors.warn
    else -> AppTheme.colors.bad
}

/** A stack of cards with the theme's gap between them. */
@Composable
fun CardStack(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.gap),
        content = content,
    )
}
