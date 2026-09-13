package com.mewname.app.domain

/** Rank is relative to each species. Do not compare raw stat products across evolutions. */
internal fun <T> pvpRankComparator(
    currentPokemonName: String? = null,
    rank: (T) -> Int?,
    name: (T) -> String?
): Comparator<T> = compareBy<T> { rank(it) ?: Int.MAX_VALUE }
    .thenBy { item ->
        val current = currentPokemonName?.let(::pokemonDisplayName)
        if (current != null && name(item)?.let(::pokemonDisplayName).equals(current, ignoreCase = true)) 0 else 1
    }
    .thenBy { name(it).orEmpty() }
