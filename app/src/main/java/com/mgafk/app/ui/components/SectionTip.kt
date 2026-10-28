package com.mgafk.app.ui.components

import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A dismissable hint shown once at the top of a screen. */
@Composable
fun SectionTip(visible: Boolean, text: String, onDismiss: () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(AppTheme.shapes.smallShape)
                .background(AppTheme.colors.accent.copy(alpha = 0.1f))
                .border(1.dp, AppTheme.colors.accent.copy(alpha = 0.25f), AppTheme.shapes.smallShape)
                .clickable { onDismiss() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text,
                    fontSize = 11.sp,
                    color = AppTheme.colors.accent,
                    lineHeight = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                Text("OK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.accent,
                    modifier = Modifier.clickable { onDismiss() })
            }
        }
    }
}
