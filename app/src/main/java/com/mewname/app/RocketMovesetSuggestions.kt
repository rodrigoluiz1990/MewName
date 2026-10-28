package com.mewname.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.mewname.app.domain.*
import kotlinx.coroutines.*
import java.util.Locale

@Composable
internal fun RocketMovesetSuggestions(selected: List<LeekPokemon>) {
    if (selected.isEmpty()) return
    val context = LocalContext.current.applicationContext
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    var failure by remember(selected, language) { mutableStateOf(false) }
    var calculating by remember { mutableStateOf(true) }
    val results by produceState<List<RocketTeamMoveset>?>(null, selected, language) {
        calculating = true
        try {
            value = withContext(Dispatchers.Default) {
                val catalog = GameInfoRepository.loadPokedexCatalog(context)
                val stats = GameInfoRepository.loadMoveCatalog(context).associateBy { pokedexIdentity(it.name) }
                val chart = GameInfoRepository.loadTypeEffectiveness(context)
                val defenders = selected.map { target ->
                    if (target.types.isNotEmpty()) return@map target.types
                    val identity = pokedexIdentity(target.name)
                    val defender = catalog.firstOrNull { pokedexIdentity(it.name) == identity }
                        ?: catalog.firstOrNull { it.aliases.any { alias -> pokedexIdentity(alias) == identity } }
                    listOfNotNull(defender?.type1, defender?.type2)
                }
                val options = catalog.flatMap { pokemon ->
                    ensureActive()
                    rocketTeamOptions(pokemon, PokemonMoveRepository.load(context, pokemon.name, language), stats, defenders, chart)
                }
                ensureActive()
                selectRocketTeam(options)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure = true; value = emptyList() }
        finally { calculating = false }
    }
    val team = results.orEmpty()
    val clipboard = LocalClipboardManager.current
    var copied by remember(selected, team) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1400); copied = false } }
    Text(tr("Equipe sugerida", "Suggested team", "Equipo sugerido"), style = MaterialTheme.typography.titleMedium)
    if (results == null) Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment=Alignment.Center) { CircularProgressIndicator() }
    if (calculating && results != null) Text(tr("Atualizando sugestões…", "Updating suggestions…", "Actualizando sugerencias…"), style=MaterialTheme.typography.bodySmall)
    if (failure) Text(tr("Não foi possível calcular as sugestões.", "Could not calculate suggestions.", "No se pudieron calcular las sugerencias."))
    if (results != null && team.isEmpty() && !failure) Text(tr("Dados insuficientes para a equipe selecionada.",
        "Insufficient data for the selected team.", "Datos insuficientes para el equipo seleccionado."))
    if (team.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        team.forEach { suggestion ->
            AppSectionCard(Modifier.weight(1f)) {
                Column(Modifier.fillMaxWidth().padding(6.dp), horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    RaidPortrait(suggestion.pokemon.name.uppercase(Locale.ROOT).replace(' ', '_'), Modifier.size(64.dp))
                    Text(suggestion.pokemon.localizedName(language), style=MaterialTheme.typography.labelLarge,
                        textAlign=TextAlign.Center)
                    RocketSuggestedMove(suggestion.fast, suggestion.fastType)
                    suggestion.charged.forEachIndexed { index, move -> RocketSuggestedMove(move, suggestion.chargedTypes[index]) }
                }
            }
        }
    }
    if (team.isNotEmpty()) Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        AppActionButton(enabled = !calculating && !failure, onClick = {
            clipboard.setText(AnnotatedString(team.map { it.pokemon.number }.distinct().joinToString(",")))
            copied = true
        }) {
            Icon(AppIcons.Copy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (copied) tr("Equipe copiada", "Team copied", "Equipo copiado")
                else tr("Copiar equipe sugerida", "Copy suggested team", "Copiar equipo sugerido"))
        }
    }

}

@Composable
private fun RocketSuggestedMove(move: PokemonMove, type: String) {
    val language = appLanguage()
    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        AsyncImage("file:///android_asset/types/POKEMON_TYPE_${type.uppercase(Locale.ROOT)}.png",
            localizedTypeLabel(type, language), Modifier.size(18.dp))
        Text(move.localizedName + if (move.legacy) " ${configuredLegacyMoveSymbol()}" else "", modifier=Modifier.weight(1f), textAlign=TextAlign.Start, maxLines=2, overflow=TextOverflow.Ellipsis, style=MaterialTheme.typography.labelSmall)
    }
}
