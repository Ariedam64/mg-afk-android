package com.mgafk.app.ui.screens.customize

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.Appearance
import com.mgafk.app.data.model.CornerStyle
import com.mgafk.app.data.model.DashboardCard
import com.mgafk.app.data.model.LayoutDensity
import com.mgafk.app.data.model.TextSize
import com.mgafk.app.data.model.ThemeMode
import com.mgafk.app.ui.components.CardStack
import com.mgafk.app.ui.components.PillButton
import com.mgafk.app.ui.components.PillStyle
import com.mgafk.app.ui.components.SegmentedPills
import com.mgafk.app.ui.components.TonalCard
import com.mgafk.app.ui.theme.AppTheme

/**
 * The Customize screen. Every change applies to the whole app at once, this screen
 * included, so it doubles as its own preview.
 */
@Composable
fun CustomizeScreen(
    appearance: Appearance,
    onChange: ((Appearance) -> Appearance) -> Unit,
) {
    CardStack {
        Group("Accent color", "Every surface is tinted from it.") {
            AccentPicker(
                selected = appearance.accentArgb,
                onPick = { argb -> onChange { it.copy(accentArgb = argb) } },
            )
        }

        Group("Theme") {
            EnumPills(ThemeMode.entries, appearance.themeMode, { it.name.lowercase().replaceFirstChar(Char::titlecase) }) { mode ->
                onChange { it.copy(themeMode = mode) }
            }
        }

        Group("Corners") {
            EnumPills(CornerStyle.entries, appearance.corners, { it.name.lowercase().replaceFirstChar(Char::titlecase) }) { corners ->
                onChange { it.copy(corners = corners) }
            }
        }

        Group("Spacing") {
            EnumPills(LayoutDensity.entries, appearance.density, { it.name.lowercase().replaceFirstChar(Char::titlecase) }) { density ->
                onChange { it.copy(density = density) }
            }
        }

        Group("Text size") {
            EnumPills(TextSize.entries, appearance.textSize, { it.name.lowercase().replaceFirstChar(Char::titlecase) }) { size ->
                onChange { it.copy(textSize = size) }
            }
        }

        Group("Home cards", "Hide what you do not need on Home.") {
            DashboardCard.entries.forEach { card ->
                val shown = card !in appearance.hiddenDashboardCards
                ToggleRow(card.label, shown) { show ->
                    onChange {
                        it.copy(
                            hiddenDashboardCards = if (show) it.hiddenDashboardCards - card
                            else it.hiddenDashboardCards + card,
                        )
                    }
                }
            }
        }

        PillButton(
            text = "Reset to default",
            style = PillStyle.OUTLINE,
            onClick = { onChange { Appearance() } },
            modifier = Modifier.fillMaxWidth(),
            height = 48.dp,
        )
    }
}

@Composable
private fun Group(title: String, hint: String? = null, content: @Composable () -> Unit) {
    TonalCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        if (hint != null) Text(hint, fontSize = 13.sp, color = AppTheme.colors.textMuted)
        content()
    }
}

@Composable
private fun <T> EnumPills(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SegmentedPills(
        options = options.map(label),
        selectedIndex = options.indexOf(selected),
        onSelect = { onSelect(options[it]) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(selected: Int, onPick: (Int) -> Unit) {
    val colors = AppTheme.colors
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Appearance.ACCENT_PRESETS.forEach { argb ->
            val isSelected = argb == selected
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (isSelected) colors.textPrimary else Color.Transparent, CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .semantics {
                        this.selected = isSelected
                        contentDescription = "Accent color"
                    }
                    .clickable(role = Role.RadioButton) { onPick(argb) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(20.dp))
            }
        }
    }

    // Any hue, at the saturation and brightness that suit both themes.
    val hue = hueOf(selected)
    Text("Custom hue", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
    Slider(
        value = hue,
        onValueChange = { onPick(android.graphics.Color.HSVToColor(floatArrayOf(it, 0.58f, 0.96f))) },
        valueRange = 0f..359f,
        colors = SliderDefaults.colors(
            thumbColor = colors.accent,
            activeTrackColor = colors.accent,
            inactiveTrackColor = colors.surfaceHigh,
        ),
    )
}

private fun hueOf(argb: Int): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    return hsv[0]
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 15.sp)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.accent,
                checkedThumbColor = colors.onAccent,
                uncheckedTrackColor = colors.surfaceHigh,
                uncheckedBorderColor = colors.outline,
                uncheckedThumbColor = colors.textMuted,
            ),
        )
    }
}
