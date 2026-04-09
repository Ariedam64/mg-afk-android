package com.mgafk.app.ui.screens.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mgafk.app.data.model.PlayerSnapshot
import com.mgafk.app.ui.theme.SurfaceBorder

/**
 * Composites the 4 cosmetic layers (Bottom, Mid, Top, Expression) into a circular avatar.
 * Zooms in ~1.8x and shifts upward to frame the head area.
 */
@Composable
fun PlayerAvatar(
    player: PlayerSnapshot,
    gameVersion: String,
    gameHost: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val host = gameHost.removePrefix("https://").removePrefix("http://").ifBlank { "magicgarden.gg" }
    val baseUrl = "https://$host/version/$gameVersion/assets/cosmetic"
    val layers = remember(player.avatarBottom, player.avatarMid, player.avatarTop, player.avatarExpression) {
        listOf(player.avatarBottom, player.avatarMid, player.avatarTop, player.avatarExpression)
            .filter { it.isNotBlank() }
            .map { "$baseUrl/$it" }
    }

    val context = LocalContext.current

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(SurfaceBorder),
        contentAlignment = Alignment.Center,
    ) {
        layers.forEach { url ->
            val model = remember(url) {
                ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = 1.8f
                        scaleY = 1.8f
                        translationY = size.toPx() * 0.25f
                    },
            )
        }
    }
}
