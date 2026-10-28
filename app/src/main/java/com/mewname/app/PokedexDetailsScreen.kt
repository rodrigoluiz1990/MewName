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
    val costumesByDex by produceState<Map<Int, List<PokedexCostume>>>(emptyMap(), language) {
        value = withContext(Dispatchers.IO) {
            val images = PersonalCollectionImages(context.applicationContext)
            val costumeNames = CostumeNames(context.applicationContext)
            CollectionDexCatalog.load(context.applicationContext).associate { species ->
                species.dex to species.targets.filter { it.key in species.costumeKeys }.mapNotNull { target ->
                    images.url(target)?.takeIf(costumeNames::isCostume)?.let { url ->
                        PokedexCostume(costumeNames.label(url, target.label.substringAfter(" · "), language), url)
                    }
                }.distinctBy { it.url }
            }
        }
    }
    val costumes = costumesByDex[entry.number].orEmpty()
    LaunchedEffect(entry.number, costumes.isEmpty()) {
        if (tab == 5 && costumes.isEmpty()) tab = 0
    }

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
    LaunchedEffect(entry.name, language, refresh) {
        localFailed = false
        try { local = withContext(Dispatchers.IO) { repository.local(entry, language) } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { localFailed = true; android.util.Log.w("PokedexDetails", "Local data", e) }
    }
    LaunchedEffect(entry.name, refresh) {
        onlineLoading = true; onlineError = false
        try {
            // Publish every available league before the first network request.
            val saved = withContext(Dispatchers.IO) {
                repository.cachedOnline("gamemaster.json")?.let { snapshot ->
                    val matched = matchPvpokeSpecies(repository.gamemasterPokemon(snapshot), entry)
                    val cachedLeagues = if (matched == null) emptyList() else listOf(500, 1500, 2500, 10000).mapNotNull { cap ->
                        repository.cachedOnline("rankings-$cap.json")?.let { parsePokedexLeague(cap, matched.getString("speciesId"), it) }
                    }
                    Triple(snapshot, matched, cachedLeagues)
                }
            }
            saved?.let { (snapshot, matched, cachedLeagues) ->
                profileSnapshot = snapshot; profile = matched
                cachedLeagues.forEach { leagues[it.cap] = it }
            }
            val snapshot = withContext(Dispatchers.IO) { repository.online("gamemaster.json", refresh > 0) }
            profileSnapshot = snapshot
            val matched = withContext(Dispatchers.Default) { matchPvpokeSpecies(repository.gamemasterPokemon(snapshot), entry) }
            if (profile?.optString("speciesId") != matched?.optString("speciesId")) leagues.clear()
            profile = matched
            if (matched != null) supervisorScope {
                listOf(500,1500,2500,10000).map { cap -> launch {
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
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("#${entry.number} · ${entry.localizedName(language)}") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(AppIcons.Back, tr("Voltar", "Back", "Volver"))
                    }
                },
                actions = {
                    IconButton(onClick = { refresh++ }, enabled = !onlineLoading) {
                        Icon(AppIcons.Refresh, tr("Atualizar dados online", "Refresh online data", "Actualizar datos online"))
                    }
                }
            )
        }
    ) { padding ->
            Column(Modifier.fillMaxSize().background(Brush.verticalGradient(LocalAppAppearance.current.background)).padding(padding)) {
                val tabs = listOf(tr("Geral","Overview","General"), tr("Golpes","Moves","Ataques"), "PvP", "Pokédex", tr("Tamanho", "Size", "Tamaño")) + if (costumes.isNotEmpty()) listOf(tr("Fantasia", "Costumes", "Disfraces")) else emptyList()
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    AppPillTabRow(selectedTabIndex = tab.coerceIn(tabs.indices)) {
                        tabs.forEachIndexed { i, title ->
                            AppPillTab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                        }
                    }
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
                                        PokedexSprite(local?.sprite, entry.name, Modifier.size(64.dp)); Text(tr("Normal","Normal","Normal"))
                                    }
                                    local?.shiny?.let { url -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        PokedexSprite(url, entry.name, Modifier.size(64.dp)); Text(tr("Brilhante","Shiny","Variocolor"))
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
                        }
                        if (tab == 1) {
                            val moves = local?.moves
                            if (moves != null && moves.fastMoves.isEmpty() && moves.chargedMoves.isEmpty()) item { Text(tr("Golpes indisponíveis.","Moves unavailable.","Ataques no disponibles.")) }
                            listOf(tr("Rápidos","Fast","Rápidos") to moves?.fastMoves.orEmpty(), tr("Carregados","Charged","Cargados") to moves?.chargedMoves.orEmpty()).forEach { (title, list) ->
                                item { PokedexDetailsSection(title) {
                                    list.forEach { move ->
                                    val detail = local?.moveStats?.get(pokedexIdentity(move.name))
                                    if (detail != null) MainMoveCard(detail, language, moveEffects[detail.moveId].orEmpty(),
                                        extraContent = if (move.legacy) ({ Text(tr("${configuredLegacyMoveSymbol()} Golpe legado/especial", "${configuredLegacyMoveSymbol()} Legacy/special move", "${configuredLegacyMoveSymbol()} Ataque legado/especial"), style = MaterialTheme.typography.labelSmall) }) else null)
                                    else PokedexDetailsSection(move.localizedName + if (move.legacy) " ${configuredLegacyMoveSymbol()}" else "") {
                                        Text(tr("Dados de combate indisponíveis.", "Combat data unavailable.", "Datos de combate no disponibles."))
                                    }
                                    }
                                } }
                            }
                            item { PokedexMovesetCard(local, leagues.toMap(), onlineLoading) }
                        }
                        if (tab == 2) {
                            listOf(500,1500,2500,10000).forEach { cap -> item { PokedexDetailsSection(
                                title = when (cap) {
                                    500 -> tr("Copinha (500 PC)", "Little Cup (500 CP)", "Copa Chica (500 PC)")
                                    1500 -> tr("Grande Liga (1.500 PC)", "Great League (1,500 CP)", "Liga Super Ball (1500 PC)")
                                    2500 -> tr("Ultra Liga (2.500 PC)", "Ultra League (2,500 CP)", "Liga Ultra Ball (2500 PC)")
                                    else -> tr("Liga Mestra (PC ilimitado)", "Master League (unlimited CP)", "Liga Master Ball (PC ilimitado)")
                                },
                                iconAsset = "collection/" + when (cap) { 500 -> "little.png"; 1500 -> "great.png"; 2500 -> "ultra.png"; else -> "master.png" }
                            ) {
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
                                profile?.optString("speciesId")?.let { id -> TextButton(onClick = { openExternalUrl(context, "https://pvpoke.com/rankings/${if (cap == 500) "little" else "all"}/$cap/overall/$id/") }) { Text(tr("Ver no PvPoke","View on PvPoke","Ver en PvPoke")) } }
                            } } }
                            item { PokedexDetailsSection(tr("IV Master · família","Master IV · family","IV Master · familia")) {
                                local?.let { d ->
                                    Text(d.masterFamily)
                                    if (d.master.isEmpty()) Text(tr("Sem regra cadastrada para esta família/forma.","No rule registered for this family/form.","Sin regla registrada para esta familia/forma."))
                                    d.master.forEach { (percent, iv) -> Text("$percent%: ${iv.first}/${iv.second}/${iv.third}") }
                                }
                            } }
                        }
                        if (tab == 3) item { PokedexInformationCard(entry, local?.info, local?.candyColors?.get(entry.name)) }
                        if (tab == 4) item { PokedexSizeCard(local?.info, local?.sprite) }
                        if (tab == 5) {
                            items(costumes.chunked(2), key = { it.first().url }) { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    row.forEach { costume ->
                                        PokedexCostumeCard(costume, Modifier.weight(1f))
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
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
private fun PokedexDetailsSection(title: String, iconAsset: String? = null, content: @Composable ColumnScope.() -> Unit) {
    AppSectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                iconAsset?.let { AssetImageIcon(it, null, Modifier.size(30.dp)) }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}
