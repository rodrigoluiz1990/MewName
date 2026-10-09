package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EncounterIvTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun readsObservedDrilburNameplateButRejectsDetailAndRaid() {
        val lines = listOf(BattleOcrLine("Drilbur / PC 53", .24f, .32f, .76f, .36f))
        assertEquals(EncounterReading("Drilbur", 53), EncounterScreenReader.detect(lines, "Drilbur / PC 53"))
        assertNull(EncounterScreenReader.detect(lines, "Drilbur PC 53 FORTALECER DOCES"))
        assertNull(EncounterScreenReader.detect(lines, "Drilbur PC 53 BATALHA"))
        assertNull(EncounterScreenReader.detect(listOf(BattleOcrLine("Drilbur PC 53", .2f, .1f, .8f, .15f)), "Drilbur PC 53"))
    }
    @Test fun everyDrilburCandidateReproducesTheObservedCp() {
        val calculator = PvpRankCalculator()
        val matches = calculator.encounterPossibilities(context, "Drilbur", 53)
        assertTrue(matches.isNotEmpty())
        matches.forEach { iv -> assertEquals(53, calculator.estimateCpAtLevel(context, "Drilbur", iv.atk, iv.def, iv.sta, iv.level)) }
        val perfectCp = calculator.estimateCpAtLevel(context, "Drilbur", 15, 15, 15, 20.0)!!
        assertTrue(calculator.encounterPossibilities(context, "Drilbur", perfectCp, 20.0).any { it.atk == 15 && it.def == 15 && it.sta == 15 })
        assertTrue(calculator.encounterPossibilities(context, "Drilbur", 53, 20.0).isEmpty())
    }
}
