package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import com.mewname.app.model.PvpLeague
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StarmiePvpRegressionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val calculator = PvpRankCalculator()
    @Before fun setup() { PvpCalculationSettings.save(context, PvpCalculationOptions(40, false)) }
    @After fun cleanup() { PvpCalculationSettings.preferences(context).edit().clear().commit() }

    @Test fun starmieMatchesBothSubmittedReferenceScreenshots() {
        val rank = calculator.calculateLeagueRankInfo(context, "Starmie", 1, 14, 12, PvpLeague.GREAT)!!
        assertEquals(606, rank.rank)
        assertEquals(1472, rank.bestCp)
        assertEquals(21.5, rank.bestLevel!!, 0.0)
        assertEquals(1103, calculator.calculateRank(context, "Starmie", 1, 14, 12, PvpLeague.ULTRA))
    }

    @Test fun competingSpreadsCannotUseLevelsThatActuallyExceed1500Cp() {
        for ((ivs, level) in listOf(listOf(2, 0, 14) to 22.5,
            listOf(7, 13, 10) to 21.5, listOf(9, 13, 7) to 21.5)) {
            assertEquals(1501, calculator.estimateCpAtLevel(context, "Starmie", ivs[0], ivs[1], ivs[2], level))
            val rank = calculator.calculateLeagueRankInfo(context, "Starmie", ivs[0], ivs[1], ivs[2], PvpLeague.GREAT)!!
            assertEquals(level - 0.5, rank.bestLevel!!, 0.0)
            assertTrue(rank.bestCp!! <= 1500)
        }
    }

    @Test fun staryuEqualBattleStatsKeepSharedRankRatherThanArtificialTieBreak() {
        val a = calculator.calculateLeagueRankInfo(context, "Staryu", 1, 14, 12, PvpLeague.GREAT)!!
        val b = calculator.calculateLeagueRankInfo(context, "Staryu", 1, 14, 13, PvpLeague.GREAT)!!
        assertEquals(878, a.rank)
        assertEquals(a.rank, b.rank)
        assertEquals(a.bestStatProduct, b.bestStatProduct)
        assertEquals(1033, a.bestCp)
        assertEquals(1037, b.bestCp)
        assertEquals(879, calculator.calculateRank(context, "Staryu", 1, 14, 12, PvpLeague.LITTLE))
    }
}
