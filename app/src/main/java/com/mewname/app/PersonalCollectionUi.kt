package com.mewname.app

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mewname.app.domain.PersonalCollection
import com.mewname.app.domain.PersonalCollectionGroup
import com.mewname.app.domain.PersonalCollectionSnapshot
import com.mewname.app.domain.PersonalCollectionTarget
import com.mewname.app.model.PokemonScreenData
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

@Composable
private fun rememberCollectionWriter(): (PersonalCollectionTarget, PersonalCollectionGroup, Boolean) -> Unit {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember(context, scope) {
        { target, group, obtained ->
            scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                // Finish an accepted edit even if navigation disposes this screen.
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                    PersonalCollection.mark(context, target, group, obtained)
                }
            }
            Unit
        }
    }
}
@Composable
private fun rememberPersonalCollection(): PersonalCollectionSnapshot {
    val context = LocalContext.current
    val snapshot by produceState(
        PersonalCollectionSnapshot(PersonalCollectionGroup.entries.toSet(), emptyMap()), context
    ) {
        val prefs = PersonalCollection.preferences(context)
        val changes = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> changes.trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        changes.trySend(Unit)
        try {
            for (ignored in changes) {
                value = withContext(Dispatchers.IO) { PersonalCollection.read(context) }
            }
        } finally {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            changes.close()
        }
    }
    return snapshot
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonalCollectionGroups(target: PersonalCollectionTarget, snapshot: PersonalCollectionSnapshot, includeHidden: Boolean = false, groups: Set<PersonalCollectionGroup>? = null) {
    val mark = rememberCollectionWriter()
    val obtained = snapshot.entries[target.key]?.groups.orEmpty()
    val visible = groups ?: if (includeHidden) snapshot.enabled + obtained else snapshot.enabled
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        snapshot.order.filter { it in visible && PersonalCollection.supports(target, it) }.forEach { group ->
            val checked = group in obtained
            FilterChip(
                selected = checked,
                onClick = { mark(target,group,!checked) },
                label = { Text(group.shortLabel,fontSize = 11.sp) },
                leadingIcon = if (checked) { { Icon(AppIcons.Selected,contentDescription = null,modifier = Modifier.size(14.dp)) } } else null,
                modifier = Modifier.semantics { contentDescription = "${group.label}: ${if (checked) "já coletado" else "não coletado"}. ${target.label}" }
            )
        }
    }
}

@Composable
internal fun PersonalCollectionReview(data: PokemonScreenData) {
    val mark = rememberCollectionWriter()
    val context = LocalContext.current
    val snapshot = rememberPersonalCollection()
    val target = remember(data.pokemonName,data.uniqueForm,data.unownLetter,data.vivillonPattern) { PersonalCollection.target(context,data) }
    if (!snapshot.showInSuggestedNames || snapshot.enabled.isEmpty()) return
    val obtained = target?.let { snapshot.entries[it.key]?.groups }.orEmpty()
    val visibleGroups = snapshot.order.filter { it in snapshot.enabled && PersonalCollection.supports(target, it) }
    if (visibleGroups.isEmpty()) return
    AppSectionCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp,vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp,vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            visibleGroups.forEach { group ->
                PersonalCollectionIcon(group,group in obtained,target != null,
                    target?.label ?: "Selecione o Pokémon e sua forma") {
                    target?.let { mark(it,group,group !in obtained) }
                }
            }
        }
    }
}

