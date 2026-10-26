package com.mewname.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mewname.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale

internal fun exactTypeExamples(entries: List<PokedexCatalogEntry>, selected: Set<String>): List<PokedexCatalogEntry> {
    val wanted = selected.map { it.trim().lowercase(Locale.ROOT) }.toSet()
    if (wanted.isEmpty()) return emptyList()
    return entries.filter { entry ->
        listOfNotNull(entry.type1, entry.type2).filter { it.isNotBlank() }
            .map { it.trim().lowercase(Locale.ROOT) }.toSet() == wanted
    }.distinctBy { it.name }.sortedBy { it.number }.take(6)
}

@Composable
internal fun TypePokemonExamples(selected: Set<String>) {
    if (selected.isEmpty()) return
    val context = LocalContext.current.applicationContext
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    val images = remember(context) { lazy { PersonalCollectionImages(context) } }
    val result by produceState<Result<List<Pair<PokedexCatalogEntry, String?>>>?>(null, selected, context) {
        value = null
        try {
            value = Result.success(withContext(Dispatchers.IO) {
                exactTypeExamples(GameInfoRepository.loadPokedexCatalog(context), selected).map { entry ->
                    entry to images.value.url(PersonalCollectionTarget(
                        JSONArray(listOf(entry.name.uppercase(Locale.ROOT), "")).toString(), entry.name))
                }
            })
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { value = Result.failure(e) }
    }
    // LazyRow evaluates its content later; capture this composition's result
    // so a new selection resetting the producer cannot invalidate its items.
    val currentResult = result
    val examples = currentResult?.getOrNull().orEmpty()
    AppSectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Exemplos de Pokémon", "Pokémon examples", "Ejemplos de Pokémon"), style = MaterialTheme.typography.titleMedium)
            when {
                currentResult == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                currentResult.isFailure -> Text(tr("Não foi possível carregar os exemplos.", "Could not load examples.", "No se pudieron cargar los ejemplos."))
                examples.isEmpty() -> Text(tr("Nenhum exemplo com essa combinação exata no catálogo local.", "No examples with this exact combination in the local catalog.", "Sin ejemplos con esta combinación exacta en el catálogo local."))
                else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(examples, key = { it.first.name }) { (entry, sprite) ->
                        Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (sprite != null) PokedexSprite(sprite, null, Modifier.size(64.dp))
                            else Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) { Text("?") }
                            Text(entry.localizedName(language), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                            Text("#${entry.number}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
