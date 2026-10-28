package com.mgafk.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mgafk.app.data.repository.MgApi

/** Small lock badge in the top-right corner of a tile when [isLocked]. */
@Composable
internal fun LockOverlay(isLocked: Boolean, content: @Composable () -> Unit) {
    Box {
        content()
        if (isLocked) {
            SpriteImage(
                url = MgApi.lockSpriteUrl,
                size = 12.dp,
                contentDescription = "Locked",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp),
            )
        }
    }
}

/** Lock toggle icon for the top-right of item and pet detail dialogs. */
@Composable
internal fun LockToggleIcon(isLocked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SpriteImage(
        url = if (isLocked) MgApi.lockSpriteUrl else MgApi.unlockSpriteUrl,
        size = 28.dp,
        contentDescription = if (isLocked) "Locked" else "Unlocked",
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(2.dp),
    )
}
