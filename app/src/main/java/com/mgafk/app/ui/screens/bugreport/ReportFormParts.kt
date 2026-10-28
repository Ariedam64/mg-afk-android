package com.mgafk.app.ui.screens.bugreport

import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SectionLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppTheme.colors.textPrimary)
}

/** A selectable chip, styled like the mode pickers of the Settings section. */
@Composable
internal fun OptionChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(AppTheme.shapes.smallShape)
            .background(if (selected) AppTheme.colors.accent.copy(alpha = 0.12f) else AppTheme.colors.surfaceHigh.copy(alpha = 0.2f))
            .border(
                1.dp,
                if (selected) AppTheme.colors.accent.copy(alpha = 0.5f) else AppTheme.colors.surfaceHigh.copy(alpha = 0.4f),
                AppTheme.shapes.smallShape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) AppTheme.colors.accent else AppTheme.colors.textSecondary,
        )
    }
}

@Composable
internal fun reportFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppTheme.colors.accent,
    unfocusedBorderColor = AppTheme.colors.surfaceHigh,
    cursorColor = AppTheme.colors.accent,
    focusedTextColor = AppTheme.colors.textPrimary,
    unfocusedTextColor = AppTheme.colors.textPrimary,
    focusedLabelColor = AppTheme.colors.accent,
    unfocusedLabelColor = AppTheme.colors.textMuted,
)

/** A form field with a character counter against the backend's limit. */
@Composable
internal fun ReportTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    max: Int,
    enabled: Boolean,
    isError: Boolean,
    placeholder: String = "",
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    val tooLong = value.trim().length > max
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = {
            if (placeholder.isNotEmpty()) Text(placeholder, fontSize = 12.sp, color = AppTheme.colors.textMuted)
        },
        supportingText = {
            Text(
                text = "${value.trim().length} / $max",
                fontSize = 10.sp,
                color = if (tooLong) AppTheme.colors.bad else AppTheme.colors.textMuted,
            )
        },
        isError = isError || tooLong,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
        modifier = Modifier.fillMaxWidth(),
        shape = AppTheme.shapes.smallShape,
        colors = reportFieldColors(),
    )
}

@Composable
internal fun ScreenshotThumb(shot: PickedScreenshot, removable: Boolean, onRemove: () -> Unit) {
    val image = remember(shot.id) { shot.encoded.thumbnail.asImageBitmap() }
    Box(modifier = Modifier.size(THUMB_SIZE)) {
        Image(
            bitmap = image,
            contentDescription = "Screenshot",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(AppTheme.shapes.smallShape)
                .border(1.dp, AppTheme.colors.surfaceHigh, AppTheme.shapes.smallShape),
        )
        if (removable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(AppTheme.colors.background.copy(alpha = 0.75f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Remove screenshot",
                    tint = AppTheme.colors.textPrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
internal fun ProcessingThumb() {
    Box(
        modifier = Modifier
            .size(THUMB_SIZE)
            .clip(AppTheme.shapes.smallShape)
            .background(AppTheme.colors.surfaceHigh.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AppTheme.colors.accent, strokeWidth = 2.dp)
    }
}

@Composable
internal fun AddScreenshotTile(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(THUMB_SIZE)
            .clip(AppTheme.shapes.smallShape)
            .background(AppTheme.colors.surfaceHigh.copy(alpha = 0.2f))
            .border(1.dp, AppTheme.colors.surfaceHigh.copy(alpha = 0.6f), AppTheme.shapes.smallShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.AddPhotoAlternate,
                contentDescription = "Add a screenshot",
                tint = if (enabled) AppTheme.colors.accent else AppTheme.colors.textMuted,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text("Add", fontSize = 10.sp, color = AppTheme.colors.textSecondary)
        }
    }
}

@Composable
internal fun AccountStateRow(
    checked: Boolean,
    enabled: Boolean,
    sessionConnected: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.smallShape)
            .background(AppTheme.colors.surfaceHigh.copy(alpha = 0.2f))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(end = 12.dp, top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = AppTheme.colors.accent, uncheckedColor = AppTheme.colors.textMuted),
        )
        Column(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
            Text("Attach my account state", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppTheme.colors.textPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "A copy of what the app already syncs for your account: garden, inventory, stats, " +
                    "journal and recent activity log. Your login token is never part of it.",
                fontSize = 10.sp,
                color = AppTheme.colors.textMuted,
                lineHeight = 13.sp,
            )
            if (!sessionConnected) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "No session is connected: the account can only be attached while one is.",
                    fontSize = 10.sp,
                    color = AppTheme.colors.warn,
                    lineHeight = 13.sp,
                )
            }
        }
    }
}

@Composable
internal fun SentBanner(ref: String, note: String?, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.smallShape)
            .background(AppTheme.colors.good.copy(alpha = 0.1f))
            .border(1.dp, AppTheme.colors.good.copy(alpha = 0.3f), AppTheme.shapes.smallShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Report sent: $ref", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.good)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Thanks! Follow its status and answer questions under My reports.",
                fontSize = 11.sp,
                color = AppTheme.colors.textSecondary,
                lineHeight = 15.sp,
            )
            if (note != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(note, fontSize = 11.sp, color = AppTheme.colors.warn, lineHeight = 15.sp)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "OK",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.good,
            modifier = Modifier.clickable { onDismiss() },
        )
    }
}
