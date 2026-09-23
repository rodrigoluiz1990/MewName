package com.mewname.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal object BubbleActionSettings {
    val defaults = setOf("capture", "pokemon", "friends", "stop")
    val keys = defaults.toList() + bubbleAppShortcuts.map { it.key }
    fun selected(context: Context): Set<String> {
        val stored = context.getSharedPreferences("bubble_actions", 0).getStringSet("selected", null)
        return stored?.intersect(keys.toSet())?.takeIf { it.isNotEmpty() } ?: defaults
    }
    fun save(context: Context, selected: Set<String>) {
        val valid = selected.intersect(keys.toSet())
        if (valid.isNotEmpty()) context.getSharedPreferences("bubble_actions", 0)
            .edit().putStringSet("selected", valid).apply()
    }
}

@Composable
internal fun BubbleActionPreferences() {
    val context = LocalContext.current
    val language = appLanguage()
    var selected by remember { mutableStateOf(BubbleActionSettings.selected(context)) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BubbleActionSettings.keys.forEach { key ->
            val label = when (key) {
                "capture" -> lt(language, "Ler tela", "Read screen", "Leer pantalla")
                "pokemon" -> lt(language, "Filtros Pokémon", "Pokémon filters", "Filtros Pokémon")
                "friends" -> lt(language, "Filtros de amigos", "Friend filters", "Filtros de amigos")
                "stop" -> lt(language, "Remover sobreposição", "Remove overlay", "Quitar superposición")
                else -> bubbleAppShortcuts.first { it.key == key }.let { lt(language, it.pt, it.en, it.es) }
            }
            val change: (Boolean) -> Unit = { checked ->
                selected = if (checked) selected + key else selected - key
                BubbleActionSettings.save(context, selected)
            }
            val enabled = key !in selected || selected.size > 1
            AppToggleRow(label = label, checked = key in selected, enabled = enabled, onCheckedChange = change)
        }
    }
}