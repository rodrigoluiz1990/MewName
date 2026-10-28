package com.mewname.app

import com.mewname.app.domain.NON_UPPER_ALPHANUMERIC_RUNS
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.mewname.app.domain.RaidCounterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Preserve forms when converting a displayed Max name or catalog ID to a sprite ID. */
internal fun maxPortraitId(value: String?): String {
    val name = value.orEmpty().trim().uppercase(Locale.US)
    val gigantamax = Regex("^(GIGANTAMAX|GIGAMAX)\\s+").containsMatchIn(name)
    val id = name.replace(Regex("^(GIGANTAMAX|GIGAMAX|DYNAMAX|DINAMAX)\\s+"), "")
        .replace("'", "").replace(NON_UPPER_ALPHANUMERIC_RUNS, "_").trim('_')
    return when {
        gigantamax -> "${id.removeSuffix("_GIGANTAMAX")}_GIGANTAMAX"
        id == "ZACIAN_CROWNED_SWORD" || id == "ZAMAZENTA_CROWNED_SHIELD" -> "${id}_FORM"
        else -> id
    }
}

@Composable
internal fun MaxPokemonPortrait(pokemon: String?, modifier: Modifier = Modifier, refreshToken: Int = 0) {
    val context = LocalContext.current
    val id = remember(pokemon) { maxPortraitId(pokemon) }
    val source by produceState<String?>(null, id, context, refreshToken) {
        value = withContext(Dispatchers.IO) {
            val asset = "raids/max/$id.png"
            val bundled = runCatching { context.assets.open(asset).use { } }.isSuccess
            if (bundled) "file:///android_asset/$asset"
            else RaidCounterRepository(context.applicationContext).image(id)
        }
    }
    val request = remember(context, source, refreshToken) {
        PortraitDownloadPolicy.request(context, source, refreshToken, CollectionSpriteTrim)
    }
    // A missing sprite is downloaded once; after a failure only the explicit refresh retries it.
    // Bundled sprites and successfully cached images remain available without downloading again.
    key(id, refreshToken) {
        AsyncImage(
            model = request,
            contentDescription = pokemon,
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    }
}