/** Trimmed category emblems, shared by every collection row and review card in the process. */
private val trimmedCollectionIcons = java.util.concurrent.ConcurrentHashMap<String, androidx.compose.ui.graphics.ImageBitmap>()
@Volatile private var shadowAuraFrame: androidx.compose.ui.graphics.ImageBitmap? = null

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonalCollectionIcon(group: PersonalCollectionGroup, checked: Boolean, enabled: Boolean, pokemon: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(8.dp)
    val appearance = LocalAppAppearance.current
    val foreground = if (enabled) appearance.text else appearance.text.copy(alpha = .38f)
    val label = "${group.label}: ${if (checked) "já coletado" else "não coletado"}. $pokemon"
    Box(
        Modifier.size(34.dp).clip(shape)
            .background(if (checked) MaterialTheme.colorScheme.primary.copy(alpha = .20f) else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp,if (checked) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,shape)
            .combinedClickable(enabled = enabled,role = Role.Checkbox,onClick = onClick,
                onLongClickLabel = group.label,
                onLongClick = { Toast.makeText(context,group.label,Toast.LENGTH_SHORT).show() })
            .semantics {
                contentDescription = label
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
            },
        contentAlignment = Alignment.Center
    ) {
        val asset = when (group) {
            PersonalCollectionGroup.SHINY -> "shiny.png"
            PersonalCollectionGroup.LUCKY -> "lucky.png"
            PersonalCollectionGroup.SHADOW -> "shadow.png"
            PersonalCollectionGroup.PURIFIED -> "purified.png"
            PersonalCollectionGroup.XXS -> "xxs.png"
            PersonalCollectionGroup.XXL -> "xxl.png"
            PersonalCollectionGroup.BACKGROUND -> "background.png"
            PersonalCollectionGroup.LITTLE -> "little.png"
            PersonalCollectionGroup.GREAT -> "great.png"
            PersonalCollectionGroup.ULTRA -> "ultra.png"
            PersonalCollectionGroup.MASTER_LEAGUE -> "master.png"
            PersonalCollectionGroup.DYNAMAX -> "dynamax.png"
            PersonalCollectionGroup.GIGANTAMAX -> "gigantamax.png"
            PersonalCollectionGroup.MEGA -> "mega.png"
            else -> null
        }
        if (asset != null) {
            val bitmap by produceState(trimmedCollectionIcons[asset], asset) {
                value = trimmedCollectionIcons[asset] ?: withContext(Dispatchers.IO) {
                context.assets.open("collection/$asset").use { input ->
                    val source = requireNotNull(BitmapFactory.decodeStream(input))
                    // One bulk read: the per-pixel scan ran for every icon of every review.
                    val pixels = IntArray(source.width * source.height)
                    source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
                    var left = source.width; var top = source.height; var right = -1; var bottom = -1
                    for (y in 0 until source.height) for (x in 0 until source.width) {
                        if ((pixels[y * source.width + x] ushr 24) > 16) {
                            left = minOf(left,x); top = minOf(top,y)
                            right = maxOf(right,x); bottom = maxOf(bottom,y)
                        }
                    }
                    val trimmed = if (right >= left && bottom >= top)
                        android.graphics.Bitmap.createBitmap(source,left,top,right-left+1,bottom-top+1) else source
                    if (trimmed !== source) source.recycle()
                    trimmed.asImageBitmap()
                }.also { trimmedCollectionIcons[asset] = it }
            }
            }
            bitmap?.let { loaded ->
                // Crop transparent margins first, then normalize visible height (not bounding-box width).
                // The wide Little Cup emblem fits inside the compact button without stretching.
                val visibleHeight = if (group == PersonalCollectionGroup.LITTLE) 20.dp else 22.dp
                Image(loaded, contentDescription = null,
                    modifier = Modifier.height(visibleHeight).then(
                        if (group == PersonalCollectionGroup.LITTLE) Modifier.width(30.dp)
                        else Modifier.aspectRatio(loaded.width.toFloat() / loaded.height)
                    ),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    alpha = if (enabled) 1f else .38f,
                    colorFilter = if (group == PersonalCollectionGroup.BACKGROUND)
                        androidx.compose.ui.graphics.ColorFilter.tint(appearance.text) else null)
            }        } else {
            // Compact emblems, like the level-50 control: no separate captions.
            val emblem = when (group) {
                PersonalCollectionGroup.SHINY -> "✦"
                PersonalCollectionGroup.LUCKY -> "❖"
                PersonalCollectionGroup.BACKGROUND -> "▧"
                PersonalCollectionGroup.IV0 -> "00%"
                else -> group.shortLabel
            }
            Text(emblem,color = foreground,fontWeight = FontWeight.Bold,
                fontSize = when (group) {
                    PersonalCollectionGroup.NORMAL -> 8.sp
                    PersonalCollectionGroup.SHINY, PersonalCollectionGroup.LUCKY, PersonalCollectionGroup.BACKGROUND -> 20.sp
                    else -> 10.sp
                },
                maxLines = 1)
        }
        if (checked) Icon(AppIcons.Selected,contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,modifier = Modifier.align(Alignment.BottomEnd).size(9.dp))
    }
}
internal data class PersonalCollectionTab(val title: String, val groups: Set<PersonalCollectionGroup>)
internal val personalCollectionTabs = listOf(
    PersonalCollectionTab("Brilhante", setOf(PersonalCollectionGroup.SHINY)),
    PersonalCollectionTab("Sortudo", setOf(PersonalCollectionGroup.LUCKY)),
    PersonalCollectionTab("Sombroso / Purificado", setOf(PersonalCollectionGroup.SHADOW,PersonalCollectionGroup.PURIFIED)),
    PersonalCollectionTab("Tamanho", setOf(PersonalCollectionGroup.XXS,PersonalCollectionGroup.XXL)),
    PersonalCollectionTab("IV", setOf(PersonalCollectionGroup.IV100,PersonalCollectionGroup.IV98,
        PersonalCollectionGroup.IV96,PersonalCollectionGroup.IV93,PersonalCollectionGroup.IV91,PersonalCollectionGroup.IV67,PersonalCollectionGroup.IV0)),
    PersonalCollectionTab("PVP", setOf(PersonalCollectionGroup.LITTLE,PersonalCollectionGroup.GREAT,PersonalCollectionGroup.ULTRA,PersonalCollectionGroup.MASTER_LEAGUE)),
    PersonalCollectionTab("Dinamax", setOf(PersonalCollectionGroup.DYNAMAX)),
    PersonalCollectionTab("Gigamax", setOf(PersonalCollectionGroup.GIGANTAMAX)),
    PersonalCollectionTab("Mega", setOf(PersonalCollectionGroup.MEGA))
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PersonalCollectionPreferences() {
    val context = LocalContext.current
    val snapshot = rememberPersonalCollection()
    var order by remember { mutableStateOf(snapshot.order) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var dragged by remember { mutableStateOf<PersonalCollectionGroup?>(null) }
    LaunchedEffect(snapshot.order) {
        if (dragged == null) order = snapshot.order
    }
    var dragTop by remember { mutableFloatStateOf(0f) }
    var dragHeight by remember { mutableFloatStateOf(0f) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    val edge = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    fun reorderAtPointer() {
        val group = dragged ?: return
        val center = dragTop + dragHeight / 2
        val currentIndex = order.indexOf(group)
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            val index = order.indexOfFirst { it.name == item.key }
            index >= 0 && index != currentIndex &&
                if (index > currentIndex) center > item.offset + item.size / 2 && center < item.offset + item.size
                else center < item.offset + item.size / 2 && center > item.offset
        } ?: return
        val targetIndex = order.indexOfFirst { it.name == target.key }
        order = order.toMutableList().apply { add(targetIndex, removeAt(currentIndex)) }
    }
    LaunchedEffect(dragged) {
        if (dragged == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val info = listState.layoutInfo
            val distance = when {
                pointerY < info.viewportStartOffset + edge -> -12f
                pointerY > info.viewportEndOffset - edge -> 12f
                else -> 0f
            }
            if (distance != 0f) listState.scrollBy(distance)
            reorderAtPointer()
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(bottom = LocalNavigationContentPadding.current),
        state = listState,
        // The list's own scroll sees pointer events before this modifier and would take the drag after the long press.
        userScrollEnabled = dragged == null,
        modifier = Modifier.fillMaxSize().pointerInput(listState) {
            detectDragGesturesAfterLongPress(
                onDragStart = { point ->
                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                        point.y >= it.offset && point.y < it.offset + it.size && order.any { g -> g.name == it.key }
                    }
                    dragged = order.firstOrNull { it.name == item?.key }
                    if (dragged != null && item != null) {
                        dragTop = item.offset.toFloat()
                        dragHeight = item.size.toFloat()
                        pointerY = point.y
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    }
                },
                onDrag = { change, amount ->
                    if (dragged != null) {
                        change.consume()
                        pointerY = change.position.y
                        dragTop += amount.y
                        reorderAtPointer()
                    }
                },
                onDragEnd = {
                    if (dragged != null) PersonalCollection.setOrder(context, order)
                    dragged = null
                },
                onDragCancel = {
                    dragged = null
                    order = PersonalCollection.read(context).order
                }
            )
        },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "visibility") {
            AppToggleRow("Mostrar coleção nos nomes sugeridos", snapshot.showInSuggestedNames,
                { PersonalCollection.setShowInSuggestedNames(context, it) })
        }
        item(key = "instructions") {
            Text("Escolha os grupos que quer colecionar. Segure e arraste um bloco para mudar a ordem dos ícones nos nomes sugeridos. Ocultar um grupo não apaga suas capturas.", style = MaterialTheme.typography.bodySmall)
        }
        items(order, key = { it.name }) { group ->
            val isDragging = dragged == group
            val offset = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == group.name }?.offset ?: 0
            AppToggleRow(group.label, group in snapshot.enabled,
                { PersonalCollection.setEnabled(context, group, it) },
                // The others slide to their new place; the held row follows the finger instead.
                modifier = (if (isDragging) Modifier else Modifier.animateItemPlacement()).zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragTop - offset else 0f
                        val scale = if (isDragging) 1.03f else 1f
                        scaleX = scale; scaleY = scale
                        shadowElevation = if (isDragging) 12.dp.toPx() else 0f
                    }.semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("Mover para cima") {
                                val index = order.indexOf(group)
                                if (index > 0) {
                                    order = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                                    PersonalCollection.setOrder(context, order)
                                    true
                                } else false
                            },
                            CustomAccessibilityAction("Mover para baixo") {
                                val index = order.indexOf(group)
                                if (index < order.lastIndex) {
                                    order = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                                    PersonalCollection.setOrder(context, order)
                                    true
                                } else false
                            }
                        )
                    })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun PersonalCollectionScreen(
    onBack: () -> Unit,
    overlay: Boolean = false,
    panel: Boolean = false,
    registerBackAction: ((() -> Unit) -> Unit)? = null
) {
    val mark = rememberCollectionWriter()
    val context = LocalContext.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val snapshot = rememberPersonalCollection()
    var section by rememberSaveable { mutableStateOf(0) }
    var compactRows by rememberSaveable { mutableStateOf(
        context.getSharedPreferences("mewname_prefs", android.content.Context.MODE_PRIVATE)
            .getBoolean("collection_overlay_compact_rows", false)) }
    val layoutToggle: @Composable () -> Unit = {
        if (overlay && (section == 0 || section == 3)) CollectionLayoutToggle(compactRows) {
            compactRows = it
            context.getSharedPreferences("mewname_prefs", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("collection_overlay_compact_rows", it).apply()
        }
    }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var backgroundFiltersActive by remember { mutableStateOf(false) }
    var stickerFiltersActive by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var onlyCollected by rememberSaveable { mutableStateOf(false) }
    var quickGroupName by rememberSaveable { mutableStateOf<String?>(PersonalCollectionGroup.NORMAL.name) }
    val quickGroup = if (section == 3) PersonalCollectionGroup.NORMAL else if (panel) PersonalCollectionGroup.entries.firstOrNull { it.name == quickGroupName } else null
    var selectedDex by rememberSaveable { mutableStateOf<Int?>(null) }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val compactListState = androidx.compose.foundation.lazy.rememberLazyListState()
    SideEffect { registerBackAction?.invoke { if (selectedDex != null) selectedDex = null else onBack() } }
    // Quick marks only change flags on targets already present in this catalog.
    // Keep its seed stable during the panel session instead of reparsing all assets per tap.
    val initialTargets = remember { snapshot.entries.values.map { it.target } }
    val savedTargets = if (panel) initialTargets else remember(snapshot.entries) { snapshot.entries.values.map { it.target } }
    val species by produceState<List<com.mewname.app.domain.CollectionSpecies>?>(null, savedTargets) {
        value = withContext(Dispatchers.IO) { com.mewname.app.domain.CollectionDexCatalog.load(context, savedTargets) }
    }
    val images by produceState<com.mewname.app.domain.PersonalCollectionImages?>(null) {
        value = withContext(Dispatchers.IO) { com.mewname.app.domain.PersonalCollectionImages(context.applicationContext) }
    }
    val entries = remember(species, snapshot.entries, onlyCollected) {
        species.orEmpty().filter { entry ->
            !onlyCollected || entry.targets.any { snapshot.entries[it.key]?.groups?.isNotEmpty() == true }
        }
    }
    val compactTargets = remember(species, onlyCollected, snapshot.entries, section) {
        com.mewname.app.domain.collectionCompactTargets(species.orEmpty(), "",
            if (onlyCollected) snapshot.entries.filterValues { it.groups.isNotEmpty() }.keys else null, costumesOnly = section == 3)
    }
    val obtainedForGroup = remember(snapshot.entries, quickGroup, onlyCollected) {
        if (onlyCollected) snapshot.entries.filterValues { quickGroup in it.groups }.keys else null
    }
    val quickTargets = remember(species, quickGroup, obtainedForGroup, section) {
        quickGroup?.let { com.mewname.app.domain.collectionQuickTargets(species.orEmpty(), it, "", obtainedForGroup, costumesOnly = section == 3) }.orEmpty()
    }
    // Search navigates within the full list. Do not restart it on marks or returning from details.
    LaunchedEffect(query, species, compactRows, quickGroup, onlyCollected, section) {
        val search = query.trim()
        if (search.isEmpty() || (section != 0 && section != 3) || species == null) return@LaunchedEffect
        kotlinx.coroutines.delay(300)
        val candidates = when {
            overlay && compactRows -> compactTargets.map { it.first.dex to it.second.label }
            quickGroup != null -> quickTargets.map { it.first.dex to it.second.label }
            else -> entries.map { it.dex to it.name }
        }
        val index = collectionSearchIndex(search, candidates)
        if (index >= 0) {
            if (overlay && compactRows) compactListState.scrollToItem(index)
            else gridState.scrollToItem(index)
        }
    }
    if (!overlay || selectedDex == null) Scaffold(topBar = {
        if (!panel) AppTopBar(title = { Text("Coleções") }, navigationIcon = {
            if (!overlay) IconButton(onClick = onBack) { Icon(AppIcons.Back, "Voltar") }
        }, actions = {
            AppFilterToggle(showFilters, if (section == 1) backgroundFiltersActive else if (section == 2) stickerFiltersActive else query.isNotBlank() || onlyCollected,
                { showFilters = it })
            layoutToggle()
            if (overlay) IconButton(onClick = onBack) { Icon(AppIcons.Close, "Fechar coleção") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppPillTabRow(selectedTabIndex = listOf(0, 3, 1, 2).indexOf(section), edgePadding = 0.dp, divider = {}) {
                listOf(0 to "Pokémon", 3 to "Fantasias", 1 to "Fundos especiais", 2 to "Adesivos").forEach { (index, title) ->
                    AppPillTab(selected = section == index, onClick = { section = index }, text = { Text(title) })
                }
            }
            if (panel) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (section == 0 || section == 3) {
                    AppGlassTextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f).padding(end = 8.dp),
                        label = { Text("Ir até Pokémon", maxLines = 1) }, singleLine = true)
                    layoutToggle()
                } else AppFilterToggle(showFilters, if (section == 1) backgroundFiltersActive else stickerFiltersActive,
                    { showFilters = it })
            }
            if (section == 2) StickerCollectionContent(showFilters) { stickerFiltersActive = it }
            else if (section == 1) CollectionBackgroundsContent(inlinePreview = overlay, showFilters = showFilters, onFilterActive = { backgroundFiltersActive = it }) else {
                if (!panel && showFilters) {
                    AppFilterPanel(query.isNotBlank() || onlyCollected, onClear = { query = ""; onlyCollected = false }) {
                        AppGlassTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                            search = true, label = { Text("Ir até Pokémon, forma ou número") }, singleLine = true)
                        AppToggleRow("Mostrar somente coletados", onlyCollected, { onlyCollected = it })
                    }
                }
                if (species == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (overlay && compactRows) {
                    LazyColumn(Modifier.weight(1f), state = compactListState, verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)) {
                        if (species != null && compactTargets.isEmpty()) item { Text("Nenhum Pokémon encontrado.") }
                        items(compactTargets, key = { it.second.key }) { (entry, target) ->
                            CollectionSpeciesRow(target, images, snapshot, mark) {
                                keyboard?.hide(); focus.clearFocus(); selectedDex = entry.dex
                            }
                        }
                    }
                } else LazyVerticalGrid(state = gridState, columns = GridCells.Adaptive(96.dp), modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp + LocalNavigationContentPadding.current)) {
                    if (quickGroup == null && species != null && entries.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("Nenhum Pokémon encontrado.") }
                    if (quickGroup != null) {
                        if (species != null && quickTargets.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("Nenhum Pokémon encontrado.") }
                        gridItems(quickTargets, key = { it.second.key }) { (entry, target) ->
                            val obtained = quickGroup in snapshot.entries[target.key]?.groups.orEmpty()
                            CollectionTile(target.label, obtained, onClick = {
                                keyboard?.hide(); focus.clearFocus()
                                mark(target, quickGroup, !obtained)
                            }, onLongClick = { keyboard?.hide(); focus.clearFocus(); selectedDex = entry.dex }) {
                                PersonalCollectionPortrait(target, images, quickGroup, compact = true)
                            }
                        }
                    } else gridItems(entries, key = { it.dex }) { entry ->
                        AppSectionCard(Modifier.fillMaxWidth().clickable { keyboard?.hide(); focus.clearFocus(); selectedDex = entry.dex }) {
                            Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                PersonalCollectionPortrait(
                                    PersonalCollectionTarget(JSONArray(listOf(entry.name.uppercase(java.util.Locale.ROOT), "")).toString(), entry.name),
                                    images, PersonalCollectionGroup.NORMAL)
                                Text(entry.dex.toString().padStart(4, '0'), style = MaterialTheme.typography.labelSmall)
                                Text(entry.name, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.fillMaxWidth(), minLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }
    species?.firstOrNull { it.dex == selectedDex }?.let { entry ->
        val content: @Composable () -> Unit = {
            AppSectionCard(Modifier.fillMaxWidth().fillMaxHeight(if (overlay) 1f else 0.88f).padding(horizontal = if (overlay) 0.dp else 12.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))) {
                Column(Modifier.padding(12.dp)) {
                    if (overlay) {
                        Box(Modifier.fillMaxWidth()) {
                            IconButton(onClick = { selectedDex = null }, modifier = Modifier.align(Alignment.CenterStart)) {
                                Icon(AppIcons.Back, "Voltar à lista")
                            }
                            Text(entry.name, Modifier.align(Alignment.Center).padding(horizontal = 48.dp),
                                textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
                            if (!panel) IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterEnd)) {
                                Icon(AppIcons.Close, "Fechar coleção")
                            }
                        }
                    } else AppModalHeader(entry.name, onClose = { selectedDex = null })
                    LazyVerticalGrid(columns = GridCells.Adaptive(100.dp), modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        entry.targets.filter { it.key !in entry.costumeKeys }.forEach { target ->
                            val formLabel = target.label.removePrefix(entry.name).trim().trimStart('·').trim()
                            if (formLabel.isNotBlank()) {
                                item(key = "heading:" + target.key, span = { GridItemSpan(maxLineSpan) }) {
                                    Text(formLabel, Modifier.padding(top = 12.dp), fontWeight = FontWeight.SemiBold)
                                }
                            }
                            val groups = (listOf(PersonalCollectionGroup.NORMAL) + snapshot.order).distinct().filter {
                                (it == PersonalCollectionGroup.NORMAL || it in snapshot.enabled || it in snapshot.entries[target.key]?.groups.orEmpty()) && PersonalCollection.supports(target, it)
                            }
                            gridItems(groups, key = { target.key + it.name }) { group ->
                                val obtained = group in snapshot.entries[target.key]?.groups.orEmpty()
                                AppSectionCard(Modifier.fillMaxWidth().clickable {
                                    mark( target, group, !obtained)
                                }.semantics {
                                    contentDescription = "${target.label}, ${group.label}: ${if (obtained) "coletado" else "não coletado"}"
                                    toggleableState = if (obtained) ToggleableState.On else ToggleableState.Off
                                }) {
                                    Column(Modifier.fillMaxWidth().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        PersonalCollectionIcon(group, obtained, true, target.label) {
                                            mark( target, group, !obtained)
                                        }
                                        PersonalCollectionPortrait(target, images, group, silhouette = !obtained)
                                        Text(group.label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.fillMaxWidth(), minLines = 2)
                                    }
                                }
                            }
                        }
                        val costumes = entry.targets.filter { it.key in entry.costumeKeys }
                        if (costumes.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text("Fantasiados", Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                            }
                            gridItems(costumes, key = { "costume:" + it.key }) { target ->
                                val obtained = snapshot.entries[target.key]?.groups.orEmpty()
                                AppSectionCard(Modifier.fillMaxWidth().clickable {
                                    if (obtained.isEmpty()) mark( target, PersonalCollectionGroup.NORMAL, true)
                                    else obtained.forEach { mark( target, it, false) }
                                }.semantics {
                                    contentDescription = "${target.label}: ${if (obtained.isEmpty()) "não coletado" else "coletado"}"
                                    toggleableState = if (obtained.isEmpty()) ToggleableState.Off else ToggleableState.On
                                }) {
                                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                        PersonalCollectionPortrait(target, images, PersonalCollectionGroup.NORMAL, silhouette = obtained.isEmpty())
                                        if (obtained.isNotEmpty()) Icon(AppIcons.Selected, null,
                                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (overlay) content() else androidx.compose.ui.window.Dialog(
            onDismissRequest = { selectedDex = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) { content() }
    }
}
@Composable
internal fun PersonalCollectionPortrait(target: PersonalCollectionTarget, images: com.mewname.app.domain.PersonalCollectionImages?, group: PersonalCollectionGroup, silhouette: Boolean = false, compact: Boolean = false) {
    val context = LocalContext.current
    // A tab change gets fresh state immediately; never flash the previous category's image.
    key(target.key, group, images) {
        val urls by produceState<List<String>>(emptyList()) {
            value = withContext(Dispatchers.IO) { images?.urls(target, group).orEmpty() }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            urls.ifEmpty { listOf("") }.forEach { url ->
                val request = remember(url, context) {
                    coil.request.ImageRequest.Builder(context).data(url.takeIf { it.isNotBlank() })
                        .transformations(CollectionSpriteTrim).crossfade(true).build()
                }
                Box(Modifier.size(if (compact) 64.dp else 80.dp), contentAlignment = Alignment.Center) {
                if (group == PersonalCollectionGroup.SHADOW && !silhouette && url.isNotBlank()) CollectionShadowAura()
                coil.compose.AsyncImage(
                    model = request,
                    contentDescription = target.label,
                    colorFilter = if (silhouette) androidx.compose.ui.graphics.ColorFilter.tint(LocalAppAppearance.current.text.copy(alpha = 0.35f)) else null,
                    modifier = Modifier.size(when (group) {
                        PersonalCollectionGroup.XXS -> if (compact) 36.dp else 48.dp
                        PersonalCollectionGroup.XXL -> if (compact) 60.dp else 76.dp
                        else -> 64.dp
                    }),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    placeholder = collectionPlaceholder(),
                    error = collectionPlaceholder()
                )
                }
            }
        }
    }
}
@Composable
internal fun CollectionShadowAura() {
    val context = LocalContext.current
    // Shared by every shadow tile: decoding the whole sheet per tile churned memory while scrolling.
    val frame by produceState(shadowAuraFrame) {
        value = shadowAuraFrame ?: withContext(Dispatchers.IO) {
            context.assets.open("collection/shadow_flame_sheet.png").use { stream ->
                val sheet = requireNotNull(BitmapFactory.decodeStream(stream))
                val frame = android.graphics.Bitmap.createBitmap(sheet, sheet.width / 4, sheet.height / 4, sheet.width / 4, sheet.height / 4)
                sheet.recycle()
                frame.asImageBitmap()
            }
        }.also { shadowAuraFrame = it }
    }
    frame?.let { Image(it, null, Modifier.size(80.dp),
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(androidx.compose.ui.graphics.Color(0xFF8042BC)), alpha = 0.8f) }
}

/** Crop only transparent padding; retain the game's original sprite pixels and proportions. */
internal object CollectionSpriteTrim : coil.transform.Transformation {
    override val cacheKey = "collection-go-sprite-trim-v1"
    override suspend fun transform(input: android.graphics.Bitmap, size: coil.size.Size): android.graphics.Bitmap {
        val pixels = IntArray(input.width * input.height)
        input.getPixels(pixels, 0, input.width, 0, 0, input.width, input.height)
        var left = input.width; var top = input.height; var right = -1; var bottom = -1
        for (y in 0 until input.height) for (x in 0 until input.width) {
            if ((pixels[y * input.width + x] ushr 24) > 16) {
                left = minOf(left, x); top = minOf(top, y)
                right = maxOf(right, x); bottom = maxOf(bottom, y)
            }
        }
        return if (right >= left && bottom >= top)
            android.graphics.Bitmap.createBitmap(input, left, top, right - left + 1, bottom - top + 1) else input
    }
}

@Composable
private fun CollectionLayoutToggle(compact: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(appControlFill(false)).selectableGroup()) {
        listOf(false, true).forEach { rows ->
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                .background(if (compact == rows) appControlFill(true) else androidx.compose.ui.graphics.Color.Transparent)
                .selectable(selected = compact == rows, role = Role.RadioButton, onClick = { onChange(rows) }),
                contentAlignment = Alignment.Center) {
                Icon(if (rows) androidx.compose.ui.graphics.vector.rememberVectorPainter(AppIcons.List) else androidx.compose.ui.res.painterResource(R.drawable.fa_table_cells_large),
                    if (rows) "Lista compacta por espécie" else "Grade de Pokémon", Modifier.size(22.dp), tint = appControlInk())
            }
        }
    }
}

@Composable
private fun CollectionSpeciesRow(
    target: PersonalCollectionTarget,
    images: com.mewname.app.domain.PersonalCollectionImages?,
    snapshot: PersonalCollectionSnapshot,
    mark: (PersonalCollectionTarget, PersonalCollectionGroup, Boolean) -> Unit,
    onDetails: () -> Unit
) {
    val context = LocalContext.current
    val url by produceState<String?>(null, target.key, images) {
        value = withContext(Dispatchers.IO) { images?.urls(target, PersonalCollectionGroup.NORMAL)?.firstOrNull() }
    }
    val request = remember(url, context) {
        coil.request.ImageRequest.Builder(context).data(url).transformations(CollectionSpriteTrim).build()
    }
    val obtained = snapshot.entries[target.key]?.groups.orEmpty()
    AppSectionCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            coil.compose.AsyncImage(request, "${target.label}: abrir formas",
                Modifier.size(40.dp).clickable(onClick = onDetails),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                placeholder = collectionPlaceholder(),
                error = collectionPlaceholder())
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                snapshot.order.filter { it in snapshot.enabled && PersonalCollection.supports(target, it) }.forEach { group ->
                    PersonalCollectionIcon(group, group in obtained, true, target.label) {
                        mark(target, group, group !in obtained)
                    }
                }
            }
        }
    }
}

/** Small bundled silhouette: no network access while a sprite loads or is unavailable. */
@Composable
private fun collectionPlaceholder(): androidx.compose.ui.graphics.painter.Painter {
    val context = LocalContext.current
    val bitmap = remember(context) {
        requireNotNull(AssetBitmapCache.get(context, "filter_icons/no_icon_pikachu.png"))
    }
    val tint = appControlInk().copy(alpha = .55f)
    return remember(bitmap, tint) {
        object : androidx.compose.ui.graphics.painter.Painter() {
            override val intrinsicSize = androidx.compose.ui.geometry.Size(bitmap.width.toFloat(), bitmap.height.toFloat())
            override fun androidx.compose.ui.graphics.drawscope.DrawScope.onDraw() {
                drawImage(bitmap, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint))
            }
        }
    }
}
