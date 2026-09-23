package com.mewname.app

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.mewname.app.domain.RaidCounterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Preserve forms when converting a displayed Max name or catalog ID to a sprite ID. */
internal fun maxPortraitId(value: String?): String {
    val name = value.orEmpty().trim().uppercase(Locale.US)
    val gigantamax = Regex("^(GIGANTAMAX|GIGAMAX)\\s+").containsMatchIn(name)
    val id = name.replace(Regex("^(GIGANTAMAX|GIGAMAX|DYNAMAX|DINAMAX)\\s+"), "")
        .replace("'", "").replace(Regex("[^A-Z0-9]+"), "_").trim('_')
    return when {
        gigantamax -> "${id.removeSuffix("_GIGANTAMAX")}_GIGANTAMAX"
        id == "ZACIAN_CROWNED_SWORD" || id == "ZAMAZENTA_CROWNED_SHIELD" -> "${id}_FORM"
        else -> id
    }
}

@Composable
internal fun MaxPokemonPortrait(pokemon: String?, modifier: Modifier = Modifier, offline: Boolean = true) {
    val context = LocalContext.current
    val id = remember(pokemon) { maxPortraitId(pokemon) }
    val source by produceState<String?>(null, id, context) {
        value = withContext(Dispatchers.IO) {
            val asset = "raids/max/$id.png"
            val bundled = runCatching { context.assets.open(asset).use { } }.isSuccess
            if (bundled) "file:///android_asset/$asset"
            else RaidCounterRepository(context.applicationContext).image(id)
        }
    }
    AsyncImage(
        model = ImageRequest.Builder(context).data(source)
            .networkCachePolicy(if (offline) CachePolicy.DISABLED else CachePolicy.ENABLED).build(),
        contentDescription = pokemon,
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}
