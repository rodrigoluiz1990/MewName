package com.mewname.app

import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class PokedexMovesetsTest {
    private fun fast(name: String = "FAST", type: String = "Grass") = MoveCatalogEntry(name, type = type, power = 10, energyDelta = 10, duration = 500, category = MoveCategory.FAST)
    private fun charged(name: String = "CHARGED", type: String = "Grass") = MoveCatalogEntry(name, type = type, power = 100, energyDelta = -50, duration = 1000, category = MoveCategory.CHARGED)
    @Test fun cycleIncludesEnergyDurationAndStab() {
        val stats = listOf(fast(), charged()).associateBy { pokedexIdentity(it.name) }
        val pool = PokemonMoveSet(listOf(PokemonMove("FAST", "Fast", false)), listOf(PokemonMove("CHARGED", "Charged", true)))
        val result = bestPokedexPveMovesets(pool, stats, listOf("Grass")).single()
        assertEquals(180.0 / 3.5, result.powerPerSecond, 0.00001)
        assertTrue(result.charged.legacy)
    }
    @Test fun missingAndInvalidCombatDataIsExcluded() {
        val pool = PokemonMoveSet(listOf(PokemonMove("FAST", "Fast", false)), listOf(PokemonMove("CHARGED", "Charged", false)))
        for (bad in listOf(charged().copy(duration = null), charged().copy(energyDelta = 0), charged().copy(power = 0))) {
            val stats = listOf(fast(), bad).associateBy { pokedexIdentity(it.name) }
            assertTrue(bestPokedexPveMovesets(pool, stats, emptyList()).isEmpty())
        }
        assertTrue(bestPokedexPveMovesets(pool, emptyMap(), emptyList()).isEmpty())
    }
    @Test fun sortingUsesStabAndOnlyLearntMoves() {
        val f = fast()
        val a = charged("A", "Water")
        val b = charged("B", "Grass")
        val extra = charged("NOT_LEARNT").copy(power = 900)
        val pool = PokemonMoveSet(listOf(PokemonMove("FAST", "Fast", false)), listOf(PokemonMove("A", "A", false), PokemonMove("B", "B", false)))
        val results = bestPokedexPveMovesets(pool, listOf(f,a,b,extra).associateBy { pokedexIdentity(it.name) }, listOf("Grass"))
        assertEquals(listOf("B", "A"), results.map { it.charged.name })
    }
}
