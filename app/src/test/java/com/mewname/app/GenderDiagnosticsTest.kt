package com.mewname.app

import android.graphics.Rect
import com.mewname.app.domain.OcrPokemonParser
import com.mewname.app.model.Gender
import com.mewname.app.ocr.OcrTextLine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GenderDiagnosticsTest {
    private val parser = OcrPokemonParser()
    private val bounds = Rect(0, 0, 1000, 1000)
    @Test fun `catalog genderless takes precedence over OCR`() {
        val result = parser.detectGender("♂", listOf(OcrTextLine("♂", Rect(800, 400, 840, 440))), bounds, "Zacian (Hero)", null)
        assertEquals(Gender.GENDERLESS, result.first)
        assertEquals("species_catalog", result.second?.source)
    }
    @Test fun `symbol outside relevant regions cannot determine gender`() {
        val result = parser.detectGender("♂", listOf(OcrTextLine("♂", Rect(100, 900, 140, 940))), bounds, "Wurmple", null)
        assertEquals(Gender.UNKNOWN, result.first)
        assertTrue(result.second!!.rawMaleSymbol)
        assertTrue(result.second!!.candidateLines.isEmpty())
    }
    @Test fun `regional female is detected and conflicting signals remain unknown`() {
        val female = OcrTextLine("♀", Rect(800, 400, 840, 440))
        assertEquals(Gender.FEMALE, parser.detectGender("♀", listOf(female), bounds, "Wurmple", null).first)
        val conflict = parser.detectGender("♂ ♀", listOf(female, female.copy(text = "♂")), bounds, "Wurmple", null)
        assertEquals(Gender.UNKNOWN, conflict.first)
        assertEquals("conflicting_regional_ocr", conflict.second?.source)
    }
}