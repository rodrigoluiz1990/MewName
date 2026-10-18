package com.mewname.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp

@Composable
internal fun AppFilterToggle(expanded: Boolean, active: Boolean, onToggle: (Boolean) -> Unit) {
    val focus = LocalFocusManager.current
    val language = appLanguage()
    IconToggleButton(checked = expanded, onCheckedChange = {
        onToggle(it)
        if (!it) focus.clearFocus()
    }) {
        BadgedBox(badge = { if (active) Badge() }) {
            Icon(Icons.Default.Search, lt(language,
                if (expanded) "Ocultar filtros" else "Mostrar filtros",
                if (expanded) "Hide filters" else "Show filters",
                if (expanded) "Ocultar filtros" else "Mostrar filtros"))
        }
    }
}

@Composable
internal fun AppFilterPanel(active: Boolean, onClear: () -> Unit, showClear: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    AppSectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
            if (showClear) TextButton(onClick = onClear, enabled = active, modifier = Modifier.align(Alignment.End)) {
                Text(lt(appLanguage(), "Limpar filtros", "Clear filters", "Borrar filtros"))
            }
        }
    }
}
