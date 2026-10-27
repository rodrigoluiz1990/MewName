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
        assertEquals(PowerUpCost(324000, 406), powerUpCostBetweenLevels(1.0, 40.0, isShadow = true))
        assertEquals(PowerUpCost(12000, 0, 12), powerUpCostBetweenLevels(40.0, 40.5, isShadow = true))
    }
    @Test
    fun `purified candy matches every Tyranitar and Entei preview from level 30`() {
        val observed = listOf(4, 8, 14, 20, 26, 32, 40, 48, 56, 64,
            73, 82, 91, 100, 111, 122, 133, 144, 158, 172)
        observed.forEachIndexed { index, expected ->
            val target = 30.0 + (index + 1) * 0.5
            assertEquals("Target $target", expected,
                powerUpCostBetweenLevels(30.0, target, isPurified = true).candy)
        }
        assertEquals(PowerUpCost(135000, 172),
            powerUpCostBetweenLevels(30.0, 40.0, isPurified = true))
    }

    @Test
    fun `purified candy matches every Kyogre preview from level 35`() {
        val observed = listOf(9, 18, 27, 36, 47, 58, 69, 80, 94, 108)
        observed.forEachIndexed { index, expected ->
            assertEquals(expected,
                powerUpCostBetweenLevels(35.0, 35.0 + (index + 1) * 0.5, isPurified = true).candy)
        }
        assertEquals(PowerUpCost(79200, 108),
            powerUpCostBetweenLevels(35.0, 40.0, isPurified = true))
        assertEquals(118, powerUpCostBetweenLevels(35.0, 40.0).candy)
    }

    @Test
    fun `purified ordinary candy handles low costs lucky and XL boundary`() {
        assertEquals(PowerUpCost(720, 4),
            powerUpCostBetweenLevels(1.0, 3.0, isPurified = true))
        assertEquals(PowerUpCost(39600, 108),
            powerUpCostBetweenLevels(35.0, 40.0, isLucky = true, isPurified = true))
        assertEquals(PowerUpCost(18000, 14, 9),
            powerUpCostBetweenLevels(39.5, 40.5, isPurified = true))
        assertEquals(powerUpCostBetweenLevels(35.0, 40.0, isShadow = true),
            powerUpCostBetweenLevels(35.0, 40.0, isShadow = true, isPurified = true))
    }

    @Test
    fun `Rocket capture profiles reproduce every Dragonite step`() {
        val dust = listOf(4500,9000,14400,19800,25200,30600,36900,43200,49500,55800,
            63000,70200,77400,84600,91800,99900,108000,116100,125100,134100)
        val candy = listOf(4,8,14,20,26,32,40,48,56,64,73,82,91,100,109,120,131,142,156,170)
        for (capture in listOf(8, 13)) dust.indices.forEach { i ->
            assertEquals("Capture $capture, step $i", PowerUpCost(dust[i], candy[i]),
                powerUpCostBetweenLevels(30.0, 30.5 + i * .5, isPurified = true, captureLevel = capture))
        }
    }

    @Test
    fun `Rocket profiles match Moltres steps and Ralts total without affecting later intervals`() {
        val dust = listOf(7200,14400,21600,28800,36000,44100,52200,60300,69300,78300)
        val candy = listOf(9,18,27,36,45,56,67,78,92,106)
        for (capture in listOf(8, 13)) {
            dust.indices.forEach { i ->
                assertEquals(PowerUpCost(dust[i], candy[i]),
                    powerUpCostBetweenLevels(35.0, 35.5 + i * .5, isPurified = true, captureLevel = capture))
            }
            assertEquals(PowerUpCost(173700,208),
                powerUpCostBetweenLevels(25.0,40.0,isPurified=true,captureLevel=capture))
            assertEquals(powerUpCostBetweenLevels(37.5,40.0,isPurified=true),
                powerUpCostBetweenLevels(37.5,40.0,isPurified=true,captureLevel=capture))
        }
    }

    @Test
    fun `raid and unknown capture retain nominal purified estimates`() {
        for (capture in listOf(null,20,25)) {
            assertEquals(PowerUpCost(79200,108),
                powerUpCostBetweenLevels(35.0,40.0,isPurified=true,captureLevel=capture))
            assertEquals(PowerUpCost(135000,172),
                powerUpCostBetweenLevels(30.0,40.0,isPurified=true,captureLevel=capture))
        }
        assertEquals(PowerUpCost(7200,9), powerUpCostBetweenLevels(37.0,37.5,isPurified=true,captureLevel=8))
        assertEquals(PowerUpCost(8100,11), powerUpCostBetweenLevels(37.0,37.5,isPurified=true,captureLevel=20))
    }

    @Test
    fun `capture lookup rejects invalid inputs and preserves other status calculations`() {
        for (capture in listOf(null,0,36,38)) assertEquals(37.0, powerUpCostLookupLevel(37.0,capture), 0.0)
        assertEquals(34.0, powerUpCostLookupLevel(35.0,4), 0.0)
        assertEquals(12.0, powerUpCostLookupLevel(13.0,3), 0.0)
        assertEquals(37.5, powerUpCostLookupLevel(37.5,8), 0.0)
        assertEquals(powerUpCostBetweenLevels(30.0,40.0),
            powerUpCostBetweenLevels(30.0,40.0,captureLevel=8))
        assertEquals(PowerUpCost(39150,106),
            powerUpCostBetweenLevels(35.0,40.0,isPurified=true,isLucky=true,captureLevel=8))
    }
    @Test
    fun `shadow origin changes costs while normal and lucky ignore origin`() {
        assertEquals(PowerUpCost(104400,141),
            powerUpCostBetweenLevels(35.0,40.0,isShadow=true,captureLevel=8))
        assertEquals(PowerUpCost(105600,144),
            powerUpCostBetweenLevels(35.0,40.0,isShadow=true,captureLevel=20))
        assertEquals(PowerUpCost(105600,144),
            powerUpCostBetweenLevels(35.0,40.0,isShadow=true))
        assertEquals(PowerUpCost(9600,12),
            powerUpCostBetweenLevels(37.0,37.5,isShadow=true,captureLevel=13))
        assertEquals(PowerUpCost(10800,15),
            powerUpCostBetweenLevels(37.0,37.5,isShadow=true,captureLevel=25))
        assertEquals(PowerUpCost(88000,118),
            powerUpCostBetweenLevels(35.0,40.0,captureLevel=8))
        assertEquals(PowerUpCost(44000,118),
            powerUpCostBetweenLevels(35.0,40.0,isLucky=true,captureLevel=8))
    }
}
