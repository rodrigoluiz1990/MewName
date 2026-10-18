package com.mewname.app.domain

import kotlin.math.ceil

internal data class PokedexPveMoveset(val fast: PokemonMove, val charged: PokemonMove, val powerPerSecond: Double)

/** Offensive cycle power, not simulated damage/DPS. No incoming energy, target, weather or party bonuses. */
internal fun bestPokedexPveMovesets(moves: PokemonMoveSet, stats: Map<String, MoveCatalogEntry>, types: List<String>): List<PokedexPveMoveset> {
    fun stab(move: MoveCatalogEntry) = if (types.any { it.equals(move.type, true) }) 1.2 else 1.0
    return moves.fastMoves.distinctBy { it.name }.flatMap { fast ->
        val f = stats[pokedexIdentity(fast.name)] ?: return@flatMap emptyList()
        if (f.category != MoveCategory.FAST || (f.power ?: 0) <= 0 || (f.energyDelta ?: 0) <= 0 || (f.duration ?: 0) <= 0) return@flatMap emptyList()
        moves.chargedMoves.distinctBy { it.name }.mapNotNull { charged ->
            val c = stats[pokedexIdentity(charged.name)] ?: return@mapNotNull null
            if (c.category != MoveCategory.CHARGED || (c.power ?: 0) <= 0 || (c.energyDelta ?: 0) >= 0 || (c.duration ?: 0) <= 0) return@mapNotNull null
            val count = ceil(-c.energyDelta!!.toDouble() / f.energyDelta!!)
            val seconds = (count * f.duration!! + c.duration!!) / 1000.0
            PokedexPveMoveset(fast, charged, (count * f.power!! * stab(f) + c.power!! * stab(c)) / seconds)
        }
    }.sortedWith(compareByDescending<PokedexPveMoveset> { it.powerPerSecond }.thenBy { it.fast.name }.thenBy { it.charged.name }).take(3)
}
