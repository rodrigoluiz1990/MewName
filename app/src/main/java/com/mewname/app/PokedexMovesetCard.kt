package com.mewname.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mewname.app.domain.*
import java.util.Locale

@Composable
internal fun PokedexMovesetCard(details: PokedexLocalDetails?, leagues: Map<Int, PokedexLeagueDetails>, loading: Boolean) {
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    val combinations = remember(details) { details?.let { bestPokedexPveMovesets(it.moves, it.moveStats, it.types) }.orEmpty() }
    AppSectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Melhores combinações", "Best movesets", "Mejores combinaciones"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(tr("PvE · Raids e ginásios", "PvE · Raids and gyms", "PvE · Incursiones y gimnasios"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (combinations.isEmpty()) Text(tr("Dados insuficientes para comparar combinações.", "Insufficient data to compare movesets.", "Datos insuficientes para comparar combinaciones."))
            combinations.forEachIndexed { index, pair ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("#${index + 1} · " + tr("Pontuação", "Score", "Puntuación") + ": " + String.format(Locale.getDefault(), "%.1f", pair.powerPerSecond), style = MaterialTheme.typography.labelLarge)
                RecommendedMoveset(pair.fast, listOf(pair.charged), details)
            }
            HorizontalDivider()
            Text(tr("PvP · Por liga", "PvP · By league", "PvP · Por liga"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            listOf(500, 1500, 2500, 10000).forEach { cap ->
                val league = leagues[cap]
                val title = when (cap) {
                    500 -> tr("Copinha (500 PC)", "Little Cup (500 CP)", "Copa Chica (500 PC)")
                    1500 -> tr("Grande Liga (1.500 PC)", "Great League (1,500 CP)", "Liga Super Ball (1500 PC)")
                    2500 -> tr("Ultra Liga (2.500 PC)", "Ultra League (2,500 CP)", "Liga Ultra Ball (2500 PC)")
                    else -> tr("Liga Mestra (PC ilimitado)", "Master League (unlimited CP)", "Liga Master Ball (PC ilimitado)")
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssetImageIcon("collection/" + when (cap) { 500 -> "little.png"; 1500 -> "great.png"; 2500 -> "ultra.png"; else -> "master.png" }, null, Modifier.size(24.dp))
                    Text(title, style = MaterialTheme.typography.labelLarge)
                }
                if (league?.moves.isNullOrEmpty()) Text(if (loading && league == null) tr("Carregando recomendação…", "Loading recommendation…", "Cargando recomendación…")
                    else tr("Sem recomendação disponível para esta liga.", "No recommendation available for this league.", "Sin recomendación para esta liga."), style = MaterialTheme.typography.bodySmall)
                val recommended = league?.moves.orEmpty().map { id ->
                    val key = pokedexIdentity(id.removeSuffix("_FAST"))
                    val known = (details?.moves?.fastMoves.orEmpty() + details?.moves?.chargedMoves.orEmpty()).firstOrNull { pokedexIdentity(it.name) == key }
                    val stat = details?.moveStats?.get(key)
                    known ?: PokemonMove(id.removeSuffix("_FAST"), stat?.localizedName(language) ?: raidName(id.removeSuffix("_FAST")), false)
                }
                recommended.firstOrNull()?.let { fast ->
                    RecommendedMoveset(fast, recommended.drop(1), details)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("PvP: recomendações do PvPoke.", "PvP: PvPoke recommendations.", "PvP: recomendaciones de PvPoke."), style = MaterialTheme.typography.bodySmall)
                Text(tr("★ indica golpe legado/especial.", "★ denotes a legacy/special move.", "★ indica ataque legado/especial."), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RecommendedMoveset(fast: PokemonMove, charged: List<PokemonMove>, details: PokedexLocalDetails?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            RecommendedMove(fast, details)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            charged.forEach { RecommendedMove(it, details) }
        }
    }
}

@Composable
private fun RecommendedMove(move: PokemonMove, details: PokedexLocalDetails?) {
    val language = appLanguage()
    val stat = details?.moveStats?.get(pokedexIdentity(move.name))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stat?.let { TypeIcon(it.type, language, Modifier.size(22.dp), framed = false) }
        Text(move.localizedName + if (move.legacy) " ★" else "", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}
