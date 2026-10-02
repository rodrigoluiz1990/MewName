package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.PvpRankCalculator
import com.mewname.app.domain.PvpCalculationOptions
import com.mewname.app.model.PvpLeague
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArcaninePvpTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val calculator = PvpRankCalculator()

    @Test fun normalAndHisuiHaveIndependentRankings() {
        for (level in listOf(40, 50)) {
            val ranks = calculator.calculateFamilySpeciesLeagueRanks(context,
                listOf("Arcanine", "Arcanine (Hisui)"), 1,15,12,
                options = PvpCalculationOptions(maxLevel = level))
            fun rank(name: String, league: PvpLeague) = ranks.single { it.pokemonName == name && it.league == league }.rank
            assertEquals(8, rank("Arcanine", PvpLeague.GREAT))
            assertEquals(25, rank("Arcanine", PvpLeague.ULTRA))
            assertEquals(380, rank("Arcanine (Hisui)", PvpLeague.GREAT))
            assertEquals(55, rank("Arcanine (Hisui)", PvpLeague.ULTRA))
        }
    }

    @Test fun bothGrowlitheFormsHaveSeparateStatsAndHisuiAliases() {
        for (name in listOf("Growlithe", "Arcanine")) {
            assertEquals("$name (Hisui)", calculator.canonicalName(context, "Hisuian $name"))
            val regular = calculator.estimateCpAtLevel(context, name,15,15,15,40.0)
            val hisui = calculator.estimateCpAtLevel(context, "$name (Hisui)",15,15,15,40.0)
            assertNotNull(regular)
            assertNotNull(hisui)
            assertNotEquals(regular, hisui)
        }
    }
    @Test fun ultraLeagueTieKeepsEqualStatProductsAtTheSameRank() {
        val first = calculator.calculateLeagueRankInfo(context,"Arcanine",1,15,12,PvpLeague.ULTRA)!!
        val second = calculator.calculateLeagueRankInfo(context,"Arcanine",1,15,13,PvpLeague.ULTRA)!!
        assertEquals(25, first.rank)
        assertEquals(first.rank, second.rank)
        assertEquals(first.bestStatProduct, second.bestStatProduct)
        assertEquals(2491, first.bestCp)
        assertEquals(2496, second.bestCp)
    }
}