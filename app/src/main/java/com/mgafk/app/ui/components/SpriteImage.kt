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
 *
 * When `mutations` is non-empty and the category is `"pets"` or `"plants"`, the
 * composed endpoint is used so mutation overlays are pre-rendered on the base sprite.
 * Otherwise, the plain sprite from the categorical URL is used.
 *
 * Usage:
 *   SpriteImage("pets", "Worm")
 *   SpriteImage("pets", "Worm", mutations = pet.mutations)
 *   SpriteImage("plants", "Carrot", mutations = crop.mutations)
 *   SpriteImage(url = "https://mg-api.ariedam.fr/...")
 */
@Composable
fun SpriteImage(
    category: String,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = null,
    mutations: List<String> = emptyList(),
) {
    val url = when (category) {
        "pets" -> MgApi.petSpriteUrl(name, mutations)
        "plants" -> MgApi.cropSpriteUrl(name, mutations)
        else -> MgApi.spriteUrl(category, name)
    }
    SpriteImage(
        url = url,
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
