package com.mewname.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
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
    onClose: () -> Unit
) {
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    Surface(Modifier.fillMaxWidth().heightIn(max = maxHeight), shape = RoundedCornerShape(26.dp), color = Color.Transparent) {
        Column(Modifier.background(Brush.verticalGradient(LocalAppAppearance.current.background))) {
            CompositionLocalProvider(LocalProfilePanel provides true) {
                AppTopBar(
                    title = { Text(if (friends) tr("Filtros de amigos", "Friend filters", "Filtros de amigos")
                        else tr("Filtros de Pokémon", "Pokémon filters", "Filtros de Pokémon")) },
                    navigationIcon = { IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, tr("Fechar", "Close", "Cerrar"))
                    } }
                )
            }
            if (filters.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.List, contentDescription = null,
                        modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(tr("Nenhum filtro salvo", "No saved filters", "No hay filtros guardados"),
                        style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Text(tr("Salve filtros desta categoria na tela Filtros do MewName.",
                        "Save filters for this category on MewName’s Filters screen.",
                        "Guarda filtros de esta categoría en la pantalla Filtros de MewName."),
                        style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(16.dp)) {
                    items(filters, key = { it.id }) { filter ->
                        AppSectionCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().clickable(
                                role = Role.Button,
                                onClickLabel = tr("Copiar filtro", "Copy filter", "Copiar filtro"),
                                onClick = { onCopy(filter.value) }
                            ).heightIn(min = 56.dp).padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(filter.value, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                Icon(SavedFilterCopyIcon, contentDescription = null,
                                    modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
private val SavedFilterCopyIcon = materialIcon(name = "SavedFilterCopy") {
    materialPath {
        moveTo(16f, 1f); horizontalLineTo(4f); verticalLineTo(17f)
        horizontalLineTo(6f); verticalLineTo(3f); horizontalLineTo(16f); close()
        moveTo(8f, 5f); verticalLineTo(23f); horizontalLineTo(22f)
        verticalLineTo(5f); close()
        moveTo(10f, 7f); horizontalLineTo(20f); verticalLineTo(21f)
        horizontalLineTo(10f); close()
    }
}