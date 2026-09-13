package com.mewname.app

import android.content.Context
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
class ReviewIvModeRankingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val calculator = PvpRankCalculator()
    private val original = PokemonScreenData(pokemonName = "Starmie", level = 10.0,
        attIv = 1, defIv = 14, staIv = 12, isShadow = true, pvpLeague = PvpLeague.GREAT,
        pvpPokemonName = "Starmie")
    @Before fun setup() { PvpCalculationSettings.save(context, PvpCalculationOptions(40)) }
    @After fun cleanup() { PvpCalculationSettings.preferences(context).edit().clear().commit() }

    private fun rank(data: PokemonScreenData) = buildDerivedReviewData(context, data,
        listOf("Starmie"), calculator, MasterIvBadgeCatalog())

    @Test fun purifiedRankUsesTheSameIncreasedIvsShownInTheFields() {
        val shadow = original.withReviewIvValues(ReviewIvMode.SHADOW, 1, 14, 12, false)
        val purified = original.withReviewIvValues(ReviewIvMode.PURIFIED, 1, 14, 12, false)
        assertEquals(listOf(3, 15, 14), listOf(purified.attIv, purified.defIv, purified.staIv))
        assertTrue(purified.isPurified)
        assertFalse(purified.isShadow)
        val result = rank(purified)
        assertEquals(calculator.calculateRank(context, "Starmie", 3, 15, 14, PvpLeague.GREAT), result.pvpRank)
        assertNotEquals(rank(shadow).pvpRank, result.pvpRank)
        assertTrue(result.familyPvpRanks.all { it.stadiumUrl?.contains("att_iv=3") == true })
    }

    @Test fun changingBackRestoresOriginalIvsAndRankWithoutAccumulatingBonus() {
        var current = original
        repeat(3) {
            current = current.withReviewIvValues(ReviewIvMode.PURIFIED, 1, 14, 12, false)
            assertEquals(3, current.attIv)
            current = current.withReviewIvValues(ReviewIvMode.SHADOW, 1, 14, 12, false)
            assertEquals(1, current.attIv)
            assertEquals(606, rank(current).pvpRank)
        }
        val normal = current.withReviewIvValues(ReviewIvMode.NORMAL, 1, 14, 12, false)
        assertEquals(rank(current).pvpRank, rank(normal).pvpRank)
    }

    @Test fun readingAnAlreadyPurifiedPokemonDoesNotAddTwoIvsAgain() {
        val scanned = original.copy(attIv = 3, defIv = 15, staIv = 14, isPurified = true, isShadow = false)
        val result = scanned.withReviewIvValues(ReviewIvMode.PURIFIED, 3, 15, 14, scanned.isPurified)
        assertEquals(listOf(3, 15, 14), listOf(result.attIv, result.defIv, result.staIv))
        assertEquals(3, displayIvToBaseIv(3, ReviewIvMode.PURIFIED, true))
        assertEquals(calculator.calculateRank(context, "Starmie", 3, 15, 14, PvpLeague.GREAT), rank(result).pvpRank)
        assertNull(effectiveIvForMode(null, ReviewIvMode.PURIFIED))
    }
}
