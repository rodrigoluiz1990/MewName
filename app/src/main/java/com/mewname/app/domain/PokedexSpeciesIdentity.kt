package com.mewname.app.domain

/** These generic rows coexist with explicit forms; keep them in the source catalog for saved data. */
internal fun isRedundantPokedexSpecies(name: String): Boolean = name in setOf(
    "Lycanroc", "Enamorus", "Maushold", "Dudunsparce", "Gimmighoul", "Poltchageist", "Sinistcha"
)

/** One representative per national dex number; the full catalog remains available in details. */
internal fun nationalPokedexEntries(entries: List<PokedexCatalogEntry>): List<PokedexCatalogEntry> =
    entries.groupBy { it.number }.values.map { forms ->
        forms.firstOrNull { !it.name.contains('(') && it.name.split(' ').size == 1 }
            ?: forms.firstOrNull { it.name in setOf("Zacian Hero", "Enamorus Incarnate",
                "Lycanroc (Diurno)", "Maushold Family Of Four", "Dudunsparce Two-Segment Form",
                "Gimmighoul Roaming", "Poltchageist Counterfeit Form", "Sinistcha Unremarkable Form") }
            ?: forms.first()
    }.sortedBy { it.number }

internal fun nationalPokedexName(entry: PokedexCatalogEntry, language: AppLanguage): String {
    val base = when (entry.number) {
        745 -> "Lycanroc"
        888 -> "Zacian"
        905 -> "Enamorus"
        925 -> "Maushold"
        982 -> "Dudunsparce"
        999 -> "Gimmighoul"
        1012 -> "Poltchageist"
        1013 -> "Sinistcha"
        else -> entry.name.substringBefore(" (")
    }
    return when (language) {
        AppLanguage.PT_BR -> entry.namePt?.substringBefore(" (") ?: base
        AppLanguage.ES -> entry.nameEs?.substringBefore(" (") ?: base
        AppLanguage.EN -> base
    }
}
