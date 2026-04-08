package com.mgafk.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mgafk.app.data.repository.MgApi

/**
 * Displays a sprite from the MG API.
 * Usage: SpriteImage("pets", "Worm") or SpriteImage(url = "https://mg-api.ariedam.fr/...")
 */
@Composable
fun SpriteImage(
    category: String,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = null,
) {
    SpriteImage(
        url = MgApi.spriteUrl(category, name),
        modifier = modifier,
        size = size,
        contentDescription = contentDescription,
    )
}

@Composable
fun SpriteImage(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = null,
) {
    if (url.isNullOrBlank()) return

    val context = LocalContext.current
    val model = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .build()
    }

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}
