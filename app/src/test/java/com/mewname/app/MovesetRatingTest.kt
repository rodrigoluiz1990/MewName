package com.mewname.app

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MovesetRatingTest {
    private val fast = MoveCatalogEntry("Fast", type="Fire", power=10, energyDelta=10, duration=1000, category=MoveCategory.FAST)
    private val charged = MoveCatalogEntry("Charge", type="Fire", power=100, energyDelta=-50, duration=2000, category=MoveCategory.CHARGED)
    @Test fun cycleUsesEnergyDurationAndStab() {
        assertEquals(180.0/7.0, MovesetRater.cycleScore(fast, charged, listOf("Fire"))!!, .00001)
        assertEquals(150.0/7.0, MovesetRater.cycleScore(fast, charged, listOf("Water"))!!, .00001)
    }
    @Test fun bestIs100AndWeakerPairIsLower() {
        val weak = charged.copy(name="Weak",power=20)
        val best = MovesetRater.rate(fast, charged, listOf(fast), listOf(charged,weak), listOf("Fire"))!!
        assertEquals(100,best.percent)
        assertEquals("A",best.grade)
        assertTrue(MovesetRater.rate(fast, weak, listOf(fast), listOf(charged,weak), listOf("Fire"))!!.percent < 100)
        assertNull(MovesetRater.rate(fast, charged, listOf(fast), listOf(weak), listOf("Fire")))
        assertNull(MovesetRater.cycleScore(fast.copy(energyDelta=0),charged,listOf("Fire")))
        assertNull(MovesetRater.cycleScore(fast,charged,emptyList()))
    }
    @Test fun actualPidgeotMovesCanBeRated() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val moves=PokemonMoveRepository.load(context,"Pidgeot",AppLanguage.EN)
        assertNotNull(MovesetRater.load(context,"Pidgeot",moves,"Gust","Brave Bird"))
    }
    @Test fun masterExplanationNamesLastEvolutionAndPreservesRegionalBranch() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val master=MasterIvBadgeCatalog()
        assertEquals("Venusaur", master.combinationFamilyName(listOf("Bulbasaur","Ivysaur","Venusaur"),"Bulbasaur"))
        assertEquals("Sirfetch'd", master.combinationFamilyName(listOf("Farfetch'd","Farfetch'd (Galar)","Sirfetch'd"),"Farfetch'd (Galar)"))
    }
}