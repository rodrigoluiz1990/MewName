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
class SnubbullPvpTieRegressionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val calculator = PvpRankCalculator()
    @Before fun setup() { PvpCalculationSettings.save(context, PvpCalculationOptions(40, false)) }
    @After fun cleanup() { PvpCalculationSettings.preferences(context).edit().clear().commit() }

    @Test fun equivalentProductsShareRank405InAllUncappedSnubbullLeagues() {
        for (league in listOf(PvpLeague.GREAT, PvpLeague.ULTRA, PvpLeague.MASTER)) {
            val mine = calculator.calculateLeagueRankInfo(context, "Snubbull", 4, 15, 13, league)!!
            val tied = calculator.calculateLeagueRankInfo(context, "Snubbull", 13, 9, 13, league)!!
            // At the same level both products are 14,100 * HP * CPM squared:
            // (137+4)*(85+15) == (137+13)*(85+9). HP is equal too.
            assertEquals(tied.bestStatProduct, mine.bestStatProduct)
            assertEquals(405, mine.rank)
            assertEquals(mine.rank, tied.rank)
            assertEquals(1141, mine.bestCp)
            assertEquals(40.0, mine.bestLevel!!, 0.0)
        }
    }

    @Test fun granbullReferenceRanksArePreserved() {
        assertEquals(375, calculator.calculateRank(context, "Granbull", 4, 15, 13, PvpLeague.ULTRA))
        assertEquals(677, calculator.calculateRank(context, "Granbull", 2, 15, 11, PvpLeague.ULTRA))
    }
}
