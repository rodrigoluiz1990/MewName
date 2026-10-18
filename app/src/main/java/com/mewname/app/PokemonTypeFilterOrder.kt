package com.mewname.app

import java.util.Locale

// Shared display order for type selectors, independent of the app language.
internal val pokemonTypeFilterOrder = listOf(
    "Grass", "Fire", "Water", "Electric", "Psychic", "Normal", "Fighting",
    "Rock", "Ground", "Poison", "Bug", "Flying", "Steel",
    "Dark", "Ghost", "Ice", "Dragon", "Fairy"
)

private val pokemonTypeFilterRanks = pokemonTypeFilterOrder
    .mapIndexed { index, type -> type.lowercase(Locale.ROOT) to index }.toMap()

internal fun orderedPokemonFilterTypes(types: List<String>): List<String> =
    types.sortedBy { pokemonTypeFilterRanks[it.lowercase(Locale.ROOT)] ?: Int.MAX_VALUE }
