package com.mewname.app

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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mewname.app.domain.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

@Composable
internal fun PokedexDetailsWindow(initial: PokedexCatalogEntry, entries: List<PokedexCatalogEntry>, onClose: () -> Unit) {
    val context = LocalContext.current
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    var selectedName by rememberSaveable(initial.name) { mutableStateOf(initial.name) }
    val entry = entries.firstOrNull { it.name == selectedName } ?: initial
    val repository = remember { PokedexDetailsRepository(context.applicationContext) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var local by remember(entry.name, language) { mutableStateOf<PokedexLocalDetails?>(null) }
    var localFailed by remember(entry.name, language) { mutableStateOf(false) }
    var profile by remember(entry.name) { mutableStateOf<JSONObject?>(null) }
    var profileSnapshot by remember(entry.name) { mutableStateOf<PokedexOnlineSnapshot?>(null) }
    var onlineLoading by remember(entry.name) { mutableStateOf(true) }
    var onlineError by remember(entry.name) { mutableStateOf(false) }
    val leagues = remember(entry.name) { mutableStateMapOf<Int, PokedexLeagueDetails>() }
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
    val variants = entries.filter { it.number == entry.number }.distinctBy { it.name }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().background(Brush.verticalGradient(LocalAppAppearance.current.background)).safeDrawingPadding()) {
                AppModalHeader("#${entry.number} · ${entry.name}", onClose)
                if (variants.size > 1) LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(variants, key = { it.name }) { form ->
                        AppChoiceChip(form.name == entry.name, { selectedName = form.name }, { Text(form.name) })
                    }
                }
                val tabs = listOf(tr("Geral","Overview","General"), tr("Golpes","Moves","Ataques"), "PvP", tr("Fontes","Sources","Fuentes"))
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tabs.forEachIndexed { i, title -> AppChoiceChip(tab == i, { tab = i }, { Text(title) }, modifier = Modifier.weight(1f)) }
                }
                key(entry.name, tab) {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { types.forEach { TypeBadge(it, language, showAssetIcon = true) } }
                                val stats = local?.stats ?: listOf(entry.attack, entry.defense, entry.stamina)
                                Text("${tr("Ataque","Attack","Ataque")}: ${stats[0] ?: "—"} · ${tr("Defesa","Defense","Defensa")}: ${stats[1] ?: "—"} · Stamina: ${stats[2] ?: "—"}")
                                Text(tr("Atributos base da forma selecionada · base local auditada", "Selected form base stats · audited local data", "Atributos base de la forma seleccionada · datos locales auditados"), style = MaterialTheme.typography.bodySmall)
                                if (local?.shiny != null) Text(tr("Sprite brilhante não confirma disponibilidade no jogo.","A shiny sprite does not confirm availability in game.","El sprite variocolor no confirma disponibilidad en el juego."), style = MaterialTheme.typography.bodySmall)
                            } }
                            item { PokedexDetailsSection(tr("PC por nível","CP by level","PC por nivel")) {
                                Text(tr("Faixa 10/10/10–15/15/15; níveis 40/50/51 mostram o máximo 100%.","10/10/10–15/15/15 range; levels 40/50/51 show the 100% maximum.","Rango 10/10/10–15/15/15; niveles 40/50/51 muestran el máximo 100%."), style = MaterialTheme.typography.bodySmall)
                                local?.cp?.forEach { (level, cp) -> Text("${tr("Nível","Level","Nivel")} $level: " + if (level <= 25) "${cp.first ?: "—"}–${cp.second ?: "—"}" else "${cp.second ?: "—"}") }
                            } }
                            item { PokedexDetailsSection(tr("Fraquezas e resistências","Weaknesses and resistances","Debilidades y resistencias")) {
                                local?.matchups?.filter { it.multiplier != 1.0 }?.forEach {
                                    Text("${localizedTypeLabel(it.attackType, language)} ×${String.format(java.util.Locale.getDefault(), "%.3f", it.multiplier)}")
                                }
                                if (types.isEmpty()) Text(tr("Tipos indisponíveis para esta forma.","Types unavailable for this form.","Tipos no disponibles para esta forma."))
                                val weather = raidWeather(types).map { when(it) {
                                    "sunny" -> tr("Ensolarado","Sunny","Soleado"); "rain" -> tr("Chuva","Rainy","Lluvia")
                                    "partly" -> tr("Parcialmente nublado","Partly cloudy","Parcialmente nublado"); "cloudy" -> tr("Nublado","Cloudy","Nublado")
                                    "wind" -> tr("Vento","Windy","Viento"); "snow" -> tr("Neve","Snow","Nieve"); else -> tr("Neblina","Fog","Niebla")
                                } }
                                if (weather.isNotEmpty()) Text(tr("Clima favorável: ","Weather boost: ","Clima favorable: ") + weather.joinToString())
                            } }
                            item { PokedexDetailsSection(tr("Família e evoluções","Family and evolutions","Familia y evoluciones")) {
                                local?.family?.forEach { member ->
                                    val target = entries.firstOrNull { pokedexIdentity(it.name) == pokedexIdentity(member) }
                                    if (target != null) TextButton(onClick = { selectedName = target.name }) { Text(member) } else Text(member)
                                }
                                Text(tr("Lista da família, incluindo ramificações regionais.","Family members, including regional branches.","Miembros de la familia, incluidas las ramas regionales."), style = MaterialTheme.typography.bodySmall)
                            } }
                            profile?.let { p -> item { PokedexDetailsSection(tr("Informações adicionais · PvPoke","Additional information · PvPoke","Información adicional · PvPoke")) {
                                if (p.has("buddyDistance")) Text(tr("Distância por doce: ","Buddy candy distance: ","Distancia por caramelo: ") + p.optInt("buddyDistance") + " km")
                                if (p.has("thirdMoveCost")) Text(tr("Segundo carregado: ","Second charged move: ","Segundo ataque cargado: ") + p.optInt("thirdMoveCost") + tr(" de poeira estelar"," stardust"," de polvo estelar"))
                                val tags = raidStrings(p.optJSONArray("tags")); if (tags.isNotEmpty()) Text(tags.joinToString(" · "))
                                Text(tr("Valores da fonte; custos podem variar com o estado do Pokémon.","Source values; costs can vary with the Pokémon's status.","Valores de la fuente; los costes pueden variar según el estado del Pokémon."), style = MaterialTheme.typography.bodySmall)
                            } } }
                        }
                        if (tab == 1) {
                            val moves = local?.moves
                            item { Text(tr("★ Golpe legado/especial · dados do catálogo local", "★ Legacy/special move · local catalog data", "★ Ataque legado/especial · catálogo local")) }
                            if (moves != null && moves.fastMoves.isEmpty() && moves.chargedMoves.isEmpty()) item { Text(tr("Golpes indisponíveis.","Moves unavailable.","Ataques no disponibles.")) }
                            listOf(tr("Rápidos","Fast","Rápidos") to moves?.fastMoves.orEmpty(), tr("Carregados","Charged","Cargados") to moves?.chargedMoves.orEmpty()).forEach { (title, list) ->
                                item { Text(title, style = MaterialTheme.typography.titleMedium) }
                                items(list, key = { title + it.name }) { move -> PokedexDetailsSection(move.localizedName + if (move.legacy) " ★" else "") {
                                    local?.moveStats?.get(pokedexIdentity(move.name))?.let { detail ->
                                        TypeBadge(detail.type, language, showAssetIcon = true)
                                        Text("PvE · ${tr("Dano","Damage","Daño")}: ${detail.power ?: "—"} · ${tr("Energia","Energy","Energía")}: ${detail.energyDelta ?: "—"} · ${detail.duration?.div(1000.0) ?: "—"} s")
                                        Text("PvP · ${tr("Dano","Damage","Daño")}: ${detail.pvpPower ?: "—"} · ${tr("Energia","Energy","Energía")}: ${detail.pvpEnergyDelta ?: "—"} · ${detail.pvpTurnDuration ?: "—"} ${tr("turnos","turns","turnos")}")
                                    }
                                } }
                            }
                        }
                        if (tab == 2) {
                            item { Text(tr("Ranking competitivo PvPoke, diferente do ranking de IVs. Liga aberta; formas sombrosas têm rankings próprios.","PvPoke competitive ranking, separate from IV rankings. Open leagues; shadow forms have separate rankings.","Ranking competitivo PvPoke, distinto del ranking de IV. Ligas abiertas; las formas oscuras tienen rankings propios."), style = MaterialTheme.typography.bodySmall) }
                            listOf(1500,2500,10000).forEach { cap -> item { PokedexDetailsSection(when(cap) { 1500 -> "GL · 1500"; 2500 -> "UL · 2500"; else -> "ML" }) {
                                val result = leagues[cap]
                                Text(if (result?.rank != null) "#${result.rank} · ${tr("Nota","Score","Nota")}: ${result.score ?: "—"}" else if (result != null) tr("Sem registro neste ranking.","Not listed in this ranking.","Sin registro en este ranking.") else tr("Dados indisponíveis.","Data unavailable.","Datos no disponibles."))
                                result?.let { r ->
                                    if (r.moves.isNotEmpty()) Text(r.moves.joinToString(" / ") { moveName(it) })
                                    if (r.wins.isNotEmpty()) Text(tr("Confrontos favoráveis: ","Key matchups: ","Enfrentamientos favorables: ") + r.wins.joinToString { raidName(it) })
                                    if (r.counters.isNotEmpty()) Text(tr("Principais ameaças: ","Key counters: ","Principales amenazas: ") + r.counters.joinToString { raidName(it) })
                                    Text("PvPoke · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(r.snapshot.fetchedAt)) + if (r.snapshot.stale) tr(" · dados salvos"," · saved data"," · datos guardados") else "", style = MaterialTheme.typography.bodySmall)
                                }
                                profile?.optString("speciesId")?.let { id -> TextButton(onClick = { openExternalUrl(context, "https://pvpoke.com/rankings/all/$cap/overall/$id/") }) { Text(tr("Ver no PvPoke","View on PvPoke","Ver en PvPoke")) } }
                            } } }
                            item { PokedexDetailsSection(tr("IV Master · família","Master IV · family","IV Master · familia")) {
                                local?.let { d ->
                                    Text(d.masterFamily)
                                    if (d.master.isEmpty()) Text(tr("Sem regra cadastrada para esta família/forma.","No rule registered for this family/form.","Sin regla registrada para esta familia/forma."))
                                    d.master.forEach { (percent, iv) -> Text("$percent%: ${iv.first}/${iv.second}/${iv.third}") }
                                    Text(tr("Regras salvas do app · referência nível 40; não são o ranking competitivo.","Saved app rules · level 40 reference; not competitive rankings.","Reglas guardadas · referencia nivel 40; no son rankings competitivos."), style = MaterialTheme.typography.bodySmall)
                                }
                            } }
                        }
                        if (tab == 3) {
                            item { PokedexDetailsSection(tr("Fontes e consulta","Sources and lookup","Fuentes y consulta")) {
                                Text(tr("Atributos, PC, golpes e família: base local do MewName. Rankings e informações adicionais: PvPoke, cache de 24 horas.","Stats, CP, moves and family: MewName local data. Rankings and additional information: PvPoke, 24-hour cache.","Atributos, PC, ataques y familia: datos locales. Rankings e información adicional: PvPoke, caché de 24 horas."))
                                TextButton(onClick = { openExternalUrl(context, "https://db.pokemongohub.net/pokemon/${entry.number}") }) { Text("GO Hub · #${entry.number}") }
                                Text(tr("GO Hub abre a espécie; selecione a forma no site. Consulta automatizada indisponível.","GO Hub opens the species; choose the form on the site. Automated lookup unavailable.","GO Hub abre la especie; selecciona la forma en el sitio. Consulta automática no disponible."), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { openExternalUrl(context, "https://www.stadiumgaming.gg/rank-checker") }) { Text("Stadium Gaming · " + tr("consultar IVs e selecionar forma","check IVs and select form","consultar IV y seleccionar forma")) }
                                TextButton(onClick = { openExternalUrl(context, "https://pvpoke.com/") }) { Text("PvPoke") }
                                TextButton(onClick = { openExternalUrl(context, "https://www.pokebattler.com/raids") }) { Text("Pokébattler · raids") }
                                profileSnapshot?.let { Text(tr("Consulta PvPoke: ","PvPoke fetched: ","Consulta PvPoke: ") + DateFormat.getDateTimeInstance().format(Date(it.fetchedAt)), style = MaterialTheme.typography.bodySmall) }
                            } }
                        }
                        if (onlineLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        if (onlineError || profileSnapshot?.stale == true) item { Text(tr("Não foi possível atualizar parte dos dados online. Dados locais e salvos continuam disponíveis.","Some online data could not be refreshed. Local and saved data remain available.","No se pudieron actualizar algunos datos online. Los datos locales y guardados siguen disponibles.")) }
                        if (!onlineLoading && profileSnapshot != null && profile == null) item { Text(tr("Forma sem correspondência inequívoca no PvPoke; nenhum ranking de outra forma foi usado.","No unambiguous PvPoke form match; no other form's ranking was substituted.","Forma sin correspondencia inequívoca en PvPoke; no se sustituyó por otra forma.")) }
                        item { AppSecondaryButton(onClick = { refresh++ }, enabled = !onlineLoading, modifier = Modifier.fillMaxWidth()) { Text(tr("Atualizar dados online","Refresh online data","Actualizar datos online")) } }
                    }
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
