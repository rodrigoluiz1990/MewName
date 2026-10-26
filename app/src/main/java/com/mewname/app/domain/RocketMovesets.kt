package com.mewname.app.domain



/** Relative offensive throughput in PvP turns. Not battle duration: ignores charged animations,
 * enemy attacks, shields, buffs, IVs, levels, HP, energy carry-over and Rocket pauses. */
internal fun rocketOffenseScore(attack: Int, types: List<String>, fast: MoveCatalogEntry,
    charged: MoveCatalogEntry, effectiveness: (String) -> Double): Double? {
    val power = fast.pvpPower ?: return null
    val gain = fast.pvpEnergyDelta ?: return null
    val turns = fast.pvpTurnDuration ?: return null
    val chargedPower = charged.pvpPower ?: return null
    val cost = -(charged.pvpEnergyDelta ?: return null)
    if (attack <= 0 || power < 0 || gain <= 0 || turns <= 0 || chargedPower <= 0 || cost <= 0 ||
        fast.category != MoveCategory.FAST || charged.category != MoveCategory.CHARGED) return null
    fun modifier(move: MoveCatalogEntry) = effectiveness(move.type) *
        if (types.any { it.equals(move.type, true) }) 1.2 else 1.0
    return attack * (power * modifier(fast) + chargedPower * modifier(charged) * gain / cost) / turns / 100.0
}
/** Source marks double weaknesses with (2×); preserve source order for ties. */
internal fun rocketWeaknessTypes(detail: String): List<String> = detail.split(';')
    .sortedByDescending { if (it.contains("2×")) 2 else 1 }
    .flatMap { Regex("[A-Za-z]+").findAll(it).map { match -> match.value }.toList() }
    .distinct()

internal data class RocketTeamMoveset(
    val pokemon: PokedexCatalogEntry,
    val fast: PokemonMove,
    val charged: List<PokemonMove>,
    val fastType: String,
    val chargedTypes: List<String>,
    val scores: List<Double>
)

/** Enumerates legal combinations for one species: one fast and one/two distinct charged moves.
 * Each opponent uses the better charged move, never the sum of both attacks. */
internal fun rocketTeamOptions(pokemon: PokedexCatalogEntry, moves: PokemonMoveSet,
    stats: Map<String, MoveCatalogEntry>, defenders: List<List<String>>,
    chart: Map<String, Map<String, Double>>): List<RocketTeamMoveset> {
    val types = listOfNotNull(pokemon.type1, pokemon.type2)
    if (types.isEmpty() || defenders.isEmpty() || defenders.any { it.isEmpty() }) return emptyList()
    val charged = moves.chargedMoves.distinctBy { it.name }.mapNotNull { move ->
        stats[pokedexIdentity(move.name)]?.let { move to it }
    }
    return moves.fastMoves.distinctBy { it.name }.flatMap { fast ->
        val f = stats[pokedexIdentity(fast.name)] ?: return@flatMap emptyList()
        val scored = charged.mapNotNull { (move, c) ->
            val scores = defenders.map { target ->
                rocketOffenseScore(pokemon.attack ?: 0, types, f, c) { attack ->
                    target.fold(1.0) { total, type -> total * (chart[attack]?.get(type) ?: 1.0) }
                } ?: return@mapNotNull null
            }
            Triple(move, c.type, scores)
        }
        buildList {
            scored.forEachIndexed { i, first ->
                add(RocketTeamMoveset(pokemon, fast, listOf(first.first), f.type, listOf(first.second), first.third))
                for (j in i + 1 until scored.size) {
                    val second = scored[j]
                    // A second unlock is only recommended when each move contributes coverage.
                    if (first.third.indices.any { first.third[it] > second.third[it] } &&
                        first.third.indices.any { second.third[it] > first.third[it] }) {
                        add(RocketTeamMoveset(pokemon, fast, listOf(first.first, second.first), f.type,
                            listOf(first.second, second.second), first.third.indices.map { maxOf(first.third[it], second.third[it]) }))
                    }
                }
            }
        }
    }
}

/** Greedy team coverage, not a battle simulation or exhaustive global optimum.
 * Normalize each opponent by its best available score so a single easy target cannot dominate.
 * A team member contributes only improvements over coverage already supplied by the team. */
internal fun selectRocketTeam(options: List<RocketTeamMoveset>): List<RocketTeamMoveset> {
    val count = options.firstOrNull()?.scores?.size ?: return emptyList()
    if (count == 0) return emptyList()
    val valid = options.filter { it.scores.size == count && it.scores.all { score -> score.isFinite() && score > 0 } }
    if (valid.isEmpty()) return emptyList()
    val best = List(count) { target -> valid.maxOf { it.scores[target] } }
    val coverage = DoubleArray(count)
    val team = mutableListOf<RocketTeamMoveset>()
    repeat(3) {
        val chosen = valid.filter { option -> team.none { it.pokemon.number == option.pokemon.number } }
            .maxWithOrNull(compareBy<RocketTeamMoveset> { option ->
                option.scores.indices.sumOf { target -> maxOf(coverage[target], option.scores[target] / best[target]) - coverage[target] }
            }.thenBy { option -> option.scores.indices.sumOf { option.scores[it] / best[it] } }
                .thenBy { -it.charged.size }.thenBy { -it.pokemon.number }) ?: return team
        team += chosen
        chosen.scores.indices.forEach { coverage[it] = maxOf(coverage[it], chosen.scores[it] / best[it]) }
    }
    return team
}
