package com.mewname.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
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
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        SelectionDropdownField(
            label = lt(language, "Ordenar por", "Sort by", "Ordenar por"),
            value = label(order), options = MoveSort.entries.filter { allowNationalDex || it != MoveSort.NATIONAL_DEX }.map(::label).sortedWith(java.text.Collator.getInstance(when (language) {
                AppLanguage.PT_BR -> java.util.Locale.forLanguageTag("pt-BR")
                AppLanguage.EN -> java.util.Locale.ENGLISH
                AppLanguage.ES -> java.util.Locale.forLanguageTag("es")
            })),
            onSelected = { selected -> MoveSort.entries.firstOrNull { label(it) == selected }?.let(onOrder) },
            useOptionModal = false, modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { onReversed(!reversed) }) {
            Text(if (descending) lt(language, "↓ Decrescente", "↓ Descending", "↓ Descendente")
                else lt(language, "↑ Crescente", "↑ Ascending", "↑ Ascendente"))
        }
    }
}
