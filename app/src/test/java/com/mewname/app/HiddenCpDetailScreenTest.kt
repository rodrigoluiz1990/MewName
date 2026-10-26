package com.mewname.app

import android.graphics.BitmapFactory
import android.graphics.Rect
import com.mewname.app.domain.OcrNumericReader
import com.mewname.app.domain.OcrPokemonParser
import com.mewname.app.ocr.OcrResult
import com.mewname.app.ocr.OcrTextBlock
import com.mewname.app.ocr.OcrTextLine
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * POCO X7 Pro capture of a nicknamed Zapdos whose artwork covers the CP header. The OCR could not read
 * the CP, the "NOVO ATAQUE" button was taken as an appraisal label, and the candy family was ignored.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HiddenCpDetailScreenTest {
    private val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private val bounds = Rect(0, 0, 1220, 2712)
    private val lines: List<OcrTextLine> by lazy {
        val json = JSONArray(javaClass.getResourceAsStream("/ocr/zapdos-hidden-cp.json")!!.bufferedReader().use { it.readText() })
        List(json.length()) { index ->
            val item = json.getJSONObject(index)
            val rect = item.getJSONArray("rect")
            OcrTextLine(item.getString("text"), Rect(rect.getInt(0), rect.getInt(1), rect.getInt(2), rect.getInt(3)))
        }
    }
    private fun text(lines: List<OcrTextLine>) = lines.joinToString("\n") { it.text }

    @Test fun detailButtonsConfirmDetailScreenWhenCpIsHidden() {
        assertNull(OcrNumericReader.extractCp(text(lines), lines, bounds))
        assertTrue(OcrNumericReader.isDetailWithoutAppraisal(text(lines), lines, bounds))
    }

    @Test fun hiddenCpWithoutDetailButtonsIsStillNotAssumedToBeDetail() {
        val withoutButtons = lines.filterNot { it.text == "FORTALECER" || it.text == "NOVO ATAQUE" }
        assertFalse(OcrNumericReader.isDetailWithoutAppraisal(text(withoutButtons), withoutButtons, bounds))
    }

    @Test fun visibleAppraisalLabelsStillWinOverDetailButtons() {
        fun line(text: String, y: Int) = OcrTextLine(text, Rect(140, y, 400, y + 40))
        val appraisal = lines + listOf(line("Ataque", 2047), line("Defesa", 2164), line("PS", 2275))
        assertFalse(OcrNumericReader.isDetailWithoutAppraisal(text(appraisal), appraisal, bounds))
    }

    @Test fun candyIdentifiesNicknamedPokemonWhenCpIsHidden() {
        val bitmap = javaClass.getResourceAsStream("/ocr/zapdos-hidden-cp.jpg")!!.use { BitmapFactory.decodeStream(it) }
        try {
            val data = OcrPokemonParser().parse(context, OcrResult(text(lines), bitmap, listOf(OcrTextBlock(text(lines), null, lines))))
            assertEquals("Zapdos", data.pokemonName)
            assertNull(data.attIv)
            assertNull(data.defIv)
            assertNull(data.staIv)
        } finally {
            bitmap.recycle()
        }
    }
}
