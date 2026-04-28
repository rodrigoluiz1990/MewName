package com.mewname.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mewname.app.domain.AppLanguage
import com.mewname.app.domain.BattleAdvisor
import com.mewname.app.domain.BattleMode
import com.mewname.app.domain.BattleSuggestionEntry
import com.mewname.app.domain.GameInfoRepository
import com.mewname.app.domain.GameTextRepository
import com.mewname.app.domain.MoveCatalogEntry
import com.mewname.app.domain.MoveCategory
import com.mewname.app.domain.PokedexCatalogEntry
import com.mewname.app.domain.RaidHistoryCategory
import com.mewname.app.domain.TypeMatchupEntry
import com.mewname.app.model.EvolutionFlag
import com.mewname.app.model.PokemonScreenData
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalysisTabsSection(
    uiState: UiState,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = appLanguage()
    val parsed = uiState.parsedData
    val results = uiState.generatedResults
    if (parsed == null && results.isEmpty()) return

    val suggestedTab = t(language, "Nome sugerido", "Suggested name", "Nombre sugerido")
    val detectedTab = t(language, "Dados detectados", "Detected data", "Datos detectados")
    val tabs = buildList {
        if (results.isNotEmpty()) add(suggestedTab)
        if (parsed != null) add(detectedTab)
    }
    var selectedTab by remember(tabs) { mutableStateOf(0) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = index == selectedTab,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (tabs.getOrNull(selectedTab)) {
                suggestedTab -> {
                    results.forEach { result ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    result.configName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    result.generatedName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                detectedTab -> {
                    if (parsed != null) {
                        DetectedDataSummary(parsed)
                    }
                }
            }

            Button(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Text(t(language, "Limpar resultados", "Clear results", "Limpiar resultados"))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetectedDataSummary(data: PokemonScreenData) {
    val language = appLanguage()
    val flags = remember(data, language) {
        buildList {
            if (data.isFavorite) add(t(language, "Favorito", "Favorite", "Favorito"))
            if (data.isLucky) add(t(language, "Sortudo", "Lucky", "Suertudo"))
            if (data.isShadow) add(t(language, "Sombrio", "Shadow", "Oscuro"))
            if (data.isPurified) add(t(language, "Purificado", "Purified", "Purificado"))
            if (data.hasSpecialBackground) add(t(language, "Fundo especial", "Special background", "Fondo especial"))
            if (data.hasAdventureEffect) add(t(language, "Efeito aventura", "Adventure effect", "Efecto aventura"))
            if (data.hasLegacyMove) add(t(language, "Legado", "Legacy", "Legado"))
            if (data.evolutionFlags.isNotEmpty()) addAll(data.evolutionFlags.map { it.name })
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LabeledValueCard(
            title = data.pokemonName ?: "Pokemon",
            pairs = listOf(
                "CP" to (data.cp?.toString() ?: "-"),
                t(language, "Nivel", "Level", "Nivel") to (data.level?.toString() ?: "-"),
                "IV %" to (data.ivPercent?.toString() ?: "-"),
                "IV A/D/S" to (
                    listOfNotNull(data.attIv, data.defIv, data.staIv)
                        .takeIf { it.size == 3 }
                        ?.joinToString("/") ?: "-"
                    ),
                "PvP" to listOfNotNull(data.pvpLeague?.name, data.pvpRank?.let { "#$it" }).joinToString(" ").ifBlank { "-" },
                t(language, "Tipo", "Type", "Tipo") to listOfNotNull(data.type1, data.type2).joinToString("/").ifBlank { "-" },
                t(language, "Forma", "Form", "Forma") to (data.uniqueForm ?: "-"),
                t(language, "Legado", "Legacy", "Legado") to (data.legacyDebugInfo?.matchedLegacyMove ?: "-")
            )
        )

        if (flags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                flags.forEach { flag ->
                    AssistChip(onClick = {}, label = { Text(flag) })
                }
            }
        }
    }
}

@Composable
private fun LabeledValueCard(
    title: String,
    pairs: List<Pair<String, String>>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            pairs.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value.ifBlank { "-" }, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TypesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val language = appLanguage()
    val chart = remember { GameInfoRepository.loadTypeEffectiveness(context) }
    val types = remember(chart) { chart.keys.sorted() }
    val selectedTypes = remember { mutableStateListOf<String>() }
    val matchup = remember(selectedTypes.toList()) {
        GameInfoRepository.matchupAgainst(context, selectedTypes.toList())
    }

    SimpleToolScreen(
        title = t(language, "Tipos", "Types", "Tipos"),
        onBack = onBack,
        helpTitle = t(language, "Como usar Tipos", "How to use Types", "Como usar Tipos"),
        helpText = t(
            language,
            "Selecione um ou dois tipos para ver fraquezas, resistencias e resistencias duplas. Use esta tela para decidir quais ataques funcionam melhor contra um chefe ou quais tipos resistem melhor a uma batalha.",
            "Select one or two types to see weaknesses, resistances, and double resistances. Use this screen to decide which attacks work best against a boss or which typings resist a battle better.",
            "Selecciona uno o dos tipos para ver debilidades, resistencias y resistencias dobles. Usa esta pantalla para decidir que ataques funcionan mejor contra un jefe o que tipos resisten mejor una batalla."
        )
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    t(
                        language,
                        "Calculadora de fraquezas e resistencias",
                        "Weakness and resistance calculator",
                        "Calculadora de debilidades y resistencias"
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    t(
                        language,
                        "Escolha ate dois tipos defensivos para montar a leitura no estilo PokeGenie.",
                        "Choose up to two defending types for a PokeGenie-style matchup view.",
                        "Elige hasta dos tipos defensivos para una vista estilo PokeGenie."
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (selectedTypes.isEmpty()) {
                    Text(
                        t(language, "Nenhum tipo selecionado", "No type selected", "Ningun tipo seleccionado"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        selectedTypes.forEach { type ->
                            TypeBadge(type = type, language = language)
                        }
                    }
                }
            }
        }

        TypeSelector(types = types, selectedTypes = selectedTypes, maxSelection = 2, language = language, verticalSpacing = 4.dp)
        MatchupSection(matchup = matchup, language = language)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MovesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val language = appLanguage()
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val catalogState = produceState<List<MoveCatalogEntry>>(initialValue = emptyList(), context) {
        val appContext = context.applicationContext
        withContext(Dispatchers.Default) {
            GameInfoRepository.loadMoveCatalogProgressive(appContext) { partial ->
                withContext(Dispatchers.Main) {
                    value = partial
                }
            }
        }
    }
    val catalog = catalogState.value
    val isLoading = catalog.isEmpty()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<MoveCategory?>(null) }
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }
    var visibleCount by rememberSaveable(query, selectedCategory, selectedType) { mutableStateOf(80) }
    val types = remember(catalog) { catalog.map { it.type }.distinct().sorted() }
    val normalizedQuery = remember(query) { normalizeSearch(query) }
    val indexedCatalog = remember(catalog) {
        catalog.map { entry ->
            IndexedMoveEntry(
                entry = entry,
                searchIndex = listOf(entry.name, entry.namePt, entry.nameEs)
                    .filterNotNull()
                    .joinToString(" ")
                    .let(::normalizeSearch)
            )
        }
    }
    val filtered = remember(indexedCatalog, normalizedQuery, selectedCategory, selectedType) {
        indexedCatalog.filter { indexed ->
            val entry = indexed.entry
            (selectedCategory == null || entry.category == selectedCategory) &&
                (selectedType == null || entry.type == selectedType) &&
                (
                    normalizedQuery.isBlank() ||
                        indexed.searchIndex.contains(normalizedQuery)
                    )
        }.map { it.entry }
    }
    val visibleEntries by remember(filtered, visibleCount) {
        derivedStateOf { filtered.take(visibleCount) }
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t(language, "Ataques", "Moves", "Ataques")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        UnownQuestionIcon(modifier = Modifier.size(24.dp), contentDescription = "Ajuda")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(t(language, "Buscar ataque", "Search move", "Buscar ataque")) },
                    singleLine = true
                )
            }
            item {
                MoveCategoryFilter(
                    selectedCategory = selectedCategory,
                    onSelect = { selectedCategory = if (selectedCategory == it) null else it },
                    language = language
                )
            }
            item {
                TypeSelectorSingle(
                    types = types,
                    selectedType = selectedType,
                    onSelect = { selectedType = it },
                    language = language,
                    showAllOption = false
                )
            }
            item {
                Text(
                    if (isLoading) {
                        t(language, "Carregando ataques...", "Loading moves...", "Cargando ataques...")
                    } else {
                        t(
                            language,
                            "${visibleEntries.size} de ${filtered.size} ataques",
                            "${visibleEntries.size} of ${filtered.size} moves",
                            "${visibleEntries.size} de ${filtered.size} ataques"
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isLoading) {
                item {
                    InlineLoadingRow(t(language, "Preparando primeiros resultados", "Preparing first results", "Preparando primeros resultados"))
                }
            }
            items(visibleEntries, key = { it.moveId ?: it.name.hashCode() }) { entry ->
                MoveCard(entry = entry, language = language)
            }
            if (filtered.size > visibleEntries.size) {
                item {
                    Button(
                        onClick = { visibleCount += 80 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(t(language, "Carregar mais", "Load more", "Cargar mas"))
                    }
                }
            }
        }
    }
    if (showHelp) {
        ToolHelpDialog(
            title = t(language, "Como usar Ataques", "How to use Moves", "Como usar Ataques"),
            body = t(
                language,
                "Pesquise ataques por nome, filtre por tipo ou por categoria rapido/carregado. Cada linha mostra o nome no idioma selecionado, o tipo e os dados de ginasio e PvP para comparar dano, energia e duracao.",
                "Search moves by name, filter by type or by fast/charged category. Each row shows the selected-language name, type, and Gym/PvP data so you can compare damage, energy, and duration.",
                "Busca ataques por nombre, filtra por tipo o categoria rapido/cargado. Cada fila muestra el nombre en el idioma seleccionado, el tipo y los datos de gimnasio y PvP para comparar dano, energia y duracion."
            ),
            onDismiss = { showHelp = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokedexScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val language = appLanguage()
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val pokedexState = produceState<List<PokedexCatalogEntry>>(initialValue = emptyList(), context) {
        val appContext = context.applicationContext
        withContext(Dispatchers.Default) {
            GameInfoRepository.loadPokedexCatalogProgressive(appContext) { partial ->
                withContext(Dispatchers.Main) {
                    value = partial
                }
            }
        }
    }
    val pokedex = pokedexState.value
    val isLoading = pokedex.isEmpty()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }
    var visibleCount by rememberSaveable(query, selectedType) { mutableStateOf(60) }
    val types = remember(pokedex) { pokedex.flatMap { listOfNotNull(it.type1, it.type2) }.distinct().sorted() }
    val normalizedQuery = remember(query) { normalizeSearch(query) }
    val filtered = remember(pokedex, selectedType, normalizedQuery, language) {
        pokedex.filter { entry ->
            (selectedType == null || entry.type1 == selectedType || entry.type2 == selectedType) &&
                (
                    normalizedQuery.isBlank() ||
                        normalizeSearch(entry.name).contains(normalizedQuery) ||
                        normalizeSearch(entry.namePt.orEmpty()).contains(normalizedQuery) ||
                        normalizeSearch(entry.nameEs.orEmpty()).contains(normalizedQuery) ||
                        entry.number.toString().contains(normalizedQuery) ||
                        entry.aliases.any { normalizeSearch(it).contains(normalizedQuery) }
                    )
        }
    }
    val visibleEntries = remember(filtered, visibleCount) { filtered.take(visibleCount) }

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pokedex") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        UnownQuestionIcon(modifier = Modifier.size(24.dp), contentDescription = "Ajuda")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            t(language, "Consulta rapida da dex", "Quick dex lookup", "Consulta rapida de la dex"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            t(
                                language,
                                "Lista carregada em lotes para manter a tela leve.",
                                "Entries are loaded in batches to keep the screen smooth.",
                                "La lista se carga por lotes para mantener la pantalla fluida."
                            )
                        )
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(t(language, "Buscar Pokemon", "Search Pokemon", "Buscar Pokemon")) },
                            singleLine = true
                        )
                        TypeSelectorSingle(types = types, selectedType = selectedType, onSelect = { selectedType = it }, language = language)
                        Text(
                            if (isLoading) {
                                t(language, "Carregando primeiros Pokemon...", "Loading first Pokemon...", "Cargando primeros Pokemon...")
                            } else {
                                "${visibleEntries.size}/${filtered.size}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (isLoading) {
                item {
                    InlineLoadingRow(t(language, "Montando Pokedex em lotes", "Building Pokedex in batches", "Montando Pokedex por lotes"))
                }
            }
            items(visibleEntries, key = { "${it.number}-${it.name}" }) { entry ->
                PokedexCard(entry = entry, language = language)
            }
            if (visibleCount < filtered.size) {
                item {
                    Button(
                        onClick = { visibleCount += 60 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(t(language, "Carregar mais", "Load more", "Cargar mas"))
                    }
                }
            }
        }
    }
    if (showHelp) {
        ToolHelpDialog(
            title = t(language, "Como usar Pokedex", "How to use Pokedex", "Como usar Pokedex"),
            body = t(
                language,
                "Use a busca para encontrar Pokemon por nome, numero ou apelidos do catalogo. O filtro de tipo reduz a lista para Pokemon que tenham aquele tipo. Os cards mostram numero, tipos, atributos base e quantidade de formas conhecidas.",
                "Use search to find Pokemon by name, number, or catalog aliases. The type filter narrows the list to Pokemon that have that type. Cards show number, typing, base stats, and known form count.",
                "Usa la busqueda para encontrar Pokemon por nombre, numero o alias del catalogo. El filtro de tipo reduce la lista a Pokemon con ese tipo. Las tarjetas muestran numero, tipos, estadisticas base y cantidad de formas conocidas."
            ),
            onDismiss = { showHelp = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBuilderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val language = appLanguage()
    var tabIndex by rememberSaveable { mutableStateOf(0) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val tabTitles = listOf(
        t(language, "Pokemon", "Pokemon", "Pokemon"),
        t(language, "Pessoas", "People", "Personas")
    )

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t(language, "Filtros", "Filters", "Filtros")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        UnownQuestionIcon(modifier = Modifier.size(24.dp), contentDescription = "Ajuda")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScrollableTabRow(selectedTabIndex = tabIndex, edgePadding = 0.dp, divider = {}) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(selected = index == tabIndex, onClick = { tabIndex = index }, text = { Text(title) })
                }
            }
            when (tabIndex) {
                0 -> PokemonFilterBuilder(context, language)
                else -> PeopleFilterBuilder(context, language)
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
                confirmButton = {
                    Button(onClick = { showHelp = false }) {
                    Text(t(language, "OK", "OK", "OK"))
                }
            },
            title = { Text(t(language, "Como usar filtros", "How to use filters", "Como usar filtros")) },
            text = {
                Text(
                    t(
                        language,
                        "Toque uma vez para incluir, toque de novo para excluir, e mais uma vez para limpar. Para cada opcao selecionada, escolha se ela entra com & para combinar regras ou com virgula para alternativa de busca.",
                        "Tap once to include, tap again to exclude, and once more to clear. For each selected option, choose & to combine rules or comma for an alternative search.",
                        "Toca una vez para incluir, otra vez para excluir y una vez mas para limpiar. Para cada opcion seleccionada, elige & para combinar reglas o coma para busqueda alternativa."
                    )
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RaidPlannerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val language = appLanguage()
    val raidHistory = remember(context) { GameInfoRepository.loadRaidHistory(context.applicationContext) }
    val pokedexOrder = remember(context) { GameInfoRepository.loadPokemonDexOrder(context.applicationContext) }

    SimpleToolScreen(
        title = t(language, "Raids", "Raids", "Raids"),
        onBack = onBack,
        helpTitle = t(language, "Como usar Raids", "How to use Raids", "Como usar Raids"),
        helpText = t(
            language,
            "Use as abas para navegar por tipo de raid: nivel 5, Mega, Super Mega, Sombrosas, Gigantamax e Dynamax. A lista mostra os Pokemon que ja apareceram em cada categoria, mantendo a aparicao mais recente de cada um. Use o campo de busca para localizar um chefe e toque no nome para abrir a pagina correspondente no Pokebattler.",
            "Use the tabs to browse by raid type: tier 5, Mega, Super Mega, Shadow, Gigantamax, and Dynamax. The list shows Pokemon that have appeared in each category, keeping each one's most recent appearance. Use the search field to find a boss and tap the name to open its Pokebattler page.",
            "Usa las pestanas para navegar por tipo de raid: nivel 5, Mega, Super Mega, Oscuras, Gigantamax y Dynamax. La lista muestra los Pokemon que ya aparecieron en cada categoria, manteniendo la aparicion mas reciente de cada uno. Usa el campo de busqueda para localizar un jefe y toca el nombre para abrir su pagina en Pokebattler."
        )
    ) {
        RaidHistorySection(
            categories = raidHistory.categories,
            language = language,
            pokedexOrder = pokedexOrder
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PokemonFilterBuilderLegacy(context: Context) {
    val language = appLanguage()
    var names by rememberSaveable { mutableStateOf("") }
    var excludes by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }
    val tokens = listOf("shiny", "shadow", "lucky", "legendary", "mythical", "gmax", "dmax", "megaevolve")
    val selectedTokens = remember { mutableStateListOf<String>() }
    val output = remember(names, excludes, selectedType, selectedTokens.toList()) {
        buildPokemonFilter(
            names,
            excludes,
            selectedType,
            selectedTokens.map { FilterExpression(it) }
        )
    }
    val allTypes = remember {
        listOf("Bug", "Dark", "Dragon", "Electric", "Fairy", "Fighting", "Fire", "Flying", "Ghost", "Grass", "Ground", "Ice", "Normal", "Poison", "Psychic", "Rock", "Steel", "Water")
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = names,
            onValueChange = { names = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Pokemon ou termos") },
            supportingText = { Text("Use virgula para varios nomes, ex.: machamp,rayquaza") }
        )
        OutlinedTextField(
            value = excludes,
            onValueChange = { excludes = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Excluir termos") },
            supportingText = { Text("Use virgula para varios, ex.: traded,costume") }
        )
        TypeSelectorSingle(types = allTypes, selectedType = selectedType, onSelect = { selectedType = it }, language = language)
        Text("Tokens rapidos", fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tokens.forEach { token ->
                FilterChip(
                    selected = token in selectedTokens,
                    onClick = {
                        if (token in selectedTokens) selectedTokens.remove(token) else selectedTokens.add(token)
                    },
                    label = { Text(token) }
                )
            }
        }
        CopyableField(
            title = "Filtro copiavel",
            value = output,
            onCopy = { copyPlainText(context, output, language) }
        )
    }
}

@Composable
private fun PeopleFilterBuilderLegacy(context: Context) {
    val language = appLanguage()
    var names by rememberSaveable { mutableStateOf("") }
    val commaSeparated = remember(names) {
        names.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(",")
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = names,
            onValueChange = { names = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            label = { Text("Apelidos ou nomes") },
            supportingText = { Text("Um por linha para gerar texto rapido de busca/copia.") }
        )
        CopyableField(
            title = "Texto copiavel",
            value = commaSeparated,
            onCopy = { copyPlainText(context, commaSeparated, language) }
        )
    }
}

private data class FilterTokenOption(
    val labelPt: String,
    val labelEn: String,
    val labelEs: String,
    val token: String,
    val tokenPt: String = token,
    val tokenEs: String = token
)

private enum class FilterTokenMode {
    INCLUDE,
    EXCLUDE
}

private data class FilterSelection(
    val mode: FilterTokenMode,
    val joinerBefore: String = "&"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PokemonFilterBuilder(
    context: Context,
    language: AppLanguage
) {
    var names by rememberSaveable { mutableStateOf("") }
    val typeSelections = remember { mutableStateMapOf<String, FilterSelection>() }
    val tokenSelections = remember { mutableStateMapOf<String, FilterSelection>() }
    val sections = remember {
        linkedMapOf(
            "Status" to listOf(
                FilterTokenOption("Brilhante", "Shiny", "Variocolor", "shiny", tokenPt = "brilhante", tokenEs = "variocolor"),
                FilterTokenOption("Sombrio", "Shadow", "Oscuro", "shadow", tokenPt = "sombroso", tokenEs = "oscuro"),
                FilterTokenOption("Purificado", "Purified", "Purificado", "purified", tokenPt = "purificado", tokenEs = "purificado"),
                FilterTokenOption("Sortudo", "Lucky", "Suerte", "lucky", tokenPt = "sortudo", tokenEs = "suerte"),
                FilterTokenOption("Favorito", "Favorite", "Favorito", "favorite", tokenPt = "favorito", tokenEs = "favorito"),
                FilterTokenOption("Defensor", "Defender", "Defensor", "defender", tokenPt = "defensor", tokenEs = "defensor")
            ),
            "Colecao" to listOf(
                FilterTokenOption("Lendario", "Legendary", "Legendario", "legendary", tokenPt = "lendario", tokenEs = "legendario"),
                FilterTokenOption("Mitico", "Mythical", "Singular", "mythical", tokenPt = "mitico", tokenEs = "singular"),
                FilterTokenOption("Evento", "Event", "Evento", "event", tokenPt = "evento", tokenEs = "evento"),
                FilterTokenOption("Fantasia", "Costume", "Disfraz", "costume", tokenPt = "fantasia", tokenEs = "disfraz"),
                FilterTokenOption("Trocado", "Traded", "Intercambiado", "traded", tokenPt = "trocado", tokenEs = "intercambiado")
            ),
            "Batalha" to listOf(
                FilterTokenOption("Mega", "Mega", "Mega", "megaevolve"),
                FilterTokenOption("Pode Mega", "Can Mega", "Puede Mega", "canmegaevolve", tokenPt = "pode megaevoluir", tokenEs = "puede megaevolucionar"),
                FilterTokenOption("Dynamax", "Dynamax", "Dynamax", "dmax", tokenPt = "dinamax", tokenEs = "dinamax"),
                FilterTokenOption("Gigantamax", "Gigantamax", "Gigamax", "gmax", tokenPt = "gigamax", tokenEs = "gigamax"),
                FilterTokenOption("Ataque legado", "Legacy move", "Ataque legado", "@special"),
                FilterTokenOption("Ataque rapido", "Fast move", "Ataque rapido", "@move"),
                FilterTokenOption("Carregado", "Charged move", "Ataque cargado", "@charge")
            ),
            "Avaliacao" to listOf(
                FilterTokenOption("0 estrelas", "0 stars", "0 estrellas", "0*"),
                FilterTokenOption("1 estrela", "1 star", "1 estrella", "1*"),
                FilterTokenOption("2 estrelas", "2 stars", "2 estrellas", "2*"),
                FilterTokenOption("3 estrelas", "3 stars", "3 estrellas", "3*"),
                FilterTokenOption("4 estrelas", "4 stars", "4 estrellas", "4*")
            ),
            "Tamanho" to listOf(
                FilterTokenOption("XXS", "XXS", "XXS", "xxs"),
                FilterTokenOption("XS", "XS", "XS", "xs"),
                FilterTokenOption("XL", "XL", "XL", "xl"),
                FilterTokenOption("XXL", "XXL", "XXL", "xxl")
            ),
            "Companheiro" to listOf(
                FilterTokenOption("Buddy 0", "Buddy 0", "Buddy 0", "buddy0", tokenPt = "companheiro0", tokenEs = "companero0"),
                FilterTokenOption("Buddy 1", "Buddy 1", "Buddy 1", "buddy1", tokenPt = "companheiro1", tokenEs = "companero1"),
                FilterTokenOption("Buddy 2", "Buddy 2", "Buddy 2", "buddy2", tokenPt = "companheiro2", tokenEs = "companero2"),
                FilterTokenOption("Buddy 3", "Buddy 3", "Buddy 3", "buddy3", tokenPt = "companheiro3", tokenEs = "companero3"),
                FilterTokenOption("Buddy 4", "Buddy 4", "Buddy 4", "buddy4", tokenPt = "companheiro4", tokenEs = "companero4"),
                FilterTokenOption("Buddy 5", "Buddy 5", "Buddy 5", "buddy5", tokenPt = "companheiro5", tokenEs = "companero5")
            )
        )
    }
    val sectionEntries = remember(sections) { sections.entries.toList() }
    var sectionTabIndex by rememberSaveable { mutableStateOf(0) }
    val output = remember(names, typeSelections.toMap(), tokenSelections.toMap()) {
        buildPokemonFilter(
            names = names,
            selectedType = null,
            selectedTokens = resolveTypeExpressions(typeSelections, language) + resolveTokenExpressions(tokenSelections, sections.values.flatten(), language)
        )
    }
    val allTypes = remember {
        listOf("Bug", "Dark", "Dragon", "Electric", "Fairy", "Fighting", "Fire", "Flying", "Ghost", "Grass", "Ground", "Ice", "Normal", "Poison", "Psychic", "Rock", "Steel", "Water")
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CopyableField(
            title = t(language, "Filtro copiavel", "Copyable filter", "Filtro copiable"),
            value = output,
            onCopy = { copyPlainText(context, output, language) },
            onClear = {
                names = ""
                typeSelections.clear()
                tokenSelections.clear()
            }
        )
        val tabTitles = remember(sectionEntries) {
            listOf("Tipo") + sectionEntries.map { it.key }
        }
        ScrollableTabRow(selectedTabIndex = sectionTabIndex, edgePadding = 0.dp, divider = {}) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = index == sectionTabIndex,
                    onClick = { sectionTabIndex = index },
                    text = { Text(title) }
                )
            }
        }
        when (sectionTabIndex) {
            0 -> {
                TypeSelectorSingleTriState(
                    types = allTypes,
                    selections = typeSelections,
                    onCycle = { type -> typeSelections.cycleSelection(type) },
                    onJoinerChange = { type, joiner -> typeSelections.updateJoiner(type, joiner) },
                    language = language
                )
            }
            else -> {
                val entry = sectionEntries[sectionTabIndex - 1]
                Text(entry.key, fontWeight = FontWeight.Bold)
                UniformOptionGrid(count = entry.value.size) { index ->
                    val option = entry.value[index]
                    TriStateTokenChip(
                        option = option,
                        language = language,
                        selection = tokenSelections[option.token],
                        onCycle = { tokenSelections.cycleSelection(option.token) },
                        onJoinerChange = { joiner -> tokenSelections.updateJoiner(option.token, joiner) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        OutlinedTextField(
            value = names,
            onValueChange = { names = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(t(language, "Pokemon ou termos", "Pokemon or terms", "Pokemon o terminos")) },
            supportingText = { Text(t(language, "Use virgula para varios nomes, ex.: machamp,rayquaza", "Use commas for multiple names, e.g. machamp,rayquaza", "Usa comas para varios nombres, ej.: machamp,rayquaza")) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PeopleFilterBuilder(
    context: Context,
    language: AppLanguage
) {
    var names by rememberSaveable { mutableStateOf("") }
    val tokenSelections = remember { mutableStateMapOf<String, FilterSelection>() }
    val sections = remember {
        linkedMapOf(
            "Busca rapida" to listOf(
                FilterTokenOption("Nivel de amizade 1 a 4", "Friendship level 1 to 4", "Nivel de amistad 1 a 4", "friendlevel1,friendlevel2,friendlevel3,friendlevel4", tokenPt = "nivel de amizade 1,nivel de amizade 2,nivel de amizade 3,nivel de amizade 4", tokenEs = "nivel de amistad 1,nivel de amistad 2,nivel de amistad 3,nivel de amistad 4"),
                FilterTokenOption("Amizade sortuda", "Lucky friends", "Amistad con suerte", "lucky", tokenPt = "amizade sortuda", tokenEs = "amistad con suerte"),
                FilterTokenOption("Interagir", "Interact", "Interactuar", "interactable", tokenPt = "interagir", tokenEs = "interactuar")
            ),
            "Interacao" to listOf(
                FilterTokenOption("Interagir", "Interact", "Interactuar", "interactable", tokenPt = "interagir", tokenEs = "interactuar"),
                FilterTokenOption("Pode receber presente", "Can receive gift", "Puede recibir regalo", "canreceivegift", tokenPt = "pode receber presente", tokenEs = "puede recibir regalo"),
                FilterTokenOption("Pode enviar presente", "Giftable", "Regalable", "giftable", tokenPt = "pode enviar presente", tokenEs = "puede enviar regalo")
            ),
            "Nivel de amizade" to listOf(
                FilterTokenOption("Nivel 0", "Friendship 0", "Amistad 0", "friendlevel0", tokenPt = "nivel de amizade 0", tokenEs = "nivel de amistad 0"),
                FilterTokenOption("Nivel 1", "Friendship 1", "Amistad 1", "friendlevel1", tokenPt = "nivel de amizade 1", tokenEs = "nivel de amistad 1"),
                FilterTokenOption("Nivel 2", "Friendship 2", "Amistad 2", "friendlevel2", tokenPt = "nivel de amizade 2", tokenEs = "nivel de amistad 2"),
                FilterTokenOption("Nivel 3", "Friendship 3", "Amistad 3", "friendlevel3", tokenPt = "nivel de amizade 3", tokenEs = "nivel de amistad 3"),
                FilterTokenOption("Nivel 4", "Friendship 4", "Amistad 4", "friendlevel4", tokenPt = "nivel de amizade 4", tokenEs = "nivel de amistad 4")
            ),
            "Troca" to listOf(
                FilterTokenOption("Amizade sortuda", "Lucky friends", "Amistad con suerte", "lucky", tokenPt = "amizade sortuda", tokenEs = "amistad con suerte"),
                FilterTokenOption("Com troca pendente", "Trade", "Intercambio", "trade", tokenPt = "troca", tokenEs = "intercambio"),
                FilterTokenOption("Distancia 10km+", "Distance 10km+", "Distancia 10km+", "distance10-", tokenPt = "distancia10-", tokenEs = "distancia10-"),
                FilterTokenOption("Distancia 100km+", "Distance 100km+", "Distancia 100km+", "distance100-", tokenPt = "distancia100-", tokenEs = "distancia100-")
            )
        )
    }
    val sectionEntries = remember(sections) { sections.entries.toList() }
    var sectionTabIndex by rememberSaveable { mutableStateOf(0) }
    val output = remember(names, tokenSelections.toMap()) {
        buildPeopleFilter(
            names = names,
            selectedTokens = resolveTokenExpressions(tokenSelections, sections.values.flatten(), language)
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CopyableField(
            title = t(language, "Texto copiavel", "Copyable text", "Texto copiable"),
            value = output,
            onCopy = { copyPlainText(context, output, language) },
            onClear = {
                names = ""
                tokenSelections.clear()
            }
        )
        ScrollableTabRow(selectedTabIndex = sectionTabIndex, edgePadding = 0.dp, divider = {}) {
            sectionEntries.forEachIndexed { index, entry ->
                Tab(
                    selected = index == sectionTabIndex,
                    onClick = { sectionTabIndex = index },
                    text = { Text(entry.key) }
                )
            }
        }
        val activeSection = sectionEntries[sectionTabIndex]
        Text(activeSection.key, fontWeight = FontWeight.Bold)
        UniformOptionGrid(count = activeSection.value.size) { index ->
            val option = activeSection.value[index]
            TriStateTokenChip(
                option = option,
                language = language,
                selection = tokenSelections[option.token],
                onCycle = { tokenSelections.cycleSelection(option.token) },
                onJoinerChange = { joiner -> tokenSelections.updateJoiner(option.token, joiner) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            t(
                language,
                "Os chips alternam entre incluir, excluir e limpar. Ex.: toque em Amizade sortuda para gerar lucky e depois !lucky.",
                "Chips cycle between include, exclude, and clear. Example: tap Lucky friends to generate lucky and then !lucky.",
                "Los chips alternan entre incluir, excluir y limpiar. Ej.: toca Amistad con suerte para generar lucky y luego !lucky."
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = names,
            onValueChange = { names = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            label = { Text(t(language, "Apelidos ou nomes", "Nicknames or names", "Apodos o nombres")) },
            supportingText = { Text(t(language, "Um por linha para combinar com os filtros abaixo.", "One per line to combine with the filters below.", "Uno por linea para combinar con los filtros abajo.")) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchupSection(
    matchup: List<TypeMatchupEntry>,
    language: AppLanguage
) {
    if (matchup.isEmpty()) return
    val strongDouble = matchup.filter { it.multiplier >= 2.55 }
    val strong = matchup.filter { it.multiplier in 1.01..2.54 }
    val weak = matchup.filter { it.multiplier in 0.40..0.99 }
    val veryWeak = matchup.filter { it.multiplier < 0.40 }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (strongDouble.isNotEmpty()) {
            Text(t(language, "Fraqueza dupla", "Double weakness", "Debilidad doble"), fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                strongDouble.forEach { item ->
                    TypeStatBar(type = item.attackType, language = language, text = formatMultiplier(item.multiplier))
                }
            }
        }
        if (strong.isNotEmpty()) {
            Text(t(language, "Fraquezas", "Weaknesses", "Debilidades"), fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                strong.forEach { item ->
                    TypeStatBar(type = item.attackType, language = language, text = formatMultiplier(item.multiplier))
                }
            }
        }
        if (weak.isNotEmpty()) {
            Text(t(language, "Resistencias", "Resistances", "Resistencias"), fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                weak.forEach { item ->
                    TypeStatBar(type = item.attackType, language = language, text = formatMultiplier(item.multiplier))
                }
            }
        }
        if (veryWeak.isNotEmpty()) {
            Text(t(language, "Resistencia dupla", "Double resistance", "Resistencia doble"), fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                veryWeak.forEach { item ->
                    TypeStatBar(type = item.attackType, language = language, text = formatMultiplier(item.multiplier))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeSelector(
    types: List<String>,
    selectedTypes: MutableList<String>,
    maxSelection: Int,
    language: AppLanguage,
    verticalSpacing: Dp = 8.dp
) {
    UniformOptionGrid(count = types.size, verticalSpacing = verticalSpacing) { index ->
        val type = types[index]
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable {
                        when {
                            type in selectedTypes -> selectedTypes.remove(type)
                            selectedTypes.size < maxSelection -> selectedTypes.add(type)
                            else -> {
                                if (selectedTypes.isNotEmpty()) {
                                    selectedTypes.removeAt(0)
                                }
                                selectedTypes.add(type)
                            }
                        }
                    },
                shape = RoundedCornerShape(999.dp),
                color = Color.Transparent,
                tonalElevation = if (type in selectedTypes) 2.dp else 0.dp
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 0.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    TypeBadge(
                        type = type,
                        language = language,
                        modifier = Modifier.fillMaxWidth(),
                        showAssetIcon = true,
                        filled = true
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeSelectorSingle(
    types: List<String>,
    selectedType: String?,
    onSelect: (String?) -> Unit,
    language: AppLanguage,
    showAllOption: Boolean = true
) {
    val itemCount = types.size + if (showAllOption) 1 else 0
    UniformOptionGrid(count = itemCount, verticalSpacing = 4.dp) { index ->
        if (showAllOption && index == 0) {
            FilterChip(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                selected = selectedType == null,
                onClick = { onSelect(null) },
                label = {
                    Text(
                        t(language, "Todos", "All", "Todos"),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        } else {
            val type = types[index - if (showAllOption) 1 else 0]
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable { onSelect(if (selectedType == type) null else type) },
                shape = RoundedCornerShape(999.dp),
                color = Color.Transparent,
                tonalElevation = if (selectedType == type) 2.dp else 0.dp
            ) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    TypeBadge(
                        type = type,
                        language = language,
                        modifier = Modifier.fillMaxWidth(),
                        showAssetIcon = true,
                        filled = true
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeSelectorSingleTriState(
    types: List<String>,
    selections: MutableMap<String, FilterSelection>,
    onCycle: (String) -> Unit,
    onJoinerChange: (String, String) -> Unit,
    language: AppLanguage
) {
    UniformOptionGrid(count = types.size) { index ->
        val type = types[index]
        val selection = selections[type]
        val mode = selection?.mode
        val prefix = when (mode) {
            FilterTokenMode.INCLUDE -> "+ "
            FilterTokenMode.EXCLUDE -> "! "
            null -> ""
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable { onCycle(type) },
                shape = RoundedCornerShape(999.dp),
                color = Color.Transparent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    TypeBadge(
                        type = type,
                        language = language,
                        modifier = Modifier.fillMaxWidth(),
                        showAssetIcon = true,
                        inlinePrefix = prefix.trim().ifBlank { null },
                        filled = true
                    )
                }
            }
            if (selection != null) {
                JoinerSelector(
                    selected = selection.joinerBefore,
                    onSelect = { onJoinerChange(type, it) }
                )
            }
        }
    }
}

@Composable
private fun TypeOptionContent(
    label: String,
    type: String,
    language: AppLanguage
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TypeIcon(
            type = type,
            language = language,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun JoinerSelector(
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("&", ",").forEach { option ->
            FilterChip(
                modifier = Modifier.weight(1f).height(36.dp),
                selected = option == selected,
                onClick = { onSelect(option) },
                label = {
                    Text(
                        option,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            )
        }
    }
}

@Composable
private fun MoveCategoryFilter(
    selectedCategory: MoveCategory?,
    onSelect: (MoveCategory) -> Unit,
    language: AppLanguage
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(
            MoveCategory.FAST to t(language, "Rapido", "Fast", "Rapido"),
            MoveCategory.CHARGED to t(language, "Carregado", "Charged", "Cargado")
        ).forEach { (category, label) ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onSelect(category) },
                modifier = Modifier.weight(1f),
                label = {
                    Text(
                        label,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun FilterRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option) }
            )
            Spacer(Modifier.width(2.dp))
        }
    }
}

@Composable
private fun MoveCardLegacy(
    entry: MoveCatalogEntry,
    language: AppLanguage
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val primaryName = entry.localizedName(language)
            val categoryLabel = if (entry.category == MoveCategory.FAST) "Rapido" else "Carregado"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$primaryName ($categoryLabel)",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TypeIcon(type = entry.type, language = language, modifier = Modifier.padding(start = 8.dp))
            }
            Text("${entry.type} • ${if (entry.category == MoveCategory.FAST) "Rapido" else "Carregado"}")
            Text(
                listOf(
                    entry.power?.let { "Poder $it" },
                    entry.energyDelta?.let { "Energia $it" },
                    entry.duration?.let { "Duracao ${it}ms" }
                ).joinToString(" • ").ifBlank { "Sem detalhes" },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PokedexCardLegacy(entry: PokedexCatalogEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("#${entry.number.toString().padStart(4, '0')} ${entry.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(listOfNotNull(entry.type1, entry.type2).joinToString(" / ").ifBlank { "Tipo indisponivel" })
            Text(
                listOf(
                    entry.attack?.let { "Atk $it" },
                    entry.defense?.let { "Def $it" },
                    entry.stamina?.let { "Sta $it" },
                    entry.forms.takeIf { it.size > 1 }?.let { "${it.size} formas" }
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BattleSuggestionCardLegacy(entry: BattleSuggestionEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(entry.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entry.attackTypes.forEach { type ->
                    AssistChip(onClick = {}, label = { Text(type) })
                }
            }
            Text(entry.searchTerms.joinToString(","), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MoveCard(
    entry: MoveCatalogEntry,
    language: AppLanguage
) {
    val primaryName = entry.localizedName(language)
    val categoryLabel = t(
        language,
        if (entry.category == MoveCategory.FAST) "rapido" else "carregado",
        if (entry.category == MoveCategory.FAST) "fast" else "charged",
        if (entry.category == MoveCategory.FAST) "rapido" else "cargado"
    )
    val gymDetails = moveDetailsText(
        language = language,
        damage = entry.power,
        energy = entry.energyDelta,
        duration = entry.duration?.let { "${it}ms" }
    )
    val pvpDetails = moveDetailsText(
        language = language,
        damage = entry.pvpPower,
        energy = entry.pvpEnergyDelta,
        duration = entry.pvpTurnDuration?.let {
            t(language, "${it}t", "${it}t", "${it}t")
        }
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$primaryName ($categoryLabel)",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TypeIcon(type = entry.type, language = language, modifier = Modifier.padding(start = 8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MoveDetailBlock(
                    title = t(language, "Ginasio", "Gym", "Gimnasio"),
                    details = gymDetails,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )
                MoveDetailBlock(
                    title = "PvP",
                    details = pvpDetails,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun MoveDetailBlock(
    title: String,
    details: List<String>,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            title,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val detailLines = details.ifEmpty { listOf("-") }
        detailLines.forEach { detail ->
            Text(
                detail,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = textAlign,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun moveDetailsText(
    language: AppLanguage,
    damage: Int?,
    energy: Int?,
    duration: String?
): List<String> {
    return listOf(
        damage?.let { t(language, "dano $it", "damage $it", "dano $it") },
        energy?.let { t(language, "energia $it", "energy $it", "energia $it") },
        duration?.let { t(language, "duracao $it", "duration $it", "duracion $it") }
    ).filterNotNull().ifEmpty { listOf(t(language, "sem detalhes", "no details", "sin detalles")) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PokedexCard(
    entry: PokedexCatalogEntry,
    language: AppLanguage
) {
    val primaryName = entry.localizedName(language)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "#${entry.number.toString().padStart(3, '0')}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(primaryName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (primaryName != entry.name) {
                        Text(entry.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOfNotNull(entry.type1, entry.type2).forEach { type ->
                    TypeBadge(type = type, language = language)
                }
            }
            Text(
                listOf(
                    entry.attack?.let { "Atk $it" },
                    entry.defense?.let { "Def $it" },
                    entry.stamina?.let { "Sta $it" },
                    entry.forms.takeIf { it.size > 1 }?.let { t(language, "${it.size} formas", "${it.size} forms", "${it.size} formas") }
                ).joinToString("  •  "),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaidHistorySection(
    categories: List<RaidHistoryCategory>,
    language: AppLanguage,
    pokedexOrder: Map<String, Int>
) {
    if (categories.isEmpty()) return

    val uriHandler = LocalUriHandler.current
    var tabIndex by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val selectedCategory = categories.getOrNull(tabIndex) ?: categories.first()
    val normalizedQuery = remember(query) { normalizeSearch(query) }
    val sortedItems = remember(selectedCategory, pokedexOrder) {
        selectedCategory.items.sortedWith(
            compareBy(
                { item -> pokedexOrder[normalizeRaidHistoryName(item.name)] ?: Int.MAX_VALUE },
                { item -> normalizeSearch(item.name) }
            )
        )
    }
    val visibleItems = remember(sortedItems, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            sortedItems
        } else {
            sortedItems.filter { normalizeSearch(it.name).contains(normalizedQuery) }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ScrollableTabRow(selectedTabIndex = tabIndex, edgePadding = 0.dp, divider = {}) {
                categories.forEachIndexed { index, category ->
                    Tab(
                        selected = index == tabIndex,
                        onClick = { tabIndex = index },
                        text = {
                            Text(
                                category.localizedTitle(language),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(t(language, "Buscar chefe", "Search boss", "Buscar jefe")) },
                singleLine = true
            )
            if (visibleItems.isEmpty()) {
                Text(
                    t(language, "Nenhum Pokemon encontrado", "No Pokemon found", "Ningun Pokemon encontrado"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    visibleItems.forEach { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = item.url.isNotBlank()) {
                                    runCatching { uriHandler.openUri(item.url) }
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    item.name,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaidSuggestionColumns(
    attackers: List<BattleSuggestionEntry>,
    defenders: List<BattleSuggestionEntry>,
    language: AppLanguage
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RaidSuggestionColumn(
            title = t(language, "Atacantes", "Attackers", "Atacantes"),
            entries = attackers.take(8),
            language = language,
            modifier = Modifier.weight(1f)
        )
        RaidSuggestionColumn(
            title = t(language, "Defensores", "Defenders", "Defensores"),
            entries = defenders.take(8),
            language = language,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaidSuggestionColumn(
    title: String,
    entries: List<BattleSuggestionEntry>,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            entries.forEachIndexed { index, entry ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${index + 1}.", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        entry.attackTypes.take(2).forEach { type ->
                            TypeIcon(type = type, language = language, modifier = Modifier.size(22.dp))
                        }
                    }
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BattleSuggestionCard(
    entry: BattleSuggestionEntry,
    language: AppLanguage
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            entry.attackTypes.take(2).forEach { type ->
                TypeIcon(type = type, language = language)
            }
            Text(
                entry.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CopyableField(
    title: String,
    value: String,
    onCopy: () -> Unit,
    onClear: (() -> Unit)? = null
) {
    val language = appLanguage()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1400)
            copied = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.24f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(value.ifBlank { "-" })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    onCopy()
                    copied = true
                },
                enabled = value.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (copied) {
                        t(language, "Copiado!", "Copied!", "Copiado!")
                    } else {
                        t(language, "Copiar", "Copy", "Copiar")
                    }
                )
            }
            if (onClear != null) {
                Button(
                    onClick = onClear,
                modifier = Modifier.weight(1f)
                ) {
                    Text(t(language, "Limpar", "Clear", "Limpiar"))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleToolScreen(
    title: String,
    onBack: () -> Unit,
    helpTitle: String? = null,
    helpText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var showHelp by rememberSaveable { mutableStateOf(false) }
    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (helpTitle != null && helpText != null) {
                        IconButton(onClick = { showHelp = true }) {
                            UnownQuestionIcon(modifier = Modifier.size(24.dp), contentDescription = "Ajuda")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
    if (showHelp && helpTitle != null && helpText != null) {
        ToolHelpDialog(title = helpTitle, body = helpText, onDismiss = { showHelp = false })
    }
}

@Composable
private fun ToolHelpDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit
) {
    val language = appLanguage()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(t(language, "OK", "OK", "OK"))
            }
        },
        title = { Text(title) },
        text = { Text(body) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadingToolScreen(
    title: String,
    onBack: () -> Unit,
    language: AppLanguage
) {
    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(t(language, "Carregando dados", "Loading data", "Cargando datos"))
            }
        }
    }
}

@Composable
private fun InlineLoadingRow(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun HomeMenuGlyph(kind: String) {
    val assetPath = when (kind) {
        "capture" -> "menu/icon-gerar-nome.jpg"
        "presets" -> "menu/icon-definir-nome.jpg"
        "legacy" -> "menu/icon-ataque-legado.jpg"
        "adventure" -> "menu/icon-efeito-aventura.jpg"
        "update" -> "menu/icon-atualizar.jpg"
        "validation" -> "menu/icon-amostras.jpg"
        "raid" -> "menu/icon-raids.jpg"
        "types" -> "menu/icon-tipos.jpg"
        "moves" -> "menu/icon-ataques.jpg"
        "pokedex" -> "menu/icon-pokedex.jpg"
        "filters" -> "menu/icon-filtros.jpg"
        "donation" -> "menu/icon-doacao.jpg"
        "calendar" -> "menu/icon-calendario.jpg"
        "privacy" -> "menu/icon-ajuda.jpg"
        "test" -> "menu/icon-teste.jpg"
        "help" -> "menu/icon-ajuda.jpg"
        else -> null
    }
    if (assetPath != null) {
        AssetImageIcon(
            assetPath = assetPath,
            contentDescription = null,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp)),
            fallbackSize = 30.dp
        )
        return
    }

    val accent = when (kind) {
        "capture" -> MaterialTheme.colorScheme.primary
        "presets" -> MaterialTheme.colorScheme.secondary
        "legacy" -> Color(0xFFB26A00)
        "adventure" -> Color(0xFF2E7D6B)
        "update" -> Color(0xFF5C6BC0)
        "validation" -> Color(0xFF8E24AA)
        "raid" -> Color(0xFFC62828)
        "types" -> Color(0xFF00897B)
        "moves" -> Color(0xFFEF6C00)
        "pokedex" -> Color(0xFF3949AB)
        "filters" -> Color(0xFF6D4C41)
        "donation" -> Color(0xFFD4A017)
        "calendar" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = Modifier
            .size(30.dp)
            .border(2.dp, accent, shape)
            .padding(3.dp)
    ) {
        when (kind) {
            "capture" -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(14.dp)
                        .border(2.dp, accent, RoundedCornerShape(999.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(5.dp)
                        .background(accent, RoundedCornerShape(999.dp))
                )
            }

            "presets" -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(accent.copy(alpha = 0.18f), RoundedCornerShape(3.dp))
                )
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(2.dp)
                                .background(accent, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }

            "legacy" -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(modifier = Modifier.width(6.dp).height(3.dp).background(accent, RoundedCornerShape(2.dp)))
                    Box(modifier = Modifier.width(14.dp).height(3.dp).background(accent, RoundedCornerShape(2.dp)))
                    Box(modifier = Modifier.width(8.dp).height(3.dp).background(accent, RoundedCornerShape(2.dp)))
                    Box(modifier = Modifier.width(12.dp).height(3.dp).background(accent, RoundedCornerShape(2.dp)))
                }
            }

            "adventure" -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(14.dp)
                        .border(2.dp, accent, RoundedCornerShape(999.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(5.dp)
                        .background(accent, RoundedCornerShape(999.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(14.dp)
                        .height(2.dp)
                        .background(accent.copy(alpha = 0.55f), RoundedCornerShape(2.dp))
                )
            }

            "raid" -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(18.dp)
                        .border(2.dp, accent, RoundedCornerShape(999.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(8.dp)
                        .border(2.dp, accent, RoundedCornerShape(999.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(3.dp)
                        .background(accent, RoundedCornerShape(999.dp))
                )
            }

            "types" -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    repeat(2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(2) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(accent, RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }
                }
            }

            "moves" -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    repeat(3) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(accent, RoundedCornerShape(999.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .width(12.dp)
                                    .height(2.dp)
                                    .background(accent, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }

            "pokedex" -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(accent, RoundedCornerShape(3.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(13.dp)
                        .border(2.dp, accent, RoundedCornerShape(4.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .width(12.dp)
                        .height(2.dp)
                        .background(accent.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
                )
            }

            "filters" -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(modifier = Modifier.width(18.dp).height(4.dp).background(accent, RoundedCornerShape(2.dp)))
                    Box(modifier = Modifier.width(10.dp).height(4.dp).background(accent, RoundedCornerShape(2.dp)))
                    Box(modifier = Modifier.width(4.dp).height(8.dp).background(accent, RoundedCornerShape(2.dp)))
                }
            }

            "update" -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .width(4.dp)
                        .height(12.dp)
                        .background(accent, RoundedCornerShape(2.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(12.dp)
                        .height(4.dp)
                        .background(accent, RoundedCornerShape(2.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(16.dp)
                        .height(4.dp)
                        .background(accent.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                )
            }

            "validation" -> {
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    listOf(8.dp, 14.dp, 20.dp).forEach { barHeight ->
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(barHeight)
                                .background(accent, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }

            "donation" -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(18.dp)
                        .border(2.dp, accent, RoundedCornerShape(999.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(3.dp)
                        .height(12.dp)
                        .background(accent, RoundedCornerShape(2.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(10.dp)
                        .height(2.dp)
                        .background(accent.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
                )
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(accent.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(accent.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(width = 2.dp, height = 16.dp)
                        .background(accent.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

private fun buildPokemonFilter(
    names: String,
    excludes: String = "",
    selectedType: String?,
    selectedTokens: List<FilterExpression>
): String {
    val includeNames = names.split(',').map { it.trim() }.filter { it.isNotBlank() }
    val excludeTerms = excludes.split(',').map { it.trim() }.filter { it.isNotBlank() }

    val parts = mutableListOf<FilterExpression>()
    if (includeNames.isNotEmpty()) {
        parts += FilterExpression(
            value = if (includeNames.size == 1) includeNames.first() else includeNames.joinToString(prefix = "(", postfix = ")", separator = ",")
        )
    }
    selectedType?.let { parts += FilterExpression(it) }
    parts += selectedTokens
    parts += excludeTerms.map { FilterExpression("!$it") }
    return parts.mapIndexed { index, expression ->
        if (index == 0) expression.value else "${expression.joinerBefore}${expression.value}"
    }.joinToString("")
}

private data class FilterExpression(
    val value: String,
    val joinerBefore: String = "&"
)

private fun buildPeopleFilter(
    names: String,
    selectedTokens: List<FilterExpression>
): String {
    val includeNames = names.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()
    return (includeNames + selectedTokens.map { it.value })
        .distinct()
        .joinToString(",")
}

private fun resolveTypeExpressions(
    typeSelections: Map<String, FilterSelection>,
    language: AppLanguage
): List<FilterExpression> {
    return typeSelections.entries.map { (type, selection) ->
        val normalized = localizedTypeLabel(type, language).lowercase(Locale.US)
        val value = when (selection.mode) {
            FilterTokenMode.INCLUDE -> normalized
            FilterTokenMode.EXCLUDE -> "!$normalized"
        }
        FilterExpression(value = value, joinerBefore = selection.joinerBefore)
    }
}

@Composable
private fun TriStateTokenChip(
    option: FilterTokenOption,
    language: AppLanguage,
    selection: FilterSelection?,
    onCycle: () -> Unit,
    onJoinerChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val mode = selection?.mode
    val colors = when (mode) {
        FilterTokenMode.INCLUDE -> FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
        FilterTokenMode.EXCLUDE -> FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
        )
        null -> FilterChipDefaults.filterChipColors()
    }
    val prefix = when (mode) {
        FilterTokenMode.INCLUDE -> "+ "
        FilterTokenMode.EXCLUDE -> "! "
        null -> ""
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            selected = mode != null,
            onClick = onCycle,
            colors = colors,
            label = {
                Text(
                    prefix + localizedLabel(option, language),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
        if (selection != null) {
            JoinerSelector(
                selected = selection.joinerBefore,
                onSelect = onJoinerChange
            )
        }
    }
}

@Composable
private fun UniformOptionGrid(
    count: Int,
    modifier: Modifier = Modifier,
    verticalSpacing: Dp = 8.dp,
    content: @Composable (Int) -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = 3
        val rows = (0 until count).toList().chunked(columns)

        Column(verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { index ->
                        Box(modifier = Modifier.weight(1f)) {
                            content(index)
                        }
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun MutableMap<String, FilterSelection>.cycleSelection(token: String) {
    when (this[token]?.mode) {
        null -> this[token] = FilterSelection(FilterTokenMode.INCLUDE)
        FilterTokenMode.INCLUDE -> this[token] = this[token]?.copy(mode = FilterTokenMode.EXCLUDE)
            ?: FilterSelection(FilterTokenMode.EXCLUDE)
        FilterTokenMode.EXCLUDE -> remove(token)
    }
}

private fun MutableMap<String, FilterSelection>.updateJoiner(token: String, joiner: String) {
    this[token]?.let { current ->
        this[token] = current.copy(joinerBefore = joiner)
    }
}

private fun resolveTokenExpressions(
    tokenSelections: Map<String, FilterSelection>,
    options: List<FilterTokenOption>,
    language: AppLanguage
): List<FilterExpression> {
    val localizedTokens = options.associate { option -> option.token to option.localizedToken(language) }
    return tokenSelections.entries.flatMap { (token, selection) ->
        localizedTokens[token].orEmpty().ifBlank { token }.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapIndexed { index, part ->
                val value = when (selection.mode) {
                    FilterTokenMode.INCLUDE -> part
                    FilterTokenMode.EXCLUDE -> "!$part"
                }
                FilterExpression(
                    value = value,
                    joinerBefore = if (index == 0) selection.joinerBefore else ","
                )
            }
    }
}

private fun FilterTokenOption.localizedToken(language: AppLanguage): String {
    return when (language) {
        AppLanguage.PT_BR -> tokenPt
        AppLanguage.EN -> token
        AppLanguage.ES -> tokenEs
    }
}

private fun localizedLabel(
    option: FilterTokenOption,
    language: AppLanguage
): String {
    return when (language) {
        AppLanguage.PT_BR -> option.labelPt
        AppLanguage.EN -> option.labelEn
        AppLanguage.ES -> option.labelEs
    }
}

private data class IndexedMoveEntry(
    val entry: MoveCatalogEntry,
    val searchIndex: String
)

private fun t(
    language: AppLanguage,
    pt: String,
    en: String,
    es: String
): String {
    return when (language) {
        AppLanguage.PT_BR -> pt
        AppLanguage.EN -> en
        AppLanguage.ES -> es
    }
}

private fun copyPlainText(context: Context, value: String, language: AppLanguage) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("MewName", value))
    Toast.makeText(context, t(language, "Copiado", "Copied", "Copiado"), Toast.LENGTH_SHORT).show()
}

private fun normalizeSearch(text: String): String {
    return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase(Locale.US)
        .trim()
}

private fun normalizeRaidHistoryName(text: String): String {
    return normalizeSearch(text)
        .replace(Regex("\\([^)]*\\)"), "")
        .replace(Regex("\\b(mega|primal|shadow|dynamax|gigantamax)\\b"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun formatMultiplier(value: Double): String {
    return when {
        value >= 2.55 -> "x2.56"
        value >= 1.55 -> "x1.60"
        value > 1.0 -> "x1.00+"
        value <= 0.40 -> "x0.39"
        value < 1.0 -> "x0.62"
        else -> "x1.00"
    }
}

