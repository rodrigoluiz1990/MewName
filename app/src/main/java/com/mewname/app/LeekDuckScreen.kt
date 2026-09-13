package com.mewname.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mewname.app.domain.*
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LeekDuckScreen(section: LeekSection, onBack: () -> Unit, capture: CatalogCapture? = null) {
    val context = LocalContext.current
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    val normalScreenName = when (section) {
        LeekSection.RESEARCH -> tr("Pesquisas de campo", "Field research", "Investigaciones de campo")
        LeekSection.EGGS -> tr("Ovos", "Eggs", "Huevos")
        LeekSection.ROCKET -> tr("Equipe GO Rocket", "Team GO Rocket", "Equipo GO Rocket")
        LeekSection.CODES -> tr("Códigos promocionais", "Promo codes", "Códigos promocionales")
    }
    val updateInAppMessage = tr(
        "Não encontramos informações nos dados salvos. Atualize a tela $normalScreenName no app, fora do modo bolha, e faça uma nova leitura.",
        "No information found in saved data. Update $normalScreenName in the normal app, outside bubble mode, then scan again.",
        "No encontramos información en los datos guardados. Actualiza $normalScreenName en la app, fuera del modo burbuja, y vuelve a leer.")
    val repository = remember { LeekDuckRepository(context.applicationContext) }
    var data by remember(section) { mutableStateOf<LeekCatalog?>(null) }
    var loading by remember(section) { mutableStateOf(true) }
    var error by remember(section) { mutableStateOf(false) }
    var refresh by remember(section) { mutableIntStateOf(0) }
    var search by rememberSaveable(section) { mutableStateOf("") }
    var group by rememberSaveable(section) { mutableStateOf(if (section == LeekSection.CODES) "Active" else "All") }
    var distance by rememberSaveable(section) { mutableStateOf("All") }
    var rocketQuotes by remember(language) { mutableStateOf<List<RocketQuote>>(emptyList()) }
    LaunchedEffect(language, section) {
        if (section == LeekSection.ROCKET) rocketQuotes = withContext(Dispatchers.IO) {
            GameTextRepository.rocketQuotes(context.applicationContext, language)
        }
    }
    fun description(entry: LeekEntry) = if (section == LeekSection.ROCKET)
        translatedRocketQuote(entry.description, entry.title, rocketQuotes) ?: entry.description
        else entry.description
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(60_000) } }
    LaunchedEffect(section, refresh) {
        loading = true; error = false
        try {
            val cached = withContext(Dispatchers.IO) { repository.cached(section) }
            data = cached
            if (capture == null && shouldRefreshLeekCatalog(cached?.fetchedAt, bubble = false, manual = refresh > 0)) {
                data = withContext(Dispatchers.IO) { repository.refresh(section) }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = true }
        finally { loading = false }
    }
    var researchTerms by remember(language) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var researchTitles by remember(section, language) { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(data, section, language, capture) {
        researchTitles = if (section == LeekSection.RESEARCH && data != null) {
            val index = withContext(Dispatchers.IO) { GameTextRepository.researchIndex(context.applicationContext, language) }
            researchTerms = withContext(Dispatchers.IO) { GameTextRepository.researchDisplayTerms(context.applicationContext, language) }
            val entries = data!!.entries
            withContext(Dispatchers.Default) {
                entries.associate { entry -> entry.id to (index.variants(entry.title).firstOrNull() ?: entry.title) }
            }
        } else emptyMap()
    }
    fun entryTitle(entry: LeekEntry) = if (section == LeekSection.RESEARCH)
        researchTitles[entry.id] ?: tr("Carregando pesquisa…", "Loading research…", "Cargando investigación…")
        else leekRocketTitle(entry.title, language)
    fun rewardDisplay(pokemon: LeekPokemon) = if (section == LeekSection.RESEARCH) pokemon.copy(
        name = ResearchDisplayText.reward(pokemon.name, researchTerms, language),
        detail = ResearchDisplayText.detail(pokemon.detail, language)) else pokemon
    val offlineImages = capture != null
    var capturedEntries by remember(capture) { mutableStateOf<List<LeekEntry>?>(null) }
    LaunchedEffect(data, capture) {
        capturedEntries = null
        val catalog = data
        if (capture != null && catalog != null) capturedEntries = withContext(Dispatchers.Default) {
            val quotes = if (section == LeekSection.ROCKET) withContext(Dispatchers.IO) {
                AppLanguage.entries.flatMap { GameTextRepository.rocketQuotes(context.applicationContext, it) }
            } else emptyList()
            val indexes = if (section == LeekSection.RESEARCH) withContext(Dispatchers.IO) {
                AppLanguage.entries.map { GameTextRepository.researchIndex(context.applicationContext, it) }
            } else emptyList()
            CatalogScreenMatcher.matching(capture, catalog, quotes, translations = { title -> indexes.flatMap { it.variants(title) } })
        }
    }
    fun groupLabel(key: String) = when (key) {
        "All" -> tr("Todos", "All", "Todos")
        "Grunts" -> tr("Recrutas", "Grunts", "Reclutas")
        "Leaders" -> tr("Líderes", "Leaders", "Líderes")
        "Active" -> tr("Disponíveis", "Available", "Disponibles")
        "Expired" -> tr("Expirados", "Expired", "Caducados")
        "Gifts" -> tr("Presentes", "Gifts", "Regalos")
        "Routes" -> tr("Rotas", "Routes", "Rutas")
        "Sync" -> tr("Sincroaventura", "Adventure Sync", "Sincroaventura")
        "Event" -> tr("Eventos", "Events", "Eventos")
        "Regular" -> tr("Comuns", "Regular", "Comunes")
        else -> key
    }
    fun eggOrigin(title: String) = when { title.contains("Adventure Sync") -> "Sync"; title.contains("Route") -> "Routes"; title.contains("Friend") -> "Gifts"; else -> "Regular" }
    fun expired(entry: LeekEntry) = entry.expired || entry.expires?.let { it <= now } == true
    val groups = when (section) {
        LeekSection.ROCKET -> listOf("All", "Grunts", "Leaders", "Giovanni")
        LeekSection.EGGS -> listOf("All", "Regular", "Gifts", "Routes", "Sync")
        LeekSection.RESEARCH -> listOf("All", "Regular", "Event")
        LeekSection.CODES -> listOf("All", "Active", "Expired")
    }
    val filtered = (if (capture != null) capturedEntries.orEmpty() else data?.entries.orEmpty()).filter { entry ->
        val matchesGroup = group == "All" || when(section) {
            LeekSection.ROCKET -> entry.group == group
            LeekSection.EGGS -> eggOrigin(entry.title) == group
            LeekSection.RESEARCH -> entry.description == group
            LeekSection.CODES -> if (group == "Expired") expired(entry) else !expired(entry)
        }
        (section != LeekSection.RESEARCH || !expired(entry)) && matchesGroup && (distance == "All" || section != LeekSection.EGGS || entry.title.startsWith("$distance km")) &&
            (search.isBlank() || (section == LeekSection.RESEARCH && ResearchTaskComparison.matches(entryTitle(entry), search)) || (entryTitle(entry) + " " + entry.group + " " + entry.title + " " + entry.description + " " + description(entry) + " " + entry.code + " " +
                (entry.pokemon + entry.slots.flatMap { it.pokemon }).joinToString { it.name + " " + rewardDisplay(it).name }).contains(search, true))
    }
    val title = when(section) { LeekSection.ROCKET -> tr("Equipe GO Rocket", "Team GO Rocket", "Equipo GO Rocket")
        LeekSection.EGGS -> tr("Ovos", "Eggs", "Huevos")
        LeekSection.RESEARCH -> tr("Pesquisas de campo", "Field research", "Investigaciones de campo")
        LeekSection.CODES -> tr("Códigos promocionais", "Promo codes", "Códigos promocionales") }
    Scaffold(containerColor = Color.Transparent, topBar = {
        AppTopBar(title = { Text(title) }, navigationIcon = {
            if (capture != null) IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = tr("Fechar", "Close", "Cerrar")) }
            else TextButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
        }, actions = { if (capture == null) IconButton(onClick = { refresh++ }, enabled = !loading) {
            Icon(Icons.Default.Refresh, contentDescription = tr("Atualizar", "Refresh", "Actualizar"))
        } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (capture == null) item {
                AppGlassTextField(search, { search = it }, Modifier.fillMaxWidth(), search = true, singleLine = true,
                    label = { Text(tr("Pesquisar", "Search", "Buscar")) })
            }
            if (capture == null) item { LeekFilters(groups, group, { group = it }, ::groupLabel) }
            if (section == LeekSection.EGGS && capture == null) item {
                LeekFilters(listOf("All") + data?.entries.orEmpty().map { it.title.substringBefore(" km") }.distinct(), distance,
                    { distance = it }) { if(it == "All") groupLabel(it) else "$it km" }
            }
            if (capture != null) item {
                Text(when (section) {
                    LeekSection.EGGS -> tr("Possíveis eclosões pelas distâncias lidas. A origem e a data de obtenção do ovo podem mudar as opções.",
                        "Possible hatches for the distances read. Egg origin and acquisition date may change the pool.",
                        "Posibles eclosiones según las distancias leídas. El origen y la fecha del huevo pueden cambiar las opciones.")
                    LeekSection.ROCKET -> tr("Equipes possíveis para a fala lida. Quando a fala é compartilhada, aparecem os dois recrutas.",
                        "Possible teams for the quote read. Shared quotes show both grunts.",
                        "Equipos posibles según la frase leída. Las frases compartidas muestran ambos reclutas.")
                    else -> tr("Recompensas possíveis para as tarefas lidas, separadas por origem. O evento e o ícone de recompensa podem restringir as opções; não são todas garantidas.",
                        "Possible rewards for the tasks read, separated by source. Event and reward icon may narrow the options; not all are guaranteed.",
                        "Recompensas posibles para las tareas leídas, separadas por origen. El evento y el icono pueden limitar las opciones; no se garantizan todas.")
                }, style = MaterialTheme.typography.bodySmall)
            }
            if (loading || (capture != null && data != null && capturedEntries == null)) item {
                if (capture != null) Text(tr("Consultando dados salvos…", "Reading saved data…", "Consultando datos guardados…"), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (error && capture != null) item { Text(updateInAppMessage) }
            if (error && capture == null) item { AppSectionCard(Modifier.fillMaxWidth()) {
                Text(tr("Não foi possível atualizar os dados.", "Could not refresh the data.", "No se pudieron actualizar los datos."), Modifier.padding(14.dp))
                TextButton(onClick = { refresh++ }, enabled = !loading) { Text(tr("Tentar novamente", "Retry", "Reintentar")) }
            } }
            if (capture != null && !loading && data == null && !error) item {
                Text(updateInAppMessage)
            }
            if (!loading && (capture == null || data != null) && (capture == null || data == null || capturedEntries != null) && filtered.isEmpty()) item { Text(if (capture == null) tr("Nenhum resultado disponível.", "No results available.", "No hay resultados disponibles.") else updateInAppMessage) }
            filtered.forEach { entry ->
              item(key = entry.id) {
                AppSectionCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row {
                            if (entry.image.isNotBlank()) AsyncImage(leekImageRequest(entry.image, offlineImages), null, Modifier.size(56.dp))
                            Text(if(section == LeekSection.EGGS) entry.title.substringBefore(" km") + " km · " + groupLabel(eggOrigin(entry.title)) else entryTitle(entry),
                                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        }
                        if (section == LeekSection.RESEARCH) Text(ResearchDisplayText.group(entry.group, language) + " · " + if (entry.description == "Event") tr("Evento", "Event", "Evento") else tr("Comum", "Regular", "Común"), style = MaterialTheme.typography.labelMedium)
                        if (section != LeekSection.CODES && section != LeekSection.RESEARCH && entry.description.isNotBlank()) Text(description(entry), style = MaterialTheme.typography.bodySmall)
                        if (section == LeekSection.ROCKET && capture != null) RocketCounterBlock(entry)
                        for (slot in entry.slots) {
                            Text("${slot.position}" + if(slot.encounter) " · " + tr("Pode ser capturado", "Catchable", "Se puede capturar") else "", style = MaterialTheme.typography.labelLarge)
                            for (pokemon in slot.pokemon) LeekPokemonRow(pokemon, weakness = true, offline = offlineImages)
                        }
                        if (section != LeekSection.EGGS && section != LeekSection.RESEARCH) for (pokemon in entry.pokemon) LeekPokemonRow(rewardDisplay(pokemon), offline = offlineImages)
                        if (entry.code.isNotBlank()) {
                            Text(entry.code, style = MaterialTheme.typography.titleMedium)
                            Text(when {
                                expired(entry) -> tr("Expirado", "Expired", "Caducado")
                                entry.expires != null -> tr("Validade: ", "Expires: ", "Vence: ") + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.expires))
                                else -> tr("Validade não informada", "Expiration unknown", "Vencimiento desconocido")
                            }, style = MaterialTheme.typography.bodySmall)
                            val clipboard = LocalClipboardManager.current
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppActionButton(onClick = { clipboard.setText(AnnotatedString(entry.code)) }, secondary = true) { Text(tr("Copiar", "Copy", "Copiar")) }
                                AppActionButton(onClick = { openExternalUrl(context, "https://store.pokemongo.com/offer-redemption?passcode=" + android.net.Uri.encode(entry.code)) }, enabled = !expired(entry)) { Text(tr("Resgatar", "Redeem", "Canjear")) }
                            }
                        }
                    }
                }
            }
                if (section == LeekSection.EGGS || section == LeekSection.RESEARCH) items(entry.pokemon) { pokemon ->
                    AppSectionCard(Modifier.fillMaxWidth()) {
                        Box(Modifier.padding(12.dp)) { LeekPokemonRow(rewardDisplay(pokemon), offline = offlineImages) }
                    }
                }
            }
            item {
                TextButton(onClick = { openExternalUrl(context, section.url) }) { Text(tr("Fonte: ", "Source: ", "Fuente: ") + "Leek Duck ↗") }
                data?.let { catalog ->
                    if (catalog.sourceUpdated.isNotBlank()) Text(tr("Atualização da fonte: ", "Source updated: ", "Fuente actualizada: ") + catalog.sourceUpdated, style = MaterialTheme.typography.bodySmall)
                    Text(tr("Última consulta: ", "Last checked: ", "Última consulta: ") + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(catalog.fetchedAt)), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun LeekFilters(values: List<String>, selected: String, onSelect: (String) -> Unit, label: (String) -> String) {
    AppPillTabRow(selectedTabIndex = values.indexOf(selected).coerceAtLeast(0)) {
        values.forEach { value -> AppPillTab(selected == value, { onSelect(value) }, text = { Text(label(value)) }) }
    }
}

@Composable
private fun LeekPokemonRow(pokemon: LeekPokemon, weakness: Boolean = false, offline: Boolean = false) {
    val language = appLanguage()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AsyncImage(leekImageRequest(pokemon.image, offline), null, Modifier.size(48.dp))
        Column(Modifier.weight(1f)) {
            Text(pokemon.name + if (pokemon.shiny) " ✨" else "")
            if (pokemon.detail.isNotBlank()) Text((if (weakness) lt(language, "Fraquezas: ", "Weaknesses: ", "Debilidades: ") else "") + (if (weakness) Regex("[A-Za-z]+").replace(pokemon.detail) { localizedTypeLabel(it.value, language) } else pokemon.detail), style = MaterialTheme.typography.bodySmall)
            pokemon.rarity?.let { Text(lt(language, "Raridade: ", "Rarity: ", "Rareza: ") + "$it/5", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun leekRocketTitle(title: String, language: AppLanguage): String {
    val match = Regex("(.+)-type (Male|Female) Grunt").matchEntire(title) ?: return title
    val type = localizedTypeLabel(match.groupValues[1], language)
    val gender = if (match.groupValues[2] == "Male") "♂" else "♀"
    return lt(language, "Recruta", "Grunt", "Recluta") + " $gender · $type"
}
@Composable
private fun leekImageRequest(url: String, offline: Boolean): coil.request.ImageRequest {
    val context = LocalContext.current
    return remember(context, url, offline) {
        coil.request.ImageRequest.Builder(context).data(url)
            .networkCachePolicy(if (offline) coil.request.CachePolicy.DISABLED else coil.request.CachePolicy.ENABLED)
            .build()
    }
}
@Composable
private fun RocketCounterBlock(entry: LeekEntry) {
    val language = appLanguage()
    val clipboard = LocalClipboardManager.current
    val counters = remember(entry) { RocketCounterAdvisor.suggest(entry) }
    val filter = remember(counters, language) {
        RocketCounterAdvisor.filter(counters) { localizedTypeLabel(it, language) }
    }
    var copied by remember(entry, language) { mutableStateOf(false) }
    AppSectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(lt(language, "Bons contra esta equipe", "Good against this team", "Buenos contra este equipo"),
                style = MaterialTheme.typography.titleSmall)
            if (counters.isEmpty()) Text(lt(language, "Fraquezas indisponíveis nos dados salvos.",
                "Weaknesses unavailable in saved data.", "Debilidades no disponibles en los datos guardados."))
            else {
                counters.forEach { counter ->
                    Text(localizedTypeLabel(counter.attackType, language) + ": " + counter.examples.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(lt(language, "Exemplos com golpes desses tipos. O filtro encontra golpes já aprendidos; confira o PC e a equipe adversária.",
                    "Examples using these attack types. The filter finds learned attacks; check CP and the opposing team.",
                    "Ejemplos con ataques de estos tipos. El filtro busca ataques aprendidos; revisa los PC y el equipo rival."),
                    style = MaterialTheme.typography.bodySmall)
                Text(filter, style = MaterialTheme.typography.bodySmall)
                AppActionButton(onClick = { clipboard.setText(AnnotatedString(filter)); copied = true }, secondary = true) {
                    Text(if (copied) lt(language, "Copiado", "Copied", "Copiado")
                        else lt(language, "Copiar filtro de golpes", "Copy attack filter", "Copiar filtro de ataques"))
                }
            }
        }
    }
}