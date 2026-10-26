package com.mewname.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mewname.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class MoveSort { NAME, TYPE, PVE, PVP, ENERGY, DURATION, NATIONAL_DEX }

internal fun <T : Comparable<T>> compareSortValues(left: T?, right: T?, descending: Boolean): Int = when {
    left == null -> if (right == null) 0 else 1
    right == null -> -1
    descending -> right.compareTo(left)
    else -> left.compareTo(right)
}

internal fun moveComparator(order: MoveSort, language: AppLanguage, reversed: Boolean = false): Comparator<MoveCatalogEntry> {
    val name = compareBy<MoveCatalogEntry> { it.localizedName(language).lowercase(java.util.Locale.ROOT) }
    return Comparator { left, right ->
        val primary = when (order) {
            MoveSort.NAME, MoveSort.NATIONAL_DEX -> if (reversed) name.compare(right, left) else name.compare(left, right)
            MoveSort.TYPE -> compareSortValues(localizedTypeLabel(left.type, language), localizedTypeLabel(right.type, language), reversed)
            MoveSort.PVE -> compareSortValues(left.power, right.power, !reversed)
            MoveSort.PVP -> compareSortValues(left.pvpPower, right.pvpPower, !reversed)
            MoveSort.ENERGY -> compareSortValues(left.energyDelta?.let { kotlin.math.abs(it) }, right.energyDelta?.let { kotlin.math.abs(it) }, reversed)
            MoveSort.DURATION -> compareSortValues(left.duration, right.duration, reversed)
        }
        if (primary != 0) primary else name.compare(left, right)
    }
}

internal fun nationalDexNumber(pokemon: String, numbers: Map<String, Int>): Int? {
    val key = normalizeMoveLookup(pokemon)
    numbers[key]?.let { return it }
    val words = " " + key.replace('(', ' ').replace(')', ' ').replace('-', ' ').replace('_', ' ') + " "
    return numbers.entries.filter { words.contains(" " + it.key + " ") }
        .maxByOrNull { it.key.length }?.value
}

@Composable
internal fun rememberMoveNationalDex(): Map<String, Int>? {
    val context = LocalContext.current.applicationContext
    val numbers by produceState<Map<String, Int>?>(null, context) {
        value = withContext(Dispatchers.IO) {
            GameInfoRepository.loadPokedexCatalog(context).flatMap { entry ->
                (listOfNotNull(entry.name, entry.namePt, entry.nameEs) + entry.aliases)
                    .map { normalizeMoveLookup(it) to entry.number }
            }.toMap()
        }
    }
    return numbers
}

@Composable
internal fun MoveSortControl(order: MoveSort, onOrder: (MoveSort) -> Unit,
    reversed: Boolean = false, onReversed: (Boolean) -> Unit = {}, allowNationalDex: Boolean = false) {
    val language = appLanguage()
    fun label(option: MoveSort) = when (option) {
        MoveSort.NAME -> lt(language, "Nome", "Name", "Nombre")
        MoveSort.TYPE -> lt(language, "Tipo", "Type", "Tipo")
        MoveSort.PVE -> lt(language, "Poder PvE", "PvE power", "Poder PvE")
        MoveSort.PVP -> lt(language, "Poder PvP", "PvP power", "Poder PvP")
        MoveSort.ENERGY -> lt(language, "Energia", "Energy", "Energía")
        MoveSort.DURATION -> lt(language, "Duração", "Duration", "Duración")
        MoveSort.NATIONAL_DEX -> lt(language, "Pokédex nacional", "National Pokédex", "Pokédex nacional")
    }
    val descending = (order == MoveSort.PVE || order == MoveSort.PVP) xor reversed
    var expanded by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val appearance = LocalAppAppearance.current
    val menuShape = RoundedCornerShape(20.dp)
    val options = MoveSort.entries.filter { allowNationalDex || it != MoveSort.NATIONAL_DEX }
        .sortedWith(compareBy(java.text.Collator.getInstance(when (language) {
            AppLanguage.PT_BR -> java.util.Locale.forLanguageTag("pt-BR")
            AppLanguage.EN -> java.util.Locale.ENGLISH
            AppLanguage.ES -> java.util.Locale.forLanguageTag("es")
        })) { label(it) })
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).onSizeChanged { anchorWidth = it.width }) {
            Surface(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color = appControlFill(), contentColor = appControlInk(),
                border = androidx.compose.foundation.BorderStroke(.75.dp, LocalAppAppearance.current.border)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(lt(language, "Ordenar por", "Sort by", "Ordenar por"), style = MaterialTheme.typography.labelSmall)
                        Text(label(order), style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(Icons.Default.ArrowDropDown, null)
                }
            }
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(surface = Color.Transparent, surfaceTint = Color.Transparent),
                shapes = MaterialTheme.shapes.copy(extraSmall = menuShape)
            ) {
                DropdownMenu(expanded, onDismissRequest = { expanded = false },
                    modifier = Modifier.width(with(density) { anchorWidth.toDp() })
                        .clip(menuShape).background(Brush.verticalGradient(appearance.background))
                        .border(.75.dp, appearance.border, menuShape)) {
                    options.forEach { option ->
                        val chosen = order == option
                        DropdownMenuItem(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (chosen) appControlFill(true) else Color.Transparent)
                                .semantics { selected = chosen },
                            text = { Text(label(option), fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal) },
                            colors = MenuDefaults.itemColors(textColor = appControlInk(), trailingIconColor = appControlInk()),
                            onClick = { onOrder(option); expanded = false },
                            trailingIcon = { if (chosen) Icon(AppIcons.Selected, null) else Spacer(Modifier.size(24.dp)) })
                    }
                }
            }
        }
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), color = appControlFill(),
            contentColor = appControlInk(), border = androidx.compose.foundation.BorderStroke(.75.dp, LocalAppAppearance.current.border)) {
            IconButton(onClick = { onReversed(!reversed) }) {
                Icon(if (descending) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    if (descending) lt(language, "Decrescente; inverter ordem", "Descending; reverse order", "Descendente; invertir orden")
                    else lt(language, "Crescente; inverter ordem", "Ascending; reverse order", "Ascendente; invertir orden"))
            }
        }
    }
}
