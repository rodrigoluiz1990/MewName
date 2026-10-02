package com.mewname.app.domain

import android.content.Context
import kotlin.math.roundToInt

internal data class MovesetRating(val percent: Int, val grade: String) {
    val label get() = "$percent% · $grade"
}

/** Neutral, continuous energy-cycle estimate, not an opponent/battle simulation. */
internal object MovesetRater {
    fun cycleScore(fast: MoveCatalogEntry, charged: MoveCatalogEntry, types: List<String>): Double? {
        val gain = fast.energyDelta?.takeIf { it > 0 } ?: return null
        val cost = charged.energyDelta?.takeIf { it < 0 }?.let { -it } ?: return null
        val fastTime = fast.duration?.takeIf { it > 0 } ?: return null
        val chargedTime = charged.duration?.takeIf { it > 0 } ?: return null
        val fp = fast.power?.takeIf { it >= 0 } ?: return null
        val cp = charged.power?.takeIf { it >= 0 } ?: return null
        if (types.isEmpty()) return null
        fun stab(type: String) = if (types.any { it.equals(type, true) }) 1.2 else 1.0
        val count = cost.toDouble() / gain
        return (count * fp * stab(fast.type) + cp * stab(charged.type)) / ((count * fastTime + chargedTime) / 1000.0)
    }

    fun rate(fast: MoveCatalogEntry?, charged: MoveCatalogEntry?, availableFast: List<MoveCatalogEntry>,
             availableCharged: List<MoveCatalogEntry>, types: List<String>): MovesetRating? {
        if (fast == null || charged == null || fast !in availableFast || charged !in availableCharged) return null
        val selected = cycleScore(fast, charged, types) ?: return null
        val scores = availableFast.flatMap { f -> availableCharged.map { c -> cycleScore(f, c, types) } }
        if (scores.isEmpty() || scores.any { it == null }) return null
        val best = scores.filterNotNull().maxOrNull()?.takeIf { it > 0 } ?: return null
        val percent = (100 * selected / best).roundToInt().coerceIn(0, 100)
        return MovesetRating(percent, when { percent >= 90 -> "A"; percent >= 80 -> "B"; percent >= 70 -> "C"; percent >= 60 -> "D"; else -> "E" })
    }

    fun load(context: Context, pokemon: String?, moves: PokemonMoveSet, fast: String?, charged: String?): MovesetRating? {
        if (pokemon == null || fast == null || charged == null) return null
        val meta = RaidCounterRepository(context).metadata().resolve(pokemon)
        val entry = if (meta == null) GameInfoRepository.loadPokedexCatalog(context).firstOrNull {
            it.name.equals(pokemon, true) || it.aliases.any { alias -> alias.equals(pokemon, true) }
        } else null
        val types = meta?.types ?: listOfNotNull(entry?.type1, entry?.type2)
        val catalog = GameInfoRepository.loadMoveCatalog(context)
        fun stats(name: String, category: MoveCategory) = catalog.firstOrNull { it.name.equals(name, true) && it.category == category }
        val fastOptions = moves.fastMoves.map { stats(it.name, MoveCategory.FAST) }
        val chargedOptions = moves.chargedMoves.map { stats(it.name, MoveCategory.CHARGED) }
        if (fastOptions.any { it == null } || chargedOptions.any { it == null }) return null
        return rate(stats(fast, MoveCategory.FAST), stats(charged, MoveCategory.CHARGED), fastOptions.filterNotNull(), chargedOptions.filterNotNull(), types)
    }
}