package com.mewname.app

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun DailyMissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val language = appLanguage()
    val store = remember { DailyMissionStore(context) }
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    var missions by remember { mutableStateOf(store.all()) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    DisposableEffect(store) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> missions = store.all() }
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(Unit) { while (true) { today = LocalDate.now(); delay(1000) } }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<DailyMission?>(null) }
    var editor by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf("mission_icons/pokeball_sprite.png") }
    var search by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    var draggedId by remember { mutableStateOf<String?>(null) }
    var draggedTop by remember { mutableFloatStateOf(0f) }
    var draggedHeight by remember { mutableIntStateOf(0) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    fun moveDraggedMission() {
        val id = draggedId ?: return
        val visible = listState.layoutInfo.visibleItemsInfo
        val ids = missions.map { it.id }
        val to = reorderStepTarget(ids, id, draggedTop + draggedHeight / 2f) { key ->
            visible.firstOrNull { it.key == key }?.let { it.offset + it.size / 2f }
        } ?: return
        val from = ids.indexOf(id)
        if (from >= 0 && from != to) {
            store.move(id, to - from)
            missions = store.all()
        }
    }
    LaunchedEffect(draggedId) {
        while (draggedId != null) {
            val layout = listState.layoutInfo
            val top = layout.viewportStartOffset + edge
            val bottom = layout.viewportEndOffset - layout.afterContentPadding - edge
            val speed = when {
                pointerY < top -> -((top - pointerY) / edge).coerceIn(0f, 1f) * 20f
                pointerY > bottom -> ((pointerY - bottom) / edge).coerceIn(0f, 1f) * 20f
                else -> 0f
            }
            if (speed != 0f) { listState.scrollBy(speed); moveDraggedMission() }
            delay(16)
        }
    }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        AppTopBar(title = { Text(tr("Missões diárias", "Daily missions", "Misiones diarias")) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(AppIcons.Back, tr("Voltar", "Back", "Volver")) }
        }, actions = {
            IconButton(onClick = {
            editingId = null; name = ""; search = ""; icon = "mission_icons/pokeball_sprite.png"; editor = true
        }) { Icon(AppIcons.Add, tr("Adicionar missão", "Add mission", "Añadir misión")) } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                    held.position.y >= it.offset && held.position.y < it.offset + it.size
                } ?: return@awaitEachGesture
                draggedId = item.key as? String
                draggedTop = item.offset.toFloat()
                draggedHeight = item.size
                pointerY = held.position.y
                try {
                    while (true) {
                        // Claim movement before the list's ordinary scrolling recognizer.
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes
                            .firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) { change.consume(); break }
                        draggedTop += change.positionChange().y
                        pointerY = change.position.y
                        change.consume()
                        moveDraggedMission()
                    }
                } finally { draggedId = null }
            }
        }, state = listState, contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, bottom = LocalNavigationContentPadding.current + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (missions.isEmpty()) item { Text(tr("Adicione sua primeira missão no botão +.", "Add your first mission with +.", "Añade tu primera misión con +.")) }
            items(missions, key = { it.id }) { mission ->
                val dragging = draggedId == mission.id
                // The others slide to their new place; the held card follows the finger instead.
                AppSectionCard((if (dragging) Modifier else Modifier.animateItemPlacement()).zIndex(if (dragging) 1f else 0f).graphicsLayer {
                    translationY = if (dragging) draggedTop -
                        (listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == mission.id }?.offset ?: draggedTop.toInt()) else 0f
                    val scale = if (dragging) 1.03f else 1f
                    scaleX = scale; scaleY = scale
                    shadowElevation = if (dragging) 12.dp.toPx() else 0f
                    alpha = if (dragging) .95f else 1f
                }) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (mission.icon.isNotBlank()) FilterTagIcon(mission.icon, Modifier.size(48.dp), mission.name)
                        Column(Modifier.weight(1f).clickable {
                            editingId = mission.id; name = mission.name; icon = mission.icon; search = ""; editor = true
                        }.padding(8.dp)) {
                            Text(mission.name)
                            Text(if (mission.completed(today)) tr("Concluída hoje", "Completed today", "Completada hoy")
                                else tr("Pendente", "Pending", "Pendiente"), style = MaterialTheme.typography.bodySmall)
                        }
                        AppToggle(mission.completed(today), { store.complete(mission.id, it) })
                        IconButton(onClick = { deleting = mission }) { Icon(AppIcons.Delete, tr("Excluir", "Delete", "Eliminar")) }
                    }
                }
            }
        }
    }
    if (editor) androidx.compose.ui.window.Dialog(onDismissRequest = { editor = false }) {
        val appearance = LocalAppAppearance.current
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
            color = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = appearance.text,
            border = androidx.compose.foundation.BorderStroke(.75.dp, appearance.border)) {
            Column(Modifier.fillMaxWidth().heightIn(max = 580.dp)
                .background(Brush.verticalGradient(appearance.background)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(48.dp))
                    Text(tr("Missão diária", "Daily mission", "Misión diaria"), Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { editor = false }, modifier = Modifier.size(48.dp)) {
                        Icon(AppIcons.Close, tr("Fechar", "Close", "Cerrar"))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (icon.isNotBlank()) FilterTagIcon(icon, Modifier.size(40.dp), filterIconLabel(icon))
                    AppGlassTextField(name, { name = it }, Modifier.weight(1f),
                        label = { Text(tr("Nome da missão", "Mission name", "Nombre de la misión")) }, singleLine = true)
                }
                AppGlassTextField(search, { search = it }, Modifier.fillMaxWidth(),
                    label = { Text(tr("Buscar ícone", "Find icon", "Buscar icono")) }, singleLine = true, search = true)
                SharedTaskIconPicker(icon, search, { icon = it.orEmpty() }, Modifier.fillMaxWidth().weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    AppActionButton(enabled = name.isNotBlank(), modifier = Modifier.widthIn(min = 140.dp), onClick = {
                        val old = missions.find { it.id == editingId }
                        store.save(old?.copy(name = name.trim(), icon = icon) ?: DailyMission(name = name.trim(), icon = icon))
                        editor = false
                    }) { Text(tr("Salvar", "Save", "Guardar")) }
                }
            }
        }
    }
    deleting?.let { mission ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { deleting = null }) {
            AppModalPanel(
                title = tr("Excluir missão?", "Delete mission?", "¿Eliminar misión?"),
                onClose = { deleting = null },
                actions = {
                    AppActionButton(onClick = { store.delete(mission.id); deleting = null }) {
                        Text(tr("Excluir", "Delete", "Eliminar"))
                    }
                }
            ) { Text(mission.name) }
        }
    }

}
