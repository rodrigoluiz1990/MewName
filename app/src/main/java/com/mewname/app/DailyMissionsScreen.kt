package com.mewname.app

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DailyMissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { DailyMissionStore(context) }
    val icons = remember { missionIcons(context) }
    val language = appLanguage()
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
    var editor by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf("mission_icons/pokeball_sprite.png") }
    var search by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<DailyMission?>(null) }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        AppTopBar(title = { Text(tr("Missões diárias", "Daily missions", "Misiones diarias")) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Voltar", "Back", "Volver")) }
        }, actions = { IconButton(onClick = {
            editingId = null; name = ""; search = ""; icon = "mission_icons/pokeball_sprite.png"; editor = true
        }) { Icon(Icons.Default.Add, tr("Adicionar missão", "Add mission", "Añadir misión")) } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, bottom = LocalNavigationContentPadding.current + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (missions.isEmpty()) item { Text(tr("Adicione sua primeira missão no botão +.", "Add your first mission with +.", "Añade tu primera misión con +.")) }
            items(missions, key = { it.id }) { mission ->
                AppSectionCard {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage("file:///android_asset/${mission.icon}", mission.name, Modifier.size(48.dp))
                        Column(Modifier.weight(1f).clickable {
                            editingId = mission.id; name = mission.name; icon = mission.icon; search = ""; editor = true
                        }.padding(8.dp)) {
                            Text(mission.name)
                            Text(if (mission.completed(today)) tr("Concluída hoje", "Completed today", "Completada hoy")
                                else tr("Pendente", "Pending", "Pendiente"), style = MaterialTheme.typography.bodySmall)
                        }
                        AppToggle(mission.completed(today), { store.complete(mission.id, it) })
                        IconButton(onClick = { deleting = mission }) { Icon(Icons.Default.Delete, tr("Excluir", "Delete", "Eliminar")) }
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
                        Icon(Icons.Default.Close, tr("Fechar", "Close", "Cerrar"))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AsyncImage("file:///android_asset/$icon", missionIconLabel(icon), Modifier.size(40.dp))
                    AppGlassTextField(name, { name = it }, Modifier.weight(1f),
                        label = { Text(tr("Nome da missão", "Mission name", "Nombre de la misión")) }, singleLine = true)
                }
                AppGlassTextField(search, { search = it }, Modifier.fillMaxWidth(),
                    label = { Text(tr("Buscar ícone", "Find icon", "Buscar icono")) }, singleLine = true, search = true)
                val filtered = icons.filter { missionIconLabel(it).contains(search, true) }
                AppSectionCard(Modifier.fillMaxWidth().weight(1f)) {
                    if (filtered.isEmpty()) Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(tr("Nenhum ícone encontrado", "No icons found", "No se encontraron iconos"))
                    } else LazyVerticalGrid(GridCells.Adaptive(56.dp), Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filtered, key = { it }) { path ->
                            Surface(onClick = { icon = path },
                                color = appControlFill(icon == path),
                                border = if (icon == path) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
                                AsyncImage("file:///android_asset/$path", missionIconLabel(path), Modifier.padding(8.dp).size(40.dp))
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    AppActionButton(enabled = name.isNotBlank() && icon.isNotBlank(), modifier = Modifier.widthIn(min = 140.dp), onClick = {
                        val old = missions.find { it.id == editingId }
                        store.save(old?.copy(name = name.trim(), icon = icon) ?: DailyMission(name = name.trim(), icon = icon))
                        editor = false
                    }) { Text(tr("Salvar", "Save", "Guardar")) }
                }
            }
        }
    }
    deleting?.let { mission -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(tr("Excluir missão?", "Delete mission?", "¿Eliminar misión?")) },
        text = { Text(mission.name) }, confirmButton = { TextButton(onClick = { store.delete(mission.id); deleting = null }) { Text(tr("Excluir", "Delete", "Eliminar")) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("Cancelar", "Cancel", "Cancelar")) } }) }
}