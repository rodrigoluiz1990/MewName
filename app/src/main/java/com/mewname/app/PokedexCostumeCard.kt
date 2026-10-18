package com.mewname.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

internal data class PokedexCostume(val label: String, val url: String)

@Composable
internal fun PokedexCostumeCard(costume: PokedexCostume, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(costume.url, context) {
        ImageRequest.Builder(context).data(costume.url)
            .transformations(CollectionSpriteTrim).crossfade(true).build()
    }
    AppSectionCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AsyncImage(request, costume.label, Modifier.size(100.dp), contentScale = ContentScale.Fit,
                placeholder = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_gallery),
                error = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_gallery))
            Text(costume.label, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}
