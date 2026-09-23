package com.mewname.app.domain

/** Heuristic with equal level/IVs, not a simulation of boss moves or unlocked Max move levels. */
internal fun maxSupportSuggestions(
    roster: List<BattleSuggestionEntry>,
    bossTypes: List<String>,
    chart: Map<String, Map<String, Double>>,
    metadata: RaidMetadata
): Pair<List<BattleSuggestionEntry>, List<BattleSuggestionEntry>> {
    val resolvedStats = roster.associate { entry ->
        val name = entry.name.replace(Regex("^(Gigantamax|Gigamax|Dynamax|Dinamax)\\s+", RegexOption.IGNORE_CASE), "")
        val id = name.uppercase(java.util.Locale.US).replace(" ", "_")
        entry.name to (metadata.pokemon[id] ?: metadata.pokemon[id + "_FORM"] ?: metadata.resolve(name))
    }
    fun stats(entry: BattleSuggestionEntry) = resolvedStats[entry.name]
    // Dual-type resistances multiply; averaging individual types gives incorrect results.
    fun incoming(entry: BattleSuggestionEntry): Double =
        bossTypes.maxOfOrNull { attack ->
            entry.attackTypes.fold(1.0) { damage, type -> damage * (chart[attack]?.get(type) ?: 1.0) }
        } ?: 1.0
    val known = roster.filter { stats(it) != null }
    val guards = known.sortedWith(compareByDescending<BattleSuggestionEntry> {
        val p = stats(it)!!
        p.defense.toDouble() * p.stamina / incoming(it)
    }.thenBy { it.name }).take(10)
    val healers = known.sortedWith(compareByDescending<BattleSuggestionEntry> { stats(it)!!.stamina }
        .thenByDescending { stats(it)!!.defense / incoming(it) }.thenBy { it.name }).take(10)
    return guards to healers
}