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
    @Test
    fun `early candy thresholds and half levels match upgrade settings`() {
        assertEquals(PowerUpCost(1000, 1), powerUpCostAtLevel(10.5))
        assertEquals(PowerUpCost(1300, 2), powerUpCostAtLevel(11.0))
        assertEquals(PowerUpCost(1600, 2), powerUpCostAtLevel(14.5))
        assertEquals(PowerUpCost(2500, 2), powerUpCostAtLevel(20.5))
        assertEquals(PowerUpCost(3000, 3), powerUpCostAtLevel(21.0))
        assertEquals(PowerUpCost(11600, 16), powerUpCostBetweenLevels(11.0, 15.0))
    }

    @Test
    fun `full ranges separate regular and XL candy`() {
        assertEquals(PowerUpCost(270000, 304), powerUpCostBetweenLevels(1.0, 40.0))
        assertEquals(PowerUpCost(250000, 0, 296), powerUpCostBetweenLevels(40.0, 50.0))
        assertEquals(PowerUpCost(20000, 15, 10), powerUpCostBetweenLevels(39.5, 40.5))
        assertEquals(powerUpCostBetweenLevels(1.0, 50.0), powerUpCostBetweenLevels(1.0, 51.0))
        assertEquals(PowerUpCost(0, 0), powerUpCostAtLevel(50.0))
    }

    @Test
    fun `lower preview reduces total without refunding resources`() {
        assertEquals(PowerUpCost(800, 4), powerUpCostBetweenLevels(1.0, 3.0))
        assertEquals(PowerUpCost(400, 2), powerUpCostBetweenLevels(1.0, 2.0))
        assertEquals(PowerUpCost(0, 0), powerUpCostBetweenLevels(3.0, 1.0))
        assertEquals(PowerUpCost(0, 0), powerUpCostBetweenLevels(Double.NaN, 3.0))
        assertEquals(PowerUpCost(0, 0), powerUpCostBetweenLevels(1.0, Double.POSITIVE_INFINITY))
    }

    @Test
    fun `status modifiers apply to total cost`() {
        assertEquals(PowerUpCost(650, 2), powerUpCostBetweenLevels(11.0, 11.5, isLucky = true))
        assertEquals(PowerUpCost(1560, 3), powerUpCostBetweenLevels(11.0, 11.5, isShadow = true))
        assertEquals(PowerUpCost(1170, 2), powerUpCostBetweenLevels(11.0, 11.5, isPurified = true))
        assertEquals(PowerUpCost(585, 2), powerUpCostBetweenLevels(11.0, 11.5, isLucky = true, isPurified = true))
        assertEquals(PowerUpCost(324000, 365), powerUpCostBetweenLevels(1.0, 40.0, isShadow = true))
        assertEquals(PowerUpCost(12000, 0, 12), powerUpCostBetweenLevels(40.0, 40.5, isShadow = true))
    }
}
