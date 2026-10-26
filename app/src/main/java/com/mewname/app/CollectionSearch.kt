package com.mewname.app

import java.text.Normalizer
import java.util.Locale

internal fun collectionSearchIndex(query: String, candidates: List<Pair<Int, String>>): Int {
    fun clean(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()
    val search = clean(query)
    if (search.isBlank()) return -1
    val labels = candidates.map { clean(it.second) }
    fun first(match: (Int) -> Boolean) = candidates.indices.firstOrNull(match)
    return first { labels[it] == search || candidates[it].first.toString() == search }
        ?: first { labels[it].startsWith(search) }
        ?: first { labels[it].contains(search) || candidates[it].first.toString().startsWith(search) }
        ?: -1
}
