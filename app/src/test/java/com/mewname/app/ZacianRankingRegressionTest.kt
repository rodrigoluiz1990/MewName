package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import com.mewname.app.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ZacianRankingRegressionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `family aliases produce only two Zacian ranking columns`() {
        val calculator = PvpRankCalculator()
        val family = PokemonFamilySuggester().familyMembersFor(context, "Zacian", "Zacian (Hero)")
        val ranks = calculator.calculateFamilySpeciesLeagueRanks(context, family, 15, 15, 14,
            currentPokemonName = "Zacian (Hero)", currentLevel = 51.0)
        assertEquals(setOf("Zacian Hero", "Zacian Crowned Sword"), ranks.map { it.pokemonName }.toSet())
        assertEquals(8, ranks.size)
        // Aliases of the current species must not be treated as an evolution at level 51.
        assertTrue(ranks.filter { it.pokemonName == "Zacian Hero" }.all { it.rank != null })
        assertTrue(ranks.filter { it.pokemonName == "Zacian Crowned Sword" && it.league != PvpLeague.MASTER }.all { !it.eligible && it.rank != null })
        val data = PokemonScreenData(pokemonName = "Zacian (Hero)", pvpPokemonName = "Zacian", familyPvpRanks = ranks)
        assertEquals(2, reviewRankSpecies(context, data, calculator).size)
    }

    @Test
    fun `old mixed aliases and an OCR name without rankings do not add columns`() {
        val data = PokemonScreenData(
            pokemonName = "Zacian (Hero)",
            pvpPokemonName = "Zacian",
            familyPvpRanks = listOf("Zacian", "Zacian Hero", "Zacian (Hero)", "Zacian (Coroado)").map {
                PvpSpeciesRankInfo(pokemonName = it, league = PvpLeague.MASTER)
            }
        )
        assertEquals(listOf("Zacian Hero", "Zacian Crowned Sword"), reviewRankSpecies(context, data, PvpRankCalculator()))
        assertTrue(reviewRankSpecies(context, PokemonScreenData(pokemonName = "Zacian"), PvpRankCalculator()).isEmpty())
    }

    @Test
    fun `crowned selected form keeps its signature move even when PvP chooses Hero`() {
        val data = PokemonScreenData(pokemonName = "Zacian (Coroado)", pvpPokemonName = "Zacian Hero")
        val moves = PokemonMoveRepository.load(context, reviewMoveSpecies(data)!!, AppLanguage.PT_BR)
        assertTrue(moves.chargedMoves.any { it.name == "Behemoth Blade" && it.legacy })
        assertFalse(PokemonMoveRepository.load(context, reviewMoveSpecies(data.copy(pokemonName = "Zacian (Hero)"))!!, AppLanguage.EN)
            .chargedMoves.any { it.name == "Behemoth Blade" })
    }
    @Test
    fun `over cap current Pokemon retains the hypothetical rank`() {
        val calculator = PvpRankCalculator()
        val ranks = calculator.calculateFamilySpeciesLeagueRanks(context, listOf("Zacian (Hero)"), 15, 15, 14,
            currentPokemonName = "Zacian Hero", currentLevel = 51.0, currentCp = 3682)
        for (rank in ranks.filter { it.league != PvpLeague.MASTER }) {
            assertFalse(rank.eligible)
            assertEquals(calculator.calculateRank(context, "Zacian Hero", 15, 15, 14, rank.league), rank.rank)
        }
    }

    @Test
    fun `switching the form next to attacks loads the crowned legacy attack`() {
        val data = PokemonScreenData(pokemonName = "Zacian (Hero)", selectedChargedMove = "Iron Head")
            .withReviewSpecies("Zacian (Coroado)")
        assertNull(data.selectedChargedMove)
        val moves = PokemonMoveRepository.load(context, reviewMoveSpecies(data)!!, AppLanguage.PT_BR)
        assertTrue(moveDropdownOptions(moves.chargedMoves, "[L]").any { it.value == "Behemoth Blade" && it.label.endsWith("[L]") })
    }
    @Test
    fun `low recognized CP does not bypass the current level for crowned Zacian`() {
        val ranks = PvpRankCalculator().calculateFamilySpeciesLeagueRanks(
            context, listOf("Zacian (Hero)", "Zacian (Coroado)"), 15, 15, 14,
            currentPokemonName = "Zacian (Coroado)", currentLevel = 51.0, currentCp = 682
        )
        for (rank in ranks.filter { it.league != PvpLeague.MASTER }) {
            assertFalse(rank.eligible)
            assertNotNull(rank.rank)
        }
        assertTrue(ranks.filter { it.league == PvpLeague.MASTER }.all { it.eligible })
    }
}