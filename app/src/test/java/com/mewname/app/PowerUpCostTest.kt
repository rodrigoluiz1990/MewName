package com.mewname.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PowerUpCostTest {
    @Test
    fun `Wurmple level 24 to 30 matches game preview`() {
        assertEquals(PowerUpCost(51000, 44), powerUpCostBetweenLevels(24.0, 30.0))
    }

    @Test
    fun `candy increases at level 26 without changing dust`() {
        assertEquals(PowerUpCost(4000, 3), powerUpCostBetweenLevels(25.5, 26.0))
        assertEquals(PowerUpCost(4000, 4), powerUpCostBetweenLevels(26.0, 26.5))
        assertEquals(PowerUpCost(4000, 4), powerUpCostBetweenLevels(26.5, 27.0))
        assertEquals(PowerUpCost(0, 0), powerUpCostBetweenLevels(30.0, 30.0))
    }
}