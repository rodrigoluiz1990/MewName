package com.mewname.app

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mewname.app.domain.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PokedexDetailsScreen(initial: PokedexCatalogEntry, entries: List<PokedexCatalogEntry>, onClose: () -> Unit) {
    val context = LocalContext.current
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    var selectedName by rememberSaveable(initial.name) { mutableStateOf(initial.name) }
    val entry = entries.firstOrNull { it.name == selectedName } ?: initial
    val repository = remember { PokedexDetailsRepository(context.applicationContext) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val moveEffects by produceState<Map<Int, Map<String, Double>>>(emptyMap()) {
        value = withContext(Dispatchers.IO) { loadMoveEffects(context.applicationContext) }
    }
    var refresh by remember { mutableIntStateOf(0) }
    var local by remember(entry.name, language) { mutableStateOf<PokedexLocalDetails?>(null) }
    var localFailed by remember(entry.name, language) { mutableStateOf(false) }
    var profile by remember(entry.name) { mutableStateOf<JSONObject?>(null) }
    var profileSnapshot by remember(entry.name) { mutableStateOf<PokedexOnlineSnapshot?>(null) }
    var onlineLoading by remember(entry.name) { mutableStateOf(true) }
    var onlineError by remember(entry.name) { mutableStateOf(false) }
    val leagues = remember(entry.name) { mutableStateMapOf<Int, PokedexLeagueDetails>() }
    val opponentIds = leagues.values.flatMap { it.wins + it.counters }.distinct().sorted()
    val opponents by produceState<Map<String, PokedexOpponent>>(emptyMap(), opponentIds, entries, language) {
        value = withContext(Dispatchers.IO) { repository.opponents(opponentIds, entries, language) }
    }
    LaunchedEffect(entry.name, language) {
        try { local = withContext(Dispatchers.IO) { repository.local(entry, language) } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { localFailed = true; android.util.Log.w("PokedexDetails", "Local data", e) }
    }
    LaunchedEffect(entry.name, refresh) {
        onlineLoading = true; onlineError = false
        try {
            val snapshot = withContext(Dispatchers.IO) { repository.online("gamemaster.json", refresh > 0) }
            profileSnapshot = snapshot
            val matched = withContext(Dispatchers.Default) { matchPvpokeSpecies(JSONObject(snapshot.json).getJSONArray("pokemon"), entry) }
            profile = matched
            if (matched != null) supervisorScope {
                listOf(1500,2500,10000).map { cap -> launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            parsePokedexLeague(cap, matched.getString("speciesId"), repository.online("rankings-$cap.json", refresh > 0))
                        }
                        leagues[cap] = result
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { onlineError = true }
                } }.joinAll()
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { onlineError = true }
        finally { onlineLoading = false }
    }
    fun moveName(id: String): String = local?.moveStats?.get(pokedexIdentity(id.removeSuffix("_FAST")))?.localizedName(language)
        ?: raidName(id.removeSuffix("_FAST"))
    val types = local?.types.orEmpty().ifEmpty { raidStrings(profile?.optJSONArray("types")).filter { it != "none" }.map { it.replaceFirstChar(Char::titlecase) } }
    val statMaximums = remember(entries) {
        listOf(entries.maxOfOrNull { it.attack ?: 0 }, entries.maxOfOrNull { it.defense ?: 0 }, entries.maxOfOrNull { it.stamina ?: 0 })
            .map { (it ?: 1).coerceAtLeast(1) }
    }
    val variants = entries.filter { it.number == entry.number }.distinctBy { it.name }
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("#${entry.number} · ${entry.localizedName(language)}") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Voltar", "Back", "Volver"))
                    }
                },
                actions = {
                    IconButton(onClick = { refresh++ }, enabled = !onlineLoading) {
                        Icon(Icons.Default.Refresh, tr("Atualizar dados online", "Refresh online data", "Actualizar datos online"))
                    }
                }
            )
        }
    ) { padding ->
            Column(Modifier.fillMaxSize().background(Brush.verticalGradient(LocalAppAppearance.current.background)).padding(padding)) {
                if (variants.size > 1) LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(variants, key = { it.name }) { form ->
                        AppChoiceChip(form.name == entry.name, { selectedName = form.name }, { Text(form.name) })
                    }
                }
                val tabs = listOf(tr("Geral","Overview","General"), tr("Golpes","Moves","Ataques"), "PvP")
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tabs.forEachIndexed { i, title -> AppChoiceChip(tab == i, { tab = i }, { Text(title, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }, modifier = Modifier.weight(1f)) }
                }
                key(entry.name, tab) {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp + LocalNavigationContentPadding.current), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (local == null) item {
                            Text(if (localFailed) tr("Falha nos dados locais.","Could not load local data.","Error en datos locales.") else tr("Carregando ficha…","Loading details…","Cargando ficha…"))
                        }
                        if (tab == 0) {
                            item { PokedexDetailsSection(tr("Pokémon e atributos","Pokémon and stats","Pokémon y atributos")) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        AsyncImage(local?.sprite, entry.name, Modifier.size(100.dp)); Text(tr("Normal","Normal","Normal"))
                                    }
                                    local?.shiny?.let { url -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        AsyncImage(url, entry.name, Modifier.size(100.dp)); Text(tr("Brilhante","Shiny","Variocolor"))
                                    } }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) { types.forEach { TypeBadge(it, language, showAssetIcon = true) } }
                                val stats = local?.stats ?: listOf(entry.attack, entry.defense, entry.stamina)
                                PokedexBaseStats(stats, statMaximums)
                            } }
                            item { PokedexDetailsSection(tr("PC por nível","CP by level","PC por nivel")) {
                                local?.let { PokedexCpRows(it.cp) }
                                PokedexWeather(types)
                            } }
                            item { PokedexDetailsSection(tr("Fraquezas e resistências","Weaknesses and resistances","Debilidades y resistencias")) {
                                PokedexMatchupCards(local?.matchups.orEmpty())
                                if (types.isEmpty()) Text(tr("Tipos indisponíveis para esta forma.","Types unavailable for this form.","Tipos no disponibles para esta forma."))
                            } }
                            item { PokedexDetailsSection(tr("Família e evoluções","Family and evolutions","Familia y evoluciones")) {
                                local?.let { PokedexFamilyCards(it, entries) { name -> selectedName = name } }
                            } }
                            profile?.let { p -> item { PokedexDetailsSection(tr("Informações adicionais","Additional information","Información adicional")) {
                                if (p.has("buddyDistance")) Text(tr("Distância por doce: ","Buddy candy distance: ","Distancia por caramelo: ") + p.optInt("buddyDistance") + " km")
                                if (p.has("thirdMoveCost")) Text(tr("Segundo carregado: ","Second charged move: ","Segundo ataque cargado: ") + p.optInt("thirdMoveCost") + tr(" de poeira estelar"," stardust"," de polvo estelar"))
                                val tags = raidStrings(p.optJSONArray("tags")); if (tags.isNotEmpty()) Text(tags.joinToString(" · "))
                            } } }
                        }
                        if (tab == 1) {
                            val moves = local?.moves
                            if (moves != null && moves.fastMoves.isEmpty() && moves.chargedMoves.isEmpty()) item { Text(tr("Golpes indisponíveis.","Moves unavailable.","Ataques no disponibles.")) }
                            listOf(tr("Rápidos","Fast","Rápidos") to moves?.fastMoves.orEmpty(), tr("Carregados","Charged","Cargados") to moves?.chargedMoves.orEmpty()).forEach { (title, list) ->
                                item { Text(title, style = MaterialTheme.typography.titleMedium) }
                                items(list, key = { title + it.name }) { move ->
                                    val detail = local?.moveStats?.get(pokedexIdentity(move.name))
                                    if (detail != null) MainMoveCard(detail, language, moveEffects[detail.moveId].orEmpty(),
                                        extraContent = if (move.legacy) ({ Text(tr("★ Golpe legado/especial", "★ Legacy/special move", "★ Ataque legado/especial"), style = MaterialTheme.typography.labelSmall) }) else null)
                                    else PokedexDetailsSection(move.localizedName + if (move.legacy) " ★" else "") {
                                        Text(tr("Dados de combate indisponíveis.", "Combat data unavailable.", "Datos de combate no disponibles."))
                                    }
                                }
                            }
                        }
                        if (tab == 2) {
                            listOf(1500,2500,10000).forEach { cap -> item { PokedexDetailsSection(when(cap) { 1500 -> "GL · 1500"; 2500 -> "UL · 2500"; else -> "ML" }) {
                                val result = leagues[cap]
                                Text(if (result?.rank != null) "#${result.rank} · ${tr("Nota","Score","Nota")}: ${result.score ?: "—"}" else if (result != null) tr("Sem registro neste ranking.","Not listed in this ranking.","Sin registro en este ranking.") else tr("Dados indisponíveis.","Data unavailable.","Datos no disponibles."))
                                result?.let { r ->
                                    r.moves.forEach { id ->
                                        val detail = local?.moveStats?.get(pokedexIdentity(id.removeSuffix("_FAST")))
                                        if (detail != null) MainMoveCard(detail, language, moveEffects[detail.moveId].orEmpty())
                                        else Text(moveName(id))
                                    }
                                    PokedexOpponentRow(tr("Confrontos favoráveis", "Key matchups", "Enfrentamientos favorables"), r.wins, opponents)
                                    PokedexOpponentRow(tr("Principais ameaças", "Key counters", "Principales amenazas"), r.counters, opponents)
                                    Text("PvPoke · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(r.snapshot.fetchedAt)) + if (r.snapshot.stale) tr(" · dados salvos"," · saved data"," · datos guardados") else "", style = MaterialTheme.typography.bodySmall)
                                }
                                profile?.optString("speciesId")?.let { id -> TextButton(onClick = { openExternalUrl(context, "https://pvpoke.com/rankings/all/$cap/overall/$id/") }) { Text(tr("Ver no PvPoke","View on PvPoke","Ver en PvPoke")) } }
                            } } }
                            item { PokedexDetailsSection(tr("IV Master · família","Master IV · family","IV Master · familia")) {
                                local?.let { d ->
                                    Text(d.masterFamily)
                                    if (d.master.isEmpty()) Text(tr("Sem regra cadastrada para esta família/forma.","No rule registered for this family/form.","Sin regla registrada para esta familia/forma."))
                                    d.master.forEach { (percent, iv) -> Text("$percent%: ${iv.first}/${iv.second}/${iv.third}") }
                                }
                            } }
                        }
                        if (onlineLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        if (onlineError || profileSnapshot?.stale == true) item { Text(tr("Não foi possível atualizar parte dos dados online. Dados locais e salvos continuam disponíveis.","Some online data could not be refreshed. Local and saved data remain available.","No se pudieron actualizar algunos datos online. Los datos locales y guardados siguen disponibles.")) }
                        if (!onlineLoading && profileSnapshot != null && profile == null) item { Text(tr("Forma sem correspondência inequívoca no PvPoke; nenhum ranking de outra forma foi usado.","No unambiguous PvPoke form match; no other form's ranking was substituted.","Forma sin correspondencia inequívoca en PvPoke; no se sustituyó por otra forma.")) }
                    }
                }
            }
    }
}

@Composable
private fun PokedexDetailsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    AppSectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
