package com.mewname.app

import android.graphics.*
import com.mewname.app.domain.OcrPokemonParser
import com.mewname.app.model.Gender
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GenderVisualRecognitionTest {
    private fun screen(gender: Gender?, scale: Float = 1f, shade: Int = 173, height: Int = 1356, offset: Float = 0f, background: Int = Color.rgb(247, 250, 250)): Bitmap {
        val bitmap = Bitmap.createBitmap((610 * scale).toInt(), (height * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        canvas.drawColor(background)
        canvas.translate(0f, offset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(shade, shade + 13, shade + 14)
            strokeWidth = 3f; style = Paint.Style.STROKE
        }
        // Approximate the location and geometry in the supplied Wurmple/Zigzagoon captures.
        if (gender == Gender.MALE) {
            canvas.drawCircle(536f, 601f, 10f, paint)
            canvas.drawLine(543f, 594f, 555f, 582f, paint)
            canvas.drawLine(544f, 582f, 555f, 582f, paint)
            canvas.drawLine(555f, 582f, 555f, 593f, paint)
        } else if (gender == Gender.FEMALE) {
            canvas.drawCircle(540f, 591f, 9f, paint)
            canvas.drawLine(540f, 600f, 540f, 612f, paint)
            canvas.drawLine(534f, 606f, 546f, 606f, paint)
        }
        return bitmap
    }
    @Test fun visualFallbackRecognizesBothSymbolsAtDifferentScalesAndContrasts() {
        for (scale in listOf(.75f, 1f, 1.77f)) for (shade in listOf(140, 173, 198)) {
            for (gender in listOf(Gender.MALE, Gender.FEMALE)) {
                val bitmap = screen(gender, scale, shade)
                val result = OcrPokemonParser().detectGender("", emptyList(), Rect(0, 0, bitmap.width, bitmap.height),
                    if (gender == Gender.MALE) "Wurmple" else "Zigzagoon", bitmap)
                assertEquals("scale=$scale shade=$shade ${result.second}", gender, result.first)
                assertEquals("visual_icon", result.second?.source)
                assertNotNull(result.second?.iconRect)
                bitmap.recycle()
            }
        }
    }
    @Test fun blankIsUnknownAndZacianRemainsGenderlessEvenWithVisualNoise() {
        val bitmap = screen(null)
        assertEquals(Gender.UNKNOWN, OcrPokemonParser().detectGender("", emptyList(), null, "Wurmple", bitmap).first)
        assertEquals(Gender.GENDERLESS, OcrPokemonParser().detectGender("", emptyList(), null, "Zacian", screen(Gender.MALE)).first)
    }
    @Test fun shorterCaptureFindsShiftedSymbolWithAndWithoutHpAnchor() {
        for (gender in listOf(Gender.MALE, Gender.FEMALE)) for (anchored in listOf(false, true)) {
            val bitmap = screen(gender, scale = 2f, height = 1215, offset = 55f,
                background = Color.rgb(205, 218, 219))
            val lines = if (anchored) listOf(com.mewname.app.ocr.OcrTextLine("92 / 92 PS",
                Rect(530, 1340, 700, 1370))) else emptyList()
            val result = OcrPokemonParser().detectGender("", lines, Rect(0, 0, bitmap.width, bitmap.height), "Wooper", bitmap)
            assertEquals(result.second.toString(), gender, result.first)
            assertTrue(result.second!!.visualComparisonPerformed)
            assertTrue(result.second!!.notes.contains(if (anchored) "anchor=hp" else "anchor=fallback"))
            assertTrue(result.second!!.notes.contains("rejectedSize="))
            bitmap.recycle()
        }
    }
    @Test fun clippedMaleGlyphIsRetriedWithMoreRoomAroundHpAnchor() {
        for (hpCenter in listOf(1116, 1300)) {
            val offset = if (hpCenter == 1116) -37f else 0f
            val fixture = screen(Gender.MALE, scale = 2f, height = 1215, offset = offset)
            val bitmap = Bitmap.createBitmap(fixture, 0, 0, 1220, 2429)
            val lines = listOf(com.mewname.app.ocr.OcrTextLine("92 / 92 PS",
                Rect(530, hpCenter - 12, 700, hpCenter + 12)))
            val result = OcrPokemonParser().detectGender("", lines, Rect(0, 0, 1220, 2429), "Wurmple", bitmap)
            assertEquals(result.second.toString(), Gender.MALE, result.first)
            assertTrue(result.second!!.notes.contains("edge_retry"))
            bitmap.recycle()
            if (bitmap !== fixture) fixture.recycle()
        }
    }
    @Test fun conflictingOcrAndVisualRemainUnknown() {
        val bitmap = screen(Gender.MALE)
        val result = OcrPokemonParser().detectGender("\u2640",
            listOf(com.mewname.app.ocr.OcrTextLine("\u2640", Rect(525, 580, 555, 612))),
            Rect(0, 0, 610, 1356), "Wurmple", bitmap)
        assertEquals(Gender.UNKNOWN, result.first)
        assertEquals("conflicting_visual_ocr", result.second?.source)
    }
    @Test fun plainCircleAndFilledSpotAreNotGenderSymbols() {
        for (filled in listOf(false, true)) {
            val bitmap = screen(null)
            Canvas(bitmap).drawCircle(540f, 597f, 13f, Paint().apply {
                color = Color.rgb(173, 186, 187); strokeWidth = 3f
                style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
            })
            val result = OcrPokemonParser().detectGender("", emptyList(), null, "Wurmple", bitmap)
            assertEquals(result.second.toString(), Gender.UNKNOWN, result.first)
        }
    }
}
