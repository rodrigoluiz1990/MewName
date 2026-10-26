package com.mewname.app

import android.content.Context
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.OcrPokemonNameReader
import com.mewname.app.domain.PvpRankCalculator
import com.mewname.app.ocr.OcrTextLine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CpIvEvidenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val calculator = PvpRankCalculator()
    @Test fun cpAndHpResolveMissingStaminaWithoutRoundingToNearestCp() {
        val cp = calculator.estimateCpAtLevel(context, "Psyduck", 12, 15, 15, 20.0)!!
        val hp = calculator.estimateHpAtLevel(context, "Psyduck", 15, 20.0)!!
        val result = calculator.resolveIvEvidence(context, "Psyduck", cp, hp, 12, 15, null, 20.0)
        assertEquals(15, result.stamina)
        assertEquals(12, result.attack)
        assertEquals(20.0, result.level)
        assertEquals(0, calculator.resolveIvEvidence(context, "Psyduck", cp + 10, hp, 12, 15, 15, 20.0).matches)
    }
    @Test fun ambiguousCpAndHpDoNotInventIv() {
        val cp = calculator.estimateCpAtLevel(context, "Psyduck", 12, 15, 15, 20.0)!!
        val hp = calculator.estimateHpAtLevel(context, "Psyduck", 15, 20.0)!!
        val result = calculator.resolveIvEvidence(context, "Psyduck", cp, hp)
        assertTrue(result.matches > 1)
        assertNull(result.attack)
        assertNull(result.defense)
        assertNull(result.stamina)
        assertEquals(0, calculator.resolveIvEvidence(context, "Psyduck", cp, null).matches)
    }
    @Test fun regionalStatsRemainIndependent() {
        val cp = calculator.estimateCpAtLevel(context, "Arcanine (Hisui)", 1, 15, 12, 20.0)!!
        val hp = calculator.estimateHpAtLevel(context, "Arcanine (Hisui)", 12, 20.0)!!
        assertEquals(1, calculator.resolveIvEvidence(context, "Arcanine (Hisui)", cp, hp, 1, 15, 12, 20.0).matches)
        assertEquals(0, calculator.resolveIvEvidence(context, "Arcanine", cp, hp, 1, 15, 12, 20.0).matches)
    }
    private fun name(title: String, family: String?): String? {
        val parser = OcrPokemonNameReader()
        val lines = listOf(OcrTextLine(title, Rect(300, 1000, 800, 1070)),
            OcrTextLine("100 / 100 PS", Rect(420, 1120, 650, 1160)),
            OcrTextLine("DOCES DE BULBASAUR", Rect(600, 1480, 970, 1540)))
        return parser.inferDetailPokemonName(context, lines, Rect(0,0,1080,2400), family)
    }
    @Test fun speciesTitleBeatsCandyFamilyAndNicknameDoesNotBecomeEvolution() {
        assertEquals("Venusaur", name("Venusaur", "Bulbasaur"))
        assertEquals("Venusaur", name("Venusavr", "Bulbasaur"))
        assertNull(name("Meu favorito", "Bulbasaur"))
        assertEquals("Venusaur", name("★FE⁹⁸Venusaur", "Bulbasaur"))
    }
    @Test fun candyReadsPortugueseEnglishAndSplitLabelsOutsideOldCrop() {
        val parser = OcrPokemonNameReader()
        for (label in listOf("DOCES DE BULBASAUR", "BULBASAUR CANDY", "CARAMELOS BULBASAUR", "DOCES GG DE BULBASAUR")) {
            val lines = listOf(OcrTextLine(label, Rect(650, 1840, 1000, 1880)))
            val result = parser.extractCandyFamilyName(context, lines, Rect(0,0,1080,2400), null)
            assertEquals(label, "Bulbasaur", result.first)
        }
    }

    @Test fun unanimousStaminaIsRetainedWhenAttackAndDefenseAreAmbiguous() {
        val cp = calculator.estimateCpAtLevel(context, "Psyduck", 0, 8, 15, 50.0)!!
        val hp = calculator.estimateHpAtLevel(context, "Psyduck", 15, 50.0)!!
        val result = calculator.resolveIvEvidence(context, "Psyduck", cp, hp, knownLevel = 50.0)
        assertTrue(result.matches > 1)
        assertNull(result.attack)
        assertNull(result.defense)
        assertEquals(15, result.stamina)
        assertEquals(50.0, result.level)
    }
    @Test fun twoKnownIvsCanConstrainCpWithoutHpButCpAloneIsNotConfirmation() {
        val cp = calculator.estimateCpAtLevel(context, "Psyduck", 12, 15, 15, 20.0)!!
        val result = calculator.resolveIvEvidence(context, "Psyduck", cp, null, 12, 15)
        assertTrue(result.matches > 0)
        assertEquals(12, result.attack)
        assertEquals(15, result.defense)
        assertEquals(0, calculator.resolveIvEvidence(context, "Psyduck", cp, null).matches)
    }
    @Test fun uniqueTitleTypoCanBeResolvedWithoutCandyButNicknameCannot() {
        assertEquals("Venusaur", name("Venusavr", null))
        assertNull(name("Meu favorito", null))
    }
    @Test fun splitCandyLabelsRespectTheirColumnAndDoNotAbsorbOtherFields() {
        val parser = OcrPokemonNameReader()
        fun family(lines: List<OcrTextLine>): String? = parser.extractCandyFamilyName(context, lines,
            Rect(0,0,1080,2400), null).first
        fun line(text: String, y: Int) = OcrTextLine(text, Rect(600,y,920,y+35))
        assertNull(family(listOf(line("DOCES DE",1500))))
        assertEquals("Bulbasaur", family(listOf(line("DOCES DE",1500), line("BULBASAUR",1540))))
        assertEquals("Bulbasaur", family(listOf(line("BULBASAUR",1500), line("CANDY",1540))))
        assertEquals("Bulbasaur", family(listOf(line("DOCES DE BULBASAUR",1500), line("MEGAENERGIA",1540))))
        assertNull(family(listOf(OcrTextLine("BULBASAUR",Rect(50,1500,200,1535)),line("CANDY",1540))))
    }
    @Test fun moltresHpTieRequiresVisibleCp() {
        assertEquals(3218, calculator.estimateCpAtLevel(context, "Moltres", 15, 15, 15, 35.0))
        assertEquals(3242, calculator.estimateCpAtLevel(context, "Moltres", 15, 15, 15, 35.5))
        assertEquals(169, calculator.estimateHpAtLevel(context, "Moltres", 15, 35.0))
        assertEquals(169, calculator.estimateHpAtLevel(context, "Moltres", 15, 35.5))
        assertNull(calculator.estimateLevelFromHpForCandidates(context, listOf("Moltres"), 169, 15))
        assertEquals(35.0, calculator.estimateLevelFromHpForCandidates(context, listOf("Moltres"), 169, 15, 3218, 15, 15)?.level)
        assertEquals(35.5, calculator.estimateLevelFromHpForCandidates(context, listOf("Moltres"), 169, 15, 3242, 15, 15)?.level)
    }
    @Test fun damagedCpAndMissingIvsDoNotBreakHpTie() {
        assertNull(calculator.estimateLevelFromHpForCandidates(context, listOf("Moltres"), 169, 15, 218, 15, 15))
        assertNull(calculator.estimateLevelFromHpForCandidates(context, listOf("Moltres"), 169, 15, 3218, null, 15))
    }

    @Test fun partialCpWithHpAndIvsResolvesMoltresLevel() {
        for (fragment in listOf("218", "18", "321", "3218")) {
            assertEquals(fragment, 35.0, calculator.estimateLevelFromPartialCp(
                context, "Moltres", 169, 15, 15, 15, listOf(fragment))?.level)
        }
        assertEquals(35.5, calculator.estimateLevelFromPartialCp(
            context, "Moltres", 169, 15, 15, 15, listOf("242"))?.level)
    }
    @Test fun partialCpRejectsTiesConflictsAndInsufficientEvidence() {
        for (fragments in listOf(listOf("32"), listOf("18", "42"), listOf("19"),
            listOf("8"), emptyList(), listOf("PR218"))) {
            assertNull(calculator.estimateLevelFromPartialCp(context, "Moltres", 169, 15, 15, 15, fragments))
        }
        assertNull(calculator.estimateLevelFromPartialCp(context, "Moltres", 168, 15, 15, 15, listOf("218")))
        assertNull(calculator.estimateLevelFromPartialCp(context, "Moltres", 169, -1, 15, 15, listOf("218")))
    }
    @Test fun partialCpReaderRestrictsEvidenceToHeader() {
        val reader = com.mewname.app.domain.OcrNumericReader
        assertEquals("218", reader.partialCpDigits("PR218"))
        assertEquals("18", reader.partialCpDigits("18"))
        assertNull(reader.partialCpDigits("15:18"))
        assertNull(reader.partialCpDigits("169 / 169 PS"))
        val lines = listOf(OcrTextLine("PR218", Rect(350, 100, 650, 180)),
            OcrTextLine("169", Rect(350, 1100, 650, 1180)),
            OcrTextLine("88", Rect(950, 40, 1040, 80)))
        assertEquals(listOf("218"), reader.extractPartialCpDigits(lines, Rect(0, 0, 1080, 2400)))
        assertTrue(reader.extractPartialCpDigits(lines, null).isEmpty())
    }
}
