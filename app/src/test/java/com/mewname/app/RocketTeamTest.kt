package com.mewname.app

import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class RocketTeamTest {
    private fun option(dex: Int, vararg scores: Double) = RocketTeamMoveset(
        PokedexCatalogEntry(dex, "P$dex"), PokemonMove("Fast", "Fast", false),
        listOf(PokemonMove("Charged", "Charged", false)), "Water", listOf("Water"), scores.toList())
    @Test fun choosesOneTeamWithComplementaryCoverageAndNoRepeatedSpecies() {
        val team = selectRocketTeam(listOf(option(1, 100.0, 10.0, 10.0), option(1, 99.0, 10.0, 10.0),
            option(2, 90.0, 9.0, 9.0), option(3, 10.0, 100.0, 10.0), option(4, 10.0, 10.0, 100.0)))
        assertEquals(setOf(1,3,4), team.map { it.pokemon.number }.toSet())
        assertEquals(3, team.size)
    }
    @Test fun incompleteCandidatesDoNotInventTeamMembers() {
        assertTrue(selectRocketTeam(emptyList()).isEmpty())
        assertEquals(1, selectRocketTeam(listOf(option(1, 5.0), option(1, 4.0))).size)
    }
    @Test fun secondChargedMoveAddsCoverageWithoutAddingBothDamages() {
        val fast = MoveCatalogEntry("Fast", type="Normal", pvpPower=2, pvpEnergyDelta=8, pvpTurnDuration=2, category=MoveCategory.FAST)
        val fire = MoveCatalogEntry("Fire", type="Fire", pvpPower=60, pvpEnergyDelta=-40, category=MoveCategory.CHARGED)
        val water = fire.copy(name="Water", type="Water")
        val stats = listOf(fast,fire,water).associateBy { pokedexIdentity(it.name) }
        val moves = PokemonMoveSet(listOf(PokemonMove("Fast", "Fast", false)),
            listOf(PokemonMove("Fire", "Fire", false), PokemonMove("Water", "Water", true)))
        val pokemon = PokedexCatalogEntry(1,"P1",type1="Normal",attack=200)
        val chart = mapOf("Fire" to mapOf("Grass" to 1.6, "Rock" to 0.625),
            "Water" to mapOf("Grass" to 0.625, "Rock" to 1.6))
        val options = rocketTeamOptions(pokemon,moves,stats,listOf(listOf("Grass"),listOf("Rock")),chart)
        val pair = options.single { it.charged.size == 2 }
        pair.scores.indices.forEach { target ->
            assertEquals(options.filter { it.charged.size == 1 }.maxOf { it.scores[target] }, pair.scores[target], 0.00001)
        }
        assertEquals(2, selectRocketTeam(options).single().charged.size)
        val singleTarget = rocketTeamOptions(pokemon,moves,stats,listOf(listOf("Grass")),chart)
        assertTrue(singleTarget.all { it.charged.size == 1 })
    }
}
