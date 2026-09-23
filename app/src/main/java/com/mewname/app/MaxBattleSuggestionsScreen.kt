package com.mewname.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mewname.app.domain.*
import com.mewname.app.domain.BattleAdvice
import com.mewname.app.domain.BattleAdvisor
import com.mewname.app.domain.BattleSuggestionEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MaxBattleSuggestionsScreen(
    advice: BattleAdvice,
    showLogAction: Boolean,
    onCopy: (String) -> Unit,
    onExportLog: () -> Unit,
    onClose: () -> Unit,
    bossSpriteId: String? = null,
    bossTier: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    var selectedTab by rememberSaveable(advice.bossName) { mutableStateOf(0) }
    var copied by remember(selectedTab) { mutableStateOf(false) }
    val id = remember(bossSpriteId, advice.bossName) { maxPortraitId(bossSpriteId ?: advice.bossName) }
    val repository = remember(context) { MaxCounterRepository(context.applicationContext) }
    val role = MaxRole.entries[selectedTab]
    var refresh by remember { mutableStateOf(0) }
    var tier by remember(id, bossTier) { mutableStateOf(bossTier) }
    var report by remember(id, bossTier, role) { mutableStateOf<RaidReport?>(null) }
    var loading by remember(id, bossTier, role) { mutableStateOf(true) }
    var failed by remember(id, bossTier, role) { mutableStateOf(false) }
    var metadata by remember { mutableStateOf<RaidMetadata?>(null) }
    var moveNames by remember(language) { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(language) {
        withContext(Dispatchers.IO) {
            val meta = repository.metadata()
            val translations = GameTextRepository.moveTranslations(context.applicationContext, language)
            val names = listOf(AssetPaths.FAST_MOVES, AssetPaths.CHARGED_MOVES).flatMap { path ->
                val array = org.json.JSONArray(context.assets.open(path).bufferedReader().use { it.readText() })
                (0 until array.length()).map { index -> array.getJSONObject(index).let {
                    raidKey(it.getString("name")) to (translations[it.getInt("move_id")] ?: it.getString("name"))
                } }
            }.toMap()
            withContext(Dispatchers.Main) { metadata = meta; moveNames = names }
        }
    }
    LaunchedEffect(id, bossTier, role, refresh) {
        loading = true; failed = false
        try {
            val resolved = bossTier ?: withContext(Dispatchers.IO) { repository.tier(id) }
            tier = resolved
            requireNotNull(resolved) { "Max tier unavailable" }
            val cached = withContext(Dispatchers.IO) { repository.cached(id, resolved, role) }
            report = cached
            if (cached == null || refresh > 0) {
                report = withContext(Dispatchers.IO) { repository.refresh(id, resolved, role) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    val displayed = report?.counters.orEmpty()
    val localizedFilter by produceState("", displayed, language, metadata) {
        value = ""
        value = withContext(Dispatchers.IO) {
            val entries = displayed.map { counter ->
                val pokemon = metadata?.pokemon?.get(counter.id)
                BattleSuggestionEntry(raidName(counter.id), pokemon?.types.orEmpty(),
                    searchTerms = listOf(pokemon?.dex?.takeIf { it > 0 }?.toString()
                        ?: counter.id.substringBefore("_GIGANTAMAX").removeSuffix("_FORM").lowercase().replace('_', ' ')))
            }
            if (entries.isEmpty()) "" else BattleAdvisor.copyTextFor(context, advice.mode, entries)
        }
    }
    if (androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current != null) {
        androidx.activity.compose.BackHandler(onBack = onClose)
    }
    LaunchedEffect(copied) { if (copied) { delay(1600); copied = false } }
    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text(tr("Detalhes da Batalha Max", "Max Battle details", "Detalles del Combate Max")) },
                actions = { TextButton(onClick = { refresh++ }, enabled = !loading) { Text(tr("Atualizar", "Refresh", "Actualizar")) } },
                navigationIcon = { IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, tr("Fechar", "Close", "Cerrar"))
                } }
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppActionButton(
                    onClick = { onCopy(localizedFilter); copied = true },
                    enabled = localizedFilter.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text(if (copied) tr("Copiado!", "Copied!", "¡Copiado!") else tr("Copiar filtro", "Copy filter", "Copiar filtro")) }
                if (showLogAction) AppActionButton(onClick = onExportLog, secondary = true) { Text("Log") }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .background(Brush.verticalGradient(LocalAppAppearance.current.background)).padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AppSectionCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MaxPokemonPortrait(bossSpriteId ?: advice.bossName, Modifier.size(72.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(advice.bossName ?: tr("Chefe não identificado", "Boss not identified", "Jefe no identificado"),
                                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                MaxTypeGroup(tr("Tipos", "Types", "Tipos"), advice.bossTypes,
                                    tr("Não identificados", "Not identified", "No identificados"))
                            }
                        }
                        MaxBossDetails(bossSpriteId ?: advice.bossName)
                    }
                }
            }
            item {
                AppPillTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp, divider = {}) {
                    listOf(tr("Ataque", "Attack", "Ataque"), tr("Escudo", "Guard", "Escudo"),
                        tr("Vida/Cura", "HP/Healing", "Vida/Cura")).forEachIndexed { index, label ->
                        AppPillTab(selected = selectedTab == index, onClick = { selectedTab = index },
                            text = { Text(label) })
                    }
                }
            }
            item {
                Text(tr("Pokémon sugeridos", "Suggested Pokémon", "Pokémon sugeridos"),
                    style = MaterialTheme.typography.titleMedium)
                Text(tr("Pokébattler · Nível 40 · Sem clima · Golpes aleatórios",
                    "Pokébattler · Level 40 · No weather · Random moves",
                    "Pokébattler · Nivel 40 · Sin clima · Ataques aleatorios"),
                    style = MaterialTheme.typography.bodySmall)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Text(tr("Não foi possível atualizar. Exibindo dados salvos, quando disponíveis.",
                    "Could not update. Showing saved data when available.",
                    "No se pudo actualizar. Mostrando datos guardados cuando estén disponibles."))
            }
            if (!loading && displayed.isEmpty()) item {
                Text(tr("Sem dados para esta função.", "No data for this role.", "Sin datos para esta función."))
            }
            itemsIndexed(displayed, key = { index, entry -> selectedTab.toString() + "|" + index + "|" + entry.id }) { index, entry ->
                AppSectionCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text((index + 1).toString(), Modifier.padding(end = 8.dp))
                        MaxPokemonPortrait(entry.id, Modifier.size(56.dp), offline = false)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(raidName(entry.id), style = MaterialTheme.typography.titleSmall)
                            listOf(entry.fast, entry.charged).filter { it.id !in listOf("NONE", "MOVE_NONE", "") }.forEach { move ->
                                RaidMoveRow(move, moveNames[raidKey(move.id)] ?: raidName(move.id.removeSuffix("_FAST")))
                            }
                        }
                    }
                }
            }
            item {
                report?.let {
                    Text("Pokébattler · " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                        .format(java.util.Date(it.fetchedAt)), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = {
                    val url = if (id.isBlank()) "https://www.pokebattler.com/max"
                        else maxBossUrl(id) + (tier?.let { "/" + it } ?: "") + "?sort=" + role.sort
                    openExternalUrl(context, url)
                }) { Text(tr("Abrir Pokébattler ↗", "Open Pokébattler ↗", "Abrir Pokébattler ↗")) }
            }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaxTypeGroup(title: String, types: List<String>, emptyText: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (types.isEmpty()) {
            Text(emptyText, style = MaterialTheme.typography.bodySmall)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                types.forEach { type -> TypeIcon(type = type, language = appLanguage()) }
            }
        }
    }
}
