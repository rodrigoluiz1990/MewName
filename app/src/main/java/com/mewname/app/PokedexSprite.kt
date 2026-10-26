package com.mewname.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** Match the collection grid: size measures the visible sprite, not transparent margins. */
@Composable
internal fun PokedexSprite(url: String?, name: String?, modifier: Modifier) {
    val context = LocalContext.current
    val request = remember(url, context) {
        ImageRequest.Builder(context).data(url)
            .transformations(CollectionSpriteTrim).crossfade(true).build()
    }
    AsyncImage(request, name, modifier, contentScale = ContentScale.Fit)
}