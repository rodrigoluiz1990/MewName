package com.mewname.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared saved-filter panel for Pokémon and friends, including service overlays. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedFiltersOverlay(
    friends: Boolean,
    filters: List<SavedFilterEntry>,
    maxHeight: Dp,
    onCopy: (String) -> Unit,
    onClose: () -> Unit,
    onQuickEdit: (String) -> Unit
) {
    val context = LocalContext.current
    var editing by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var displayedFilters by remember { mutableStateOf(filters) }
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    Surface(Modifier.fillMaxWidth().heightIn(max = maxHeight).then(if (editing || editingId != null) Modifier.height(maxHeight) else Modifier), shape = RoundedCornerShape(26.dp), color = Color.Transparent) {
        Column(Modifier.background(Brush.verticalGradient(LocalAppAppearance.current.background))) {
            CompositionLocalProvider(LocalProfilePanel provides true) {
                AppTopBar(
                    title = { Text(if (friends) tr("Filtros de amigos", "Friend filters", "Filtros de amigos")
                        else tr("Filtros de Pokémon", "Pokémon filters", "Filtros de Pokémon")) },
                    navigationIcon = { IconButton(onClick = {
                        if (editing || editingId != null) {
                            editingId = null
                            editing = false
                            displayedFilters = loadSavedFilters(context, if (friends) SAVED_PEOPLE_FILTERS_KEY else SAVED_POKEMON_FILTERS_KEY)
                        } else onClose()
                    }) {
                        Icon(if (editing || editingId != null) AppIcons.Back else AppIcons.Close, tr("Voltar", "Back", "Volver"))
                    } },
                    actions = {
                        if (!editing && editingId == null) IconButton(onClick = { draft = ""; editing = true }) {
                            Icon(AppIcons.Add, tr("Criar filtro", "Create filter", "Crear filtro"))
                        } else IconButton(onClick = onClose) { Icon(AppIcons.Close, tr("Fechar", "Close", "Cerrar")) }
                    }
                )
            }
            val selectedFilter = displayedFilters.firstOrNull { it.id == editingId }
            if (editing || selectedFilter != null) {
                Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(12.dp)) {
                    val save: (String) -> Unit = { value ->
                        val storageKey = if (friends) SAVED_PEOPLE_FILTERS_KEY else SAVED_POKEMON_FILTERS_KEY
                        if (selectedFilter != null) updateSavedFilter(context, storageKey, selectedFilter.id, value)
                        else saveNewFilter(context, storageKey, value)
                        displayedFilters = loadSavedFilters(context, storageKey)
                        editingId = null; editing = false
                    }
                    androidx.compose.runtime.key(editingId, editing) {
                        if (friends) PeopleFilterBuilder(context, language, onCopyFilter = onCopy,
                            initialValue = selectedFilter?.value.orEmpty(), onSaveFilter = save, showSaved = false)
                        else PokemonFilterBuilder(context, language, onCopyFilter = onCopy,
                            initialValue = selectedFilter?.value.orEmpty(), onSaveFilter = save, showSaved = false)
                    }
                }
            } else if (displayedFilters.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(AppIcons.List, contentDescription = null,
                        modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(tr("Nenhum filtro salvo", "No saved filters", "No hay filtros guardados"),
                        style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Text(tr("Toque em + para criar um filtro aqui.",
                        "Save filters for this category on MewName’s Filters screen.",
                        "Guarda filtros de esta categoría en la pantalla Filtros de MewName."),
                        style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(16.dp)) {
                    items(displayedFilters, key = { it.id }) { filter ->
                        AppSectionCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().clickable(
                                role = Role.Button,
                                onClickLabel = tr("Ajustar filtro", "Edit filter", "Ajustar filtro"),
                                onClick = { onQuickEdit(filter.value) }
                            ).heightIn(min = 56.dp).padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                Text(filter.value, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                IconButton(onClick = { onCopy(filter.value) }) { Icon(AppIcons.Copy, contentDescription = tr("Copiar", "Copy", "Copiar"), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = { editingId = filter.id }) {
                                    Icon(AppIcons.Edit, tr("Ajustar filtro", "Edit filter", "Ajustar filtro"))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
internal fun SavedFilterEditor(entry: SavedFilterEntry, onSave: (String) -> Unit, onClose: () -> Unit, inline: Boolean = false) {
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    var value by rememberSaveable(entry.id) { mutableStateOf(entry.value) }
    val content: @Composable () -> Unit = {
        Surface(shape = RoundedCornerShape(26.dp), color = Color.Transparent) {
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(LocalAppAppearance.current.background))
                .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppModalHeader(tr("Editar filtro salvo", "Edit saved filter", "Editar filtro guardado"), onClose = onClose)
                AppGlassTextField(value, { value = it }, Modifier.fillMaxWidth(),
                    label = { Text(tr("Filtro", "Filter", "Filtro")) })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    AppActionButton(onClick = { onSave(value) }, enabled = value.isNotBlank()) {
                        Text(tr("Salvar", "Save", "Guardar"))
                    }
                }
            }
        }
    }
    if (inline) content() else androidx.compose.ui.window.Dialog(onDismissRequest = onClose) { content() }
}
