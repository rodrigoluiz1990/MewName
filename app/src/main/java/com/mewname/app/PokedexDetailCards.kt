package com.mewname.app

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mewname.app.domain.*
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PokedexMatchupCards(matchups: List<TypeMatchupEntry>) {
    val language = appLanguage()
    val dark = LocalAppAppearance.current.dark
    listOf(matchups.filter { it.multiplier > 1.0 }, matchups.filter { it.multiplier < 1.0 })
        .filter { it.isNotEmpty() }.forEach { group ->
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                group.sortedBy { it.multiplier }.forEach { matchup ->
                    val accent = when {
                        matchup.multiplier > 1.0 -> Color(0xFFCA4149)
                        matchup.multiplier < 0.5 -> Color(0xFF26772D)
                        else -> Color(0xFF4D963E)
                    }
                    Column(Modifier.width(68.dp).height(90.dp).background(accent.copy(alpha = if (dark) .25f else .16f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 3.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TypeIcon(matchup.attackType, language, framed = false)
                        Text(localizedTypeLabel(matchup.attackType, language), style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.height(20.dp).fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1)
                        Text(String.format(Locale.US, "%.1f%%", matchup.multiplier * 100), color = Color.White,
                            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.background(accent, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }
}

@Composable
internal fun PokedexFamilyCards(details: PokedexLocalDetails, entries: List<PokedexCatalogEntry>, onSelect: (String) -> Unit) {
    // Merge paths by their shared prefix so branching never repeats the ancestors.
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        details.evolutionPaths.filter { it.members.isNotEmpty() }.groupBy { it.members.first() }.values.forEach { paths ->
            PokedexFamilyBranch(paths, details, entries, onSelect)
        }
    }
}

@Composable
private fun PokedexFamilyBranch(paths: List<PokedexEvolutionPath>, details: PokedexLocalDetails,
    entries: List<PokedexCatalogEntry>, onSelect: (String) -> Unit) {
    val language = appLanguage()
    val member = paths.first().members.first()
    val entry = entries.firstOrNull { pokedexIdentity(it.name) == pokedexIdentity(member) }
    val branches = paths.filter { it.members.size > 1 }.groupBy { it.members[1] }.values.toList()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.width(96.dp).clickable(enabled = entry != null, role = Role.Button) { entry?.let { onSelect(it.name) } }
            .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(details.familySprites[member], member, Modifier.size(80.dp))
            Text(entry?.localizedName(language) ?: member, modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        if (branches.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            branches.forEachIndexed { index, branch ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val arrow = when {
                        branches.size == 1 -> "→"
                        index < branches.lastIndex / 2f -> "↗"
                        index > branches.lastIndex / 2f -> "↘"
                        else -> "→"
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PokedexEvolutionCost(branch.first().steps.first(), language, details.candyColors[member], details.megaColors[branch.first().steps.first().to])
                        if (branch.first().steps.first().gigantamax) {
                            val color = MaterialTheme.colorScheme.primary
                            Canvas(Modifier.size(28.dp)) {
                                val start = Offset(0f, if (arrow == "↗") size.height else if (arrow == "↘") 0f else size.height / 2)
                                val end = Offset(size.width, if (arrow == "↗") 0f else if (arrow == "↘") size.height else size.height / 2)
                                drawLine(color, start, end, strokeWidth = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))
                            }
                        } else Text(arrow, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                    }
                    PokedexFamilyBranch(branch.map { PokedexEvolutionPath(it.members.drop(1), it.steps.drop(1)) },
                        details, entries, onSelect)
                }
            }
        }
    }
}

@Composable
private fun PokedexEvolutionCost(step: PokedexEvolution, language: AppLanguage, candyColors: Pair<Int, Int>?, megaColors: Pair<Int, Int>?) {
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    if (step.gigantamax) {
        Text(tr("Forma Gigamax", "Gigantamax form", "Forma Gigamax"), modifier = Modifier.width(96.dp),
            textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
        return
    }
    val requirements = step.requirements
    val conditions = buildList {
        if (step.tradeFree) add(tr("0 após troca", "0 after trade", "0 tras intercambio"))
        if (requirements.has("evolutionItemRequirement")) add(tr("+ item de evolução", "+ evolution item", "+ objeto evolutivo"))
        if (requirements.has("lureItemRequirement")) add(tr("Isca especial", "Special lure", "Cebo especial"))
        if (requirements.optDouble("kmBuddyDistanceRequirement", 0.0) > 0) add("${requirements.optDouble("kmBuddyDistanceRequirement").toInt()} km")
        if (requirements.optBoolean("mustBeBuddy")) add(tr("Como companheiro", "As buddy", "Como compañero"))
        if (requirements.optBoolean("onlyDaytime")) add(tr("Dia", "Daytime", "Día"))
        if (requirements.optBoolean("onlyNighttime")) add(tr("Noite", "Night", "Noche"))
        if (requirements.optBoolean("onlyDuskPeriod")) add(tr("Crepúsculo", "Dusk", "Crepúsculo"))
        if (requirements.optBoolean("onlyFullMoon")) add(tr("Lua cheia", "Full moon", "Luna llena"))
        if (requirements.optBoolean("onlyUpsideDown")) add(tr("Aparelho invertido", "Device upside down", "Dispositivo invertido"))
        if (requirements.has("genderRequirement")) add(if (requirements.optString("genderRequirement").contains("FEMALE")) "♀" else "♂")
        if (requirements.has("questDisplay")) add(tr("Tarefa de evolução", "Evolution task", "Tarea de evolución"))
    }
    Column(Modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (step.megaEnergy != null) PokedexMegaEnergyIcon(megaColors)
            else PokedexCandyIcon(candyColors)
            Text("${step.megaEnergy ?: step.candy ?: "—"}", style = MaterialTheme.typography.labelLarge)
        }
        Text(if (step.megaEnergy != null) tr("Mega Energia", "Mega Energy", "Megaenergía") else tr("Doces", "Candy", "Caramelos"), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
        if (step.megaEnergy != null) Text(tr("1ª vez", "First time", "1.ª vez"), style = MaterialTheme.typography.labelSmall)
        conditions.forEach { Text(it, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) }
    }
}
@Composable
private fun PokedexCandyIcon(colors: Pair<Int, Int>?) {
    val primary = Color(colors?.first ?: 0xFFEE78AC.toInt())
    val secondary = Color(colors?.second ?: 0xFFFFD8EB.toInt())
    // GO candy texture: red = stripes, green = body, blue = highlights.
    val matrix = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
        secondary.red, primary.red, 1f, 0f, 0f,
        secondary.green, primary.green, 1f, 0f, 0f,
        secondary.blue, primary.blue, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f))
    AssetImageIcon("pokedex_icons/candy_rgb.png", null, Modifier.size(22.dp),
        colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(matrix))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PokedexWeather(types: List<String>) {
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    val weather = raidWeather(types)
    if (weather.isEmpty()) return
    Text(tr("Clima favorável", "Weather boost", "Clima favorable"), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        weather.forEach { key ->
            val label = when (key) {
                "sunny" -> tr("Ensolarado", "Sunny", "Soleado")
                "rain" -> tr("Chuva", "Rainy", "Lluvia")
                "partly" -> tr("Parcialmente nublado", "Partly cloudy", "Parcialmente nublado")
                "cloudy" -> tr("Nublado", "Cloudy", "Nublado")
                "wind" -> tr("Vento", "Windy", "Viento")
                "snow" -> tr("Neve", "Snow", "Nieve")
                else -> tr("Neblina", "Fog", "Niebla")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssetImageIcon("pokedex_icons/weather_$key.png", null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
@Composable
private fun PokedexMegaEnergyIcon(colors: Pair<Int, Int>?) {
    val body = Color(colors?.first ?: 0xFF78C7AA.toInt())
    val highlight = Color(colors?.second ?: 0xFFE0FBFF.toInt())
    // Preserve the GO texture's shading, rim and DNA masks rather than displaying an outline.
    // This static rendering approximates the game's animated material.
    val matrix = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
        body.red * 1.2f, highlight.red, highlight.red, 0f, 0f,
        body.green * 1.2f, highlight.green, highlight.green, 0f, 0f,
        body.blue * 1.2f, highlight.blue, highlight.blue, 0f, 0f,
        0f, 0f, 0f, 1f, 0f))
    AssetImageIcon("pokedex_icons/mega_energy.png", null, Modifier.size(24.dp),
        colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(matrix))
}
@Composable
internal fun PokedexCpRows(cp: Map<Int, Pair<Int?, Int?>>) {
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    cp.toSortedMap(reverseOrder()).entries.forEachIndexed { index, (level, values) ->
        if (index > 0) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("${tr("Nível", "Level", "Nivel")} $level", fontWeight = FontWeight.Bold)
                Text(when (level) {
                    15 -> tr("Pesquisas", "Research", "Investigaciones")
                    20 -> tr("Raids / Ovos", "Raids / Eggs", "Incursiones / Huevos")
                    25 -> tr("Raids com clima favorável", "Weather-boosted raids", "Incursiones con clima favorable")
                    40 -> tr("Fortalecimento", "Power up", "Potenciación")
                    50 -> tr("PC máximo", "Max CP", "PC máximo")
                    51 -> tr("Melhor Companheiro", "Best Buddy", "Mejor Compañero")
                    else -> ""
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val value = if (level <= 25) "${values.first ?: "—"}–${values.second ?: "—"}" else "${values.second ?: "—"}"
            Text("$value ${tr("PC", "CP", "PC")}", fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun PokedexOpponentRow(title: String, ids: List<String>, opponents: Map<String, PokedexOpponent>) {
    if (ids.isEmpty()) return
    val language = appLanguage()
    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ids.distinct(), key = { it }) { id ->
            val opponent = opponents[id]
            val originPalkia = id.removeSuffix("_shadow") == "palkia_origin"
            val name = if (originPalkia) "Palkia" else opponent?.name ?: raidName(id.removeSuffix("_shadow"))
            Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                    if (opponent?.shadow == true || id.endsWith("_shadow")) CollectionShadowAura()
                    if (opponent?.sprite != null) AsyncImage(opponent.sprite, name, Modifier.size(64.dp))
                    else Text("?", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(name, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                if (originPalkia) Text(lt(language, "(Origem)", "(Origin)", "(Origen)"),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (opponent?.shadow == true || id.endsWith("_shadow")) Text(
                    lt(language, "(Sombroso)", "(Shadow)", "(Oscuro)"), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
