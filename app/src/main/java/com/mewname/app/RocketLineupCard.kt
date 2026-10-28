package com.mewname.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mewname.app.domain.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RocketLineupCard(entry: LeekEntry, title: String, quote: String, offline: Boolean) {
    val language = appLanguage()
    fun tr(pt: String,en: String,es: String) = lt(language,pt,en,es)
    var selectedNames by rememberSaveable(entry.id) { mutableStateOf(arrayListOf("", "", "")) }
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(88.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AsyncImage(leekImageRequest(entry.image, offline), null, Modifier.size(64.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            }
            if (quote.isNotBlank()) AppSectionCard(Modifier.weight(1f),
                shape = RoundedCornerShape(topStart=4.dp, topEnd=20.dp, bottomEnd=20.dp, bottomStart=20.dp)) {
                Text("“$quote”", Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        entry.slots.forEachIndexed { index, slot ->
            Text("${slot.position}" + if (slot.encounter) " · " + tr("Pode ser capturado","Catchable","Se puede capturar") else "",
                style = MaterialTheme.typography.titleSmall)
            slot.pokemon.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { pokemon ->
                        val chosen = selectedNames.getOrNull(index) == pokemon.name
                        AppSectionCard(Modifier.weight(1f)) {
                            Column(Modifier.fillMaxWidth().semantics { selected = chosen }
                                .clickable(role=Role.RadioButton) {
                                    selectedNames = ArrayList(selectedNames).apply {
                                        while (size <= index) add("")
                                        set(index, if (chosen) "" else pokemon.name)
                                    }
                                }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    AsyncImage(leekImageRequest(pokemon.image, offline, trimSprite = true), null, Modifier.size(64.dp))
                                    if (chosen) Icon(AppIcons.Selected, tr("Selecionado","Selected","Seleccionado"),
                                        Modifier.size(18.dp).align(Alignment.TopEnd), tint=MaterialTheme.colorScheme.primary)
                                }
                                Text(pokemon.name, style=MaterialTheme.typography.labelLarge, textAlign=TextAlign.Center)
                                FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                    rocketWeaknessTypes(pokemon.detail).forEach { type ->
                                        AsyncImage("file:///android_asset/types/POKEMON_TYPE_${type.uppercase()}.png",
                                            localizedTypeLabel(type, language), Modifier.size(22.dp))
                                    }
                                }
                            }
                        }
                    }
                    repeat(3-row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        val selected = entry.slots.mapIndexed { i, slot -> slot.pokemon.firstOrNull { it.name == selectedNames.getOrNull(i) } }
        RocketMovesetSuggestions(selected.filterNotNull())
    }
}
