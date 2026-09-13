package com.mewname.app

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import com.mewname.app.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PvpCalculationSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun reset() { PvpCalculationSettings.preferences(context).edit().clear().commit() }
    @After fun cleanup() { reset() }

    @Test fun defaultIsLevel50WithoutBuddy() {
        assertEquals(PvpCalculationOptions(50, false), PvpCalculationSettings.read(context))
    }

    @Test fun allFourLimitsReachCalculatorAndLinksWithoutReusingWrongCache() {
        val calculator = PvpRankCalculator()
        for (options in listOf(PvpCalculationOptions(40), PvpCalculationOptions(40, true),
            PvpCalculationOptions(50), PvpCalculationOptions(50, true), PvpCalculationOptions(40))) {
            PvpCalculationSettings.save(context, options)
            assertEquals(options, PvpCalculationSettings.read(context))
            for (league in listOf(PvpLeague.GREAT, PvpLeague.ULTRA, PvpLeague.MASTER)) {
                val result = calculator.calculateLeagueRankInfo(context, "Bulbasaur", 15, 15, 15, league)!!
                assertEquals(options.effectiveMaxLevel, result.bestLevel!!, 0.0)
                assertEquals(calculator.estimateCpAtLevel(context, "Bulbasaur", 15, 15, 15,
                    options.effectiveMaxLevel), result.bestCp)
                val url = Uri.parse(result.stadiumUrl)
                assertEquals(options.effectiveMaxLevel.toInt().toString(), url.getQueryParameter("levelCap"))
                assertEquals(options.bestBuddy.toString(), url.getQueryParameter("include_best_buddy"))
            }
        }
    }

    @Test fun limitedLeagueRespectsCpAndSettingsChangeItsRank() {
        val calculator = PvpRankCalculator()
        PvpCalculationSettings.save(context, PvpCalculationOptions(40))
        val old = calculator.calculateLeagueRankInfo(context, "Azumarill", 0, 15, 15, PvpLeague.GREAT)!!
        PvpCalculationSettings.save(context, PvpCalculationOptions(50))
        val xl = calculator.calculateLeagueRankInfo(context, "Azumarill", 0, 15, 15, PvpLeague.GREAT)!!
        assertTrue(old.bestLevel!! <= 40)
        assertTrue(xl.bestLevel!! <= 50)
        assertTrue(xl.bestCp!! <= 1500)
        assertTrue(xl.bestStatProduct!! > old.bestStatProduct!!)
        assertNotEquals(old.rank, xl.rank)
    }

    @Test fun reviewOverridesAreTemporaryAndRecalculateEveryColumn() {
        val saved = PvpCalculationOptions(50, false)
        PvpCalculationSettings.save(context, saved)
        val calculator = PvpRankCalculator()
        val data = PokemonScreenData(pokemonName = "Bulbasaur", level = 1.0,
            attIv = 0, defIv = 15, staIv = 15, pvpLeague = PvpLeague.ULTRA,
            pvpPokemonName = "Ivysaur")
        for (options in listOf(PvpCalculationOptions(40), PvpCalculationOptions(40, true),
            PvpCalculationOptions(50), PvpCalculationOptions(50, true))) {
            val result = buildDerivedReviewData(context, data,
                listOf("Bulbasaur", "Ivysaur", "Venusaur"), calculator, MasterIvBadgeCatalog(),
                pvpOptions = options)
            assertEquals(12, result.familyPvpRanks.size)
            assertEquals("Ivysaur", result.pvpPokemonName)
            result.familyPvpRanks.forEach { rank ->
                assertTrue(rank.bestLevel!! <= options.effectiveMaxLevel)
                assertEquals(options.effectiveMaxLevel.toInt().toString(),
                    Uri.parse(rank.stadiumUrl).getQueryParameter("levelCap"))
            }
            val bulbasaur = result.familyPvpRanks.first {
                it.pokemonName == "Bulbasaur" && it.league == PvpLeague.ULTRA
            }
            assertEquals(options.effectiveMaxLevel, bulbasaur.bestLevel!!, 0.0)
            assertEquals(saved, PvpCalculationSettings.read(context))
        }
    }

    @Test fun quickModesCanReturnToNormalAndNeverCombineShadowAndPurified() {
        assertEquals(ReviewIvMode.SHADOW, toggledReviewMode(ReviewIvMode.NORMAL, ReviewIvMode.SHADOW))
        assertEquals(ReviewIvMode.PURIFIED, toggledReviewMode(ReviewIvMode.SHADOW, ReviewIvMode.PURIFIED))
        assertEquals(ReviewIvMode.NORMAL, toggledReviewMode(ReviewIvMode.PURIFIED, ReviewIvMode.PURIFIED))
        assertEquals(ReviewIvMode.NORMAL, toggledReviewMode(ReviewIvMode.SHADOW, ReviewIvMode.SHADOW))
    }

    @Test fun allQuickControlAssetsDecode() {
        for (asset in listOf("shadow", "purified", "level_40", "level_50", "best_buddy")) {
            context.assets.open("pvp/controls/$asset.png").use {
                assertNotNull(asset, android.graphics.BitmapFactory.decodeStream(it))
            }
        }
    }
    private fun option(name: String, rank: Int, product: Double) = PvpSpeciesRankInfo(
        pokemonName = name, league = PvpLeague.GREAT, eligible = true, rank = rank,
        bestStatProduct = product)

    @Test fun smallerRankWinsEvenWithSmallerAbsoluteProductAndTiesPreferCurrentSpecies() {
        val options = listOf(option("Bulbasaur", 1, 100.0), option("Venusaur", 20, 500.0))
        assertEquals("Bulbasaur", bestLeagueRanksFromSpecies(options).single().pokemonName)
        val tied = listOf(option("Bulbasaur", 1, 100.0), option("Venusaur", 1, 500.0))
        assertEquals("Venusaur", bestLeagueRanksFromSpecies(tied, "Venusaur").single().pokemonName)
    }

    @Test fun evolvedPokemonKeepsEveryFamilyColumnAndManualSelection() {
        val calculator = PvpRankCalculator()
        val family = listOf("Bulbasaur", "Ivysaur", "Venusaur")
        val data = PokemonScreenData(pokemonName = "Venusaur", level = 1.0,
            attIv = 0, defIv = 15, staIv = 15, pvpLeague = PvpLeague.GREAT,
            pvpPokemonName = "Ivysaur", pvpRank = 4096)
        val result = buildDerivedReviewData(context, data, family, calculator, MasterIvBadgeCatalog())
        assertEquals(family.toSet(), result.familyPvpRanks.map { it.pokemonName }.toSet())
        assertEquals(12, result.familyPvpRanks.size)
        assertEquals("Ivysaur", result.pvpPokemonName)
        assertEquals(calculator.calculateRank(context, "Ivysaur", 0, 15, 15, PvpLeague.GREAT), result.pvpRank)
    }
}
