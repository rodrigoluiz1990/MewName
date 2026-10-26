package com.mewname.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mewname.app.domain.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

private data class PvpTeamData(val candidates: List<PvpTeamCandidate>, val portraits: Map<String, PokedexOpponent>,
    val moves: Map<String, MoveCatalogEntry>, val snapshot: PokedexOnlineSnapshot, val stale: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PvpTeamScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    val preferences = remember { context.getSharedPreferences("mewname_prefs", 0) }
    val repository = remember { PokedexDetailsRepository(context.applicationContext) }
    val caps = listOf(1500, 2500, 10000)
    var cap by rememberSaveable { mutableIntStateOf(preferences.getInt("pvp_team_cap", 1500).takeIf { it in caps } ?: 1500) }
    var team by remember(cap) { mutableStateOf(runCatching {
        val saved = JSONArray(preferences.getString("pvp_team_$cap", "[]"))
        List(3) { i -> saved.optJSONObject(i)?.let { PvpTeamSlot(it.optString("id"), raidStrings(it.optJSONArray("moves"))) } ?: PvpTeamSlot() }
    }.getOrDefault(List(3) { PvpTeamSlot() })) }
    fun update(index: Int, slot: PvpTeamSlot) {
        team = team.toMutableList().also { it[index] = slot }
        val json = JSONArray().apply { team.forEach { put(JSONObject().put("id", it.id).put("moves", JSONArray(it.moves))) } }
        preferences.edit().putString("pvp_team_$cap", json.toString()).apply()
    }
    var data by remember(cap, language) { mutableStateOf<PvpTeamData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf<Int?>(null) }
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(cap, tab) { listState.scrollToItem(0) }
    LaunchedEffect(cap, refresh, language) {
        loading = true; failed = false
        try {
            suspend fun buildData(ranking: PokedexOnlineSnapshot, master: PokedexOnlineSnapshot) = withContext(Dispatchers.IO) {
                val candidates = parsePvpTeamCandidates(ranking.json, master.json)
                val ids = (candidates.map { it.id } + candidates.flatMap { it.ratings.keys }).distinct()
                PvpTeamData(candidates, repository.opponents(ids, GameInfoRepository.loadPokedexCatalog(context), language),
                    GameInfoRepository.loadMoveCatalog(context).associateBy { pokedexIdentity(it.name) }, ranking, ranking.stale || master.stale)
            }
            val saved = withContext(Dispatchers.IO) {
                repository.cachedOnline("rankings-$cap.json") to repository.cachedOnline("gamemaster.json")
            }
            if (saved.first != null && saved.second != null) data = buildData(saved.first!!, saved.second!!)
            val fresh = withContext(Dispatchers.IO) {
                repository.online("rankings-$cap.json", refresh > 0) to repository.online("gamemaster.json", refresh > 0)
            }
            if (fresh != saved) data = buildData(fresh.first, fresh.second)

        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { failed = true }
        finally { loading = false }
    }
    val candidates = data?.candidates.orEmpty()
    val index = remember(candidates) { candidates.associateBy { it.id } }
    fun pokemonName(id: String): String = pvpTeamDisplayName(index[id]?.name ?: raidName(id), language)
    fun moveName(id: String) = data?.moves?.get(pokedexIdentity(id.removeSuffix("_FAST")))?.localizedName(language) ?: raidName(id)
    val valid = team.all { index[it.id] != null && it.moves.size >= 2 } && team.map { index[it.id]?.dex ?: it.id }.distinct().size == 3
    val matchups = remember(team, candidates) { pvpTeamMatchups(team, candidates) }
    BackHandler { if (tab == 1) tab = 0 else onBack() }
    Scaffold(containerColor = Color.Transparent, topBar = {
        AppTopBar(title = { Text(tr("Equipe PvP", "PvP team", "Equipo PvP")) }, navigationIcon = {
            IconButton(onClick = { if (tab == 1) tab = 0 else onBack() }) { Icon(AppIcons.Back, tr("Voltar", "Back", "Volver")) }
        }, actions = { IconButton(onClick = { refresh++ }, enabled = !loading) { Icon(AppIcons.Refresh, tr("Atualizar", "Refresh", "Actualizar")) } })
    }) { padding ->
        if (data == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator() else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr("Não foi possível carregar os dados de PvP.", "Could not load PvP data.", "No se pudieron cargar los datos PvP."))
                AppActionButton(onClick = { refresh++ }) { Text(tr("Tentar novamente", "Retry", "Reintentar")) }
            }
        } else LazyColumn(Modifier.fillMaxSize().padding(padding), state = listState, contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, bottom = LocalNavigationContentPadding.current + 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { AppPillTabRow(caps.indexOf(cap)) { caps.forEach { value -> AppPillTab(cap == value, {
                cap = value; tab = 0; picker = null; preferences.edit().putInt("pvp_team_cap", value).apply()
            }, { Text(when (value) { 1500 -> tr("Grande · 1500", "Great · 1500", "Super · 1500"); 2500 -> "Ultra · 2500"; else -> "Master" }) }) } } }
            item { AppPillTabRow(tab) {
                AppPillTab(tab == 0, { tab = 0 }, { Text(tr("Equipe", "Team", "Equipo")) })
                AppPillTab(tab == 1, { tab = 1 }, { Text(tr("Análise", "Analysis", "Análisis")) })
            } }
            if (loading) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
            if (failed || data?.stale == true) item { Text(tr("Não foi possível atualizar. Exibindo os dados salvos.", "Could not refresh. Showing saved data.", "No se pudo actualizar. Se muestran datos guardados.")) }
            if (tab == 0) {
                items(3) { position ->
                    val slot = team[position]
                    val candidate = index[slot.id]
                    AppSectionCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth().clickable { query = ""; picker = position }, verticalAlignment = Alignment.CenterVertically) {
                            PokedexSprite(data?.portraits?.get(slot.id)?.sprite, null, Modifier.size(64.dp))
                            Text("${position + 1}. " + if (slot.id.isEmpty()) tr("Selecionar Pokémon", "Select Pokémon", "Seleccionar Pokémon") else pokemonName(slot.id), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text(tr("Trocar", "Change", "Cambiar"), style = MaterialTheme.typography.labelMedium)
                        }
                        if (candidate != null) {
                            repeat(3) { moveIndex ->
                                val selected = slot.moves.getOrNull(moveIndex).orEmpty()
                                val options = (if (moveIndex == 0) candidate.fast else candidate.charged)
                                    .filter { it == selected || it !in slot.moves }
                                PvpTeamMoveDropdown(when (moveIndex) { 0 -> tr("Rápido", "Fast", "Rápido"); 1 -> tr("Carregado 1", "Charged 1", "Cargado 1"); else -> tr("Carregado 2", "Charged 2", "Cargado 2") },
                                    selected, if (moveIndex == 2) listOf("") + options else options, ::moveName) { id ->
                                    val moves = List(3) { slot.moves.getOrNull(it).orEmpty() }.toMutableList().also { it[moveIndex] = id }.filter { it.isNotBlank() }
                                    update(position, slot.copy(moves = moves))
                                }
                            }
                            if (!samePvpMoves(slot.moves, candidate.recommended)) {
                                Text(tr("Golpes personalizados: confrontos não calculados.", "Custom moves: matchups not calculated.", "Ataques personalizados: enfrentamientos sin calcular."), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { update(position, slot.copy(moves = candidate.recommended)) }) { Text(tr("Usar recomendados", "Use recommended", "Usar recomendados")) }
                            }
                        } else if (slot.id.isNotEmpty()) Text(tr("Sem registro nesta liga. Escolha outro Pokémon.", "Not listed in this league. Choose another Pokémon.", "Sin registro en esta liga. Elige otro Pokémon."))
                    } }
                }
                item { AppActionButton(onClick = { tab = 1 }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text(tr("Analisar equipe", "Analyze team", "Analizar equipo")) } }
            } else if (!valid) item { Text(tr("Selecione três espécies diferentes e seus golpes na aba Equipe.", "Select three different species and their moves in the Team tab.", "Selecciona tres especies diferentes y sus ataques en Equipo.")) }
            else {
                item { AppSectionCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Resumo dos confrontos publicados", "Published matchup summary", "Resumen de enfrentamientos publicados"), style = MaterialTheme.typography.titleMedium)
                    Text(tr("${matchups.count { it.covered }} com resposta favorável · ${matchups.count { it.sharedThreat }} ameaças aos três",
                        "${matchups.count { it.covered }} with a favorable answer · ${matchups.count { it.sharedThreat }} threats to all three",
                        "${matchups.count { it.covered }} con respuesta favorable · ${matchups.count { it.sharedThreat }} amenazas a los tres"))
                    Text(tr("Amostra parcial do ranking PvPoke, com os golpes recomendados. Não é uma simulação da partida nem considera seus IVs, trocas ou escolhas de escudos. Sem registro não significa empate ou vitória.",
                        "Partial PvPoke ranking sample using recommended moves. Not a match simulation; your IVs, switches and shield choices are not modeled. Missing data does not mean a tie or win.",
                        "Muestra parcial del ranking PvPoke con ataques recomendados. No simula la partida ni tus IVs, cambios o escudos. Sin datos no significa empate o victoria."), style = MaterialTheme.typography.bodySmall)
                    team.forEachIndexed { i, member -> Text("${i + 1}: ${pokemonName(member.id)}", style = MaterialTheme.typography.labelLarge) }
                    Text(tr("+ Favorável   − Desfavorável   = Empate   ? Sem dados", "+ Favorable   − Unfavorable   = Tie   ? No data", "+ Favorable   − Desfavorable   = Empate   ? Sin datos"), style = MaterialTheme.typography.bodySmall)
                } } }
                if (team.any { slot -> index[slot.id]?.let { !samePvpMoves(slot.moves, it.recommended) } == true }) item {
                    Text(tr("Os integrantes com golpes personalizados aparecem com ? até existir um simulador para recalcular esses confrontos.", "Members with custom moves show ? until a simulator can recalculate these matchups.", "Los integrantes con ataques personalizados muestran ? hasta disponer de un simulador."))
                }
                items(matchups, key = { it.opponent }) { row -> AppSectionCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        PokedexSprite(data?.portraits?.get(row.opponent)?.sprite, null, Modifier.size(42.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pokemonName(row.opponent), style = MaterialTheme.typography.labelLarge)
                            if (row.sharedThreat) Text(tr("Ameaça aos três", "Threat to all three", "Amenaza a los tres"), color = MaterialTheme.colorScheme.error)
                        }
                        row.outcomes.forEachIndexed { i, outcome -> Text("${i + 1}: " + when (outcome) {
                            PvpTeamOutcome.FAVORABLE -> "+"; PvpTeamOutcome.UNFAVORABLE -> "−"; PvpTeamOutcome.EVEN -> "="; PvpTeamOutcome.UNKNOWN -> "?"
                        }, Modifier.width(42.dp).semantics {
                            contentDescription = pokemonName(team[i].id) + ": " + when (outcome) {
                                PvpTeamOutcome.FAVORABLE -> tr("Favorável", "Favorable", "Favorable")
                                PvpTeamOutcome.UNFAVORABLE -> tr("Desfavorável", "Unfavorable", "Desfavorable")
                                PvpTeamOutcome.EVEN -> tr("Empate", "Tie", "Empate")
                                PvpTeamOutcome.UNKNOWN -> tr("Sem dados", "No data", "Sin datos")
                            }
                        }, color = when (outcome) {
                            PvpTeamOutcome.FAVORABLE -> if (LocalAppAppearance.current.dark) Color(0xFF80DFA7) else Color(0xFF1B7545)
                            PvpTeamOutcome.UNFAVORABLE -> MaterialTheme.colorScheme.error
                            else -> LocalAppAppearance.current.text
                        }, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium) }
                    }
                } }
                if (matchups.isEmpty()) item { Text(tr("Sem confrontos publicados para esta equipe.", "No published matchups for this team.", "Sin enfrentamientos publicados para este equipo.")) }
            }
            item { Text("PvPoke · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(data!!.snapshot.fetchedAt)), style = MaterialTheme.typography.bodySmall) }
        }
    }
    picker?.let { position -> Dialog(onDismissRequest = { picker = null }) {
        Surface(Modifier.fillMaxWidth().heightIn(max = 580.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            color = LocalAppAppearance.current.background.first(), contentColor = LocalAppAppearance.current.text,
            border = androidx.compose.foundation.BorderStroke(.75.dp, LocalAppAppearance.current.border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppModalHeader(tr("Selecionar Pokémon", "Select Pokémon", "Seleccionar Pokémon"), { picker = null })
            AppGlassTextField(query, { query = it }, Modifier.fillMaxWidth(), search = true, singleLine = true, label = { Text(tr("Nome ou nº da Pokédex", "Name or Pokédex number", "Nombre o n.º Pokédex")) })
            val taken = team.filterIndexed { i, _ -> i != position }.mapNotNull { index[it.id]?.dex }.toSet()
            val filtered = candidates.filter { c -> c.dex !in taken && (query.isBlank() || pokedexIdentity(pokemonName(c.id)).contains(pokedexIdentity(query)) || c.dex?.toString() == query.trim()) }
            if (filtered.isEmpty()) Text(tr("Nenhum Pokémon disponível neste ranking.", "No Pokémon available in this ranking.", "Ningún Pokémon disponible en este ranking."))
            LazyColumn(Modifier.weight(1f, fill = false)) { items(filtered, key = { it.id }) { candidate ->
                Row(Modifier.fillMaxWidth().clickable { update(position, PvpTeamSlot(candidate.id, candidate.recommended)); picker = null }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PokedexSprite(data?.portraits?.get(candidate.id)?.sprite, null, Modifier.size(64.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(pokemonName(candidate.id), Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    candidate.dex?.let { Text("#$it", style = MaterialTheme.typography.labelSmall) }
                }
            } }
        } }
    } }
}

@Composable
private fun PvpTeamMoveDropdown(label: String, value: String, options: List<String>, name: (String) -> String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val language = appLanguage()
    fun display(id: String) = if (id.isEmpty()) lt(language, "Nenhum", "None", "Ninguno") else name(id)
    Box {
        AppActionButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), secondary = true) { Text("$label: ${display(value)}", Modifier.weight(1f))
            Icon(AppIcons.Next, null, Modifier.size(18.dp).rotate(90f)) }
        AppSelectionMenu(expanded, { expanded = false }, options, value, ::display, onSelect)
    }
}
