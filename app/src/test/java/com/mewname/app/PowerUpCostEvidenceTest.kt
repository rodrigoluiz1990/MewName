package com.mewname.app

import android.graphics.Rect
import com.mewname.app.domain.PowerUpCostEvidence
import com.mewname.app.domain.PowerUpCostEvidence.Reading
import com.mewname.app.domain.PowerUpOrigin
import com.mewname.app.ocr.OcrTextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Power-up costs read from POCO X7 Pro detail screens on 2026-10-27 (purified list and a normal Zapdos). */
@RunWith(RobolectricTestRunner::class)
class PowerUpCostEvidenceTest {
    private fun row(vararg cells: Pair<String, Int>) = cells.map { (text, left) ->
        OcrTextLine(text, Rect(left, 2044, left + 30 * text.length, 2094))
    }

    @Test fun readsStardustAndCandyBesideThePowerUpButton() {
        val lines = row("FORTALECER" to 164, "5.000" to 690, "4" to 980) +
            OcrTextLine("100.000", Rect(648, 2604, 830, 2656)) + OcrTextLine("100", Rect(908, 2604, 990, 2656))
        assertEquals(Reading(5000, 4), PowerUpCostEvidence.read(lines))
    }

    @Test fun readsCostsMergedIntoOneOcrLine() {
        assertEquals(Reading(9000, 9), PowerUpCostEvidence.read(row("FORTALECER" to 164, "9.000 9" to 690)))
    }

    @Test fun missingButtonGivesNoEvidence() {
        assertNull(PowerUpCostEvidence.read(row("NOVO ATAQUE" to 132, "100.000" to 648, "100" to 908)))
    }

    @Test fun costsOnlyAPurifiedPokemonCanShow() {
        // Kyogre 7.200/9, Mamoswine 9.000/9 XL, Regigigas 3.600/3, Moltres and Porygon2 7.200/9.
        for (reading in listOf(Reading(7200, 9), Reading(9000, 9), Reading(3600, 3))) {
            assertTrue("$reading", PowerUpCostEvidence.isPurified(PowerUpCostEvidence.origins(reading)))
        }
    }

    @Test fun ambiguousCostsNeedTheExactLevel() {
        // Tyranitar and Dragonite showed 4.500/4: a normal level 27 or a purified level 29 Pokémon.
        val origins = PowerUpCostEvidence.origins(Reading(4500, 4))
        assertTrue(PowerUpOrigin.NORMAL in origins && PowerUpOrigin.PURIFIED in origins)
        assertFalse(PowerUpCostEvidence.isPurified(origins))
        assertTrue(PowerUpCostEvidence.isPurified(PowerUpCostEvidence.origins(Reading(4500, 4), exactLevel = 29.0)))
        assertFalse(PowerUpCostEvidence.isPurified(PowerUpCostEvidence.origins(Reading(4500, 4), exactLevel = 27.0)))
    }

    @Test fun normalPokemonIsNeverPurified() {
        // The hidden-CP Zapdos read on the same device.
        val origins = PowerUpCostEvidence.origins(Reading(5000, 4))
        assertTrue(PowerUpOrigin.NORMAL in origins)
        assertFalse(PowerUpCostEvidence.isPurified(origins))
        assertFalse(PowerUpCostEvidence.isShadow(origins))
    }

    @Test fun shadowCostsAreTwentyPercentHigher() {
        // Level 31: 6.000 x 1.2 Stardust and 6 x 1.2 Candy rounded up; purified 7.200 would cost 9 Candy.
        assertEquals(setOf(PowerUpOrigin.SHADOW), PowerUpCostEvidence.origins(Reading(7200, 8)))
        assertTrue(PowerUpCostEvidence.isShadow(PowerUpCostEvidence.origins(Reading(7200, 8))))
    }

    @Test fun unreadCandyStillDecidesWhenStardustIsUnique() {
        assertTrue(PowerUpCostEvidence.isPurified(PowerUpCostEvidence.origins(Reading(8100, null))))
        assertFalse(PowerUpCostEvidence.isPurified(PowerUpCostEvidence.origins(Reading(7200, null))))
    }
}
