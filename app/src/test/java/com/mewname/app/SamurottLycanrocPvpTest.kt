package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.PokemonFamilySuggester
import com.mewname.app.domain.PvpRankCalculator
import com.mewname.app.domain.PvpCalculationOptions
import com.mewname.app.model.PvpLeague
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SamurottLycanrocPvpTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val calculator = PvpRankCalculator()

    @Test fun samurottFormsHaveSeparateStatsAndAliases() {
        assertEquals(3194, calculator.estimateCpAtLevel(context, "Samurott", 15, 15, 15, 50.0))
        for (name in listOf("Samurott (Hisui)", "Hisuian Samurott", "Samurott (Hisuian)", "Samurott de Hisui")) {
            assertEquals("Samurott (Hisui)", calculator.canonicalName(context, name))
            assertEquals(3167, calculator.estimateCpAtLevel(context, name, 15, 15, 15, 50.0))
            assertNotNull(calculator.calculateRank(context, name, 0, 15, 15, PvpLeague.GREAT))
        }
    }

    @Test fun lycanrocFormsDoNotCollapseIntoOneSpecies() {
        val expected = mapOf(
            "lycanroc midday" to ("Lycanroc (Diurno)" to 3027),
            "lycanroc midnight" to ("Lycanroc (Noturno)" to 3102),
            "lycanroc dusk" to ("Lycanroc (Crepúsculo)" to 3054),
            "lycanroc forma crepusculo" to ("Lycanroc (Crepúsculo)" to 3054)
        )
        expected.forEach { (alias, form) ->
            assertEquals(form.first, calculator.canonicalName(context, alias))
            assertEquals(form.second, calculator.estimateCpAtLevel(context, alias, 15, 15, 15, 50.0))
        }
    }

    @Test fun rockruffFamilyShowsAllThreeFormsWithIndependentRankings() {
        val family = PokemonFamilySuggester().familyMembersFor(context, "Rockruff", "Rockruff")
        assertEquals(listOf("Rockruff", "Lycanroc (Diurno)", "Lycanroc (Noturno)", "Lycanroc (Crepúsculo)"), family)
        val ranks = calculator.calculateFamilySpeciesLeagueRanks(context, family, 0, 15, 15,
            options = PvpCalculationOptions(maxLevel = 50))
        for (league in PvpLeague.entries) {
            val rows = ranks.filter { it.league == league }
            assertEquals(family.toSet(), rows.map { it.pokemonName }.toSet())
            assertTrue(rows.all { it.rank != null })
        }
        val lycanroc = ranks.filter { it.league == PvpLeague.GREAT && it.pokemonName.startsWith("Lycanroc") }
        assertEquals(3, lycanroc.map { it.bestStatProduct }.distinct().size)
    }

    @Test fun oshawottFamilyIncludesCalculableHisuianSamurott() {
        val family = PokemonFamilySuggester().familyMembersFor(context, "Oshawott", "Samurott (Hisui)")
        val ranks = calculator.calculateFamilySpeciesLeagueRanks(context, family, 0, 15, 15)
        assertEquals(4, ranks.count { it.pokemonName == "Samurott (Hisui)" && it.rank != null })
        assertEquals(4, ranks.count { it.pokemonName == "Samurott" && it.rank != null })
    }
}