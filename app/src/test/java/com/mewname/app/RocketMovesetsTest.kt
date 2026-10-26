package com.mewname.app

import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class RocketMovesetsTest {
    @Test fun weaknessIconsPutDoubleWeaknessesFirstWithoutDuplicates() {
        assertEquals(listOf("Grass", "Water", "Ice"), rocketWeaknessTypes("Ice; Grass (2×); Water (2×); Grass"))
        assertTrue(rocketWeaknessTypes("").isEmpty())
    }
    private val fast = MoveCatalogEntry("Fast", type="Water", pvpPower=5, pvpEnergyDelta=8, pvpTurnDuration=2, category=MoveCategory.FAST)
    private val charged = MoveCatalogEntry("Charged", type="Water", pvpPower=60, pvpEnergyDelta=-40, category=MoveCategory.CHARGED)
    private fun score(f: MoveCatalogEntry=fast, c: MoveCatalogEntry=charged, types: List<String> = emptyList(), multiplier: Double=1.0) =
        rocketOffenseScore(200, types, f, c) { multiplier }
    @Test fun energyAndTurnsUseTrainerBattleFields() {
        assertEquals(17.0, score()!!, 0.00001)
        assertEquals(score()!!, score(fast.copy(power=999, duration=999, energyDelta=99))!!, 0.00001)
    }
    @Test fun stabAndDualWeaknessScaleOffense() {
        assertEquals(17.0 * 1.2 * 2.56, score(types=listOf("Water"), multiplier=2.56)!!, 0.00001)
        assertTrue(score(multiplier=0.390625)!! < score()!!)
    }
    @Test fun missingOrInvalidStatsNeverProduceRankedMoves() {
        assertNull(score(fast.copy(pvpPower=null)))
        assertNull(score(fast.copy(pvpTurnDuration=0)))
        assertNull(score(c=charged.copy(pvpEnergyDelta=0)))
        assertNull(score(c=charged.copy(category=MoveCategory.FAST)))
    }
    @Test fun FasterEnergyAndCheaperChargedMovesImproveCycle() {
        assertTrue(score(fast.copy(pvpEnergyDelta=10))!! > score()!!)
        assertTrue(score(c=charged.copy(pvpEnergyDelta=-30))!! > score()!!)
        assertTrue(score(fast.copy(pvpTurnDuration=3))!! < score()!!)
    }
}