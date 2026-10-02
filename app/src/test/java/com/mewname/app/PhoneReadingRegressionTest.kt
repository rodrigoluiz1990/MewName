package com.mewname.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import com.mewname.app.domain.OcrPokemonParser
import com.mewname.app.ocr.OcrTextLine
import com.mewname.app.ocr.confirmedSizeBadge
import com.mewname.app.ocr.sizeBadgeCrop
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhoneReadingRegressionTest {
    private fun verifyBars(sample: String, expected: List<Int>, width: Int, height: Int) {
        val original = javaClass.getResourceAsStream("/ocr/$sample.png")!!.use { BitmapFactory.decodeStream(it) }!!
        val bitmap = Bitmap.createScaledBitmap(original, width, height, true)
        val sx = width.toFloat() / original.width
        val sy = height.toFloat() / original.height
        val json = JSONArray(javaClass.getResourceAsStream("/ocr/$sample.json")!!.bufferedReader().use {
            it.readText().removePrefix("\uFEFF")
        })
        val lines = List(json.length()) { index ->
            val item = json.getJSONObject(index)
            val rect = item.getJSONArray("rect")
            OcrTextLine(item.getString("text"), Rect(
                (rect.getInt(0) * sx).toInt(), (rect.getInt(1) * sy).toInt(),
                (rect.getInt(2) * sx).toInt(), (rect.getInt(3) * sy).toInt()))
        }
        val method = OcrPokemonParser::class.java.getDeclaredMethod("detectIvBars", Bitmap::class.java, List::class.java)
        method.isAccessible = true
        val actual = method.invoke(OcrPokemonParser(), bitmap, lines)
        val values = listOf("attack", "defense", "stamina").map {
            actual.javaClass.getDeclaredField(it).apply { isAccessible = true }.get(actual)
        }
        assertEquals(sample + " " + width + "x" + height, expected, values)
        if (bitmap !== original) bitmap.recycle()
        original.recycle()
    }

    @Test fun psyduckDialogueCannotReplaceHpLabelAtDifferentResolutions() {
        verifyBars("psyduck-redmi", listOf(12, 15, 15), 1080, 2400)
        verifyBars("psyduck-redmi", listOf(12, 15, 15), 1220, 2712)
    }

    @Test fun scatterbugBarsStayCorrectAtDifferentResolutions() {
        verifyBars("scatterbug-redmi", listOf(10, 10, 10), 1080, 2400)
        verifyBars("scatterbug-redmi", listOf(10, 10, 10), 1220, 2712)
    }

    @Test fun detailReadMustConfirmSameSizeFamilyWithoutConflictingBadges() {
        assertEquals("XXS", confirmedSizeBadge("XS", listOf("XXS", "0.25m")))
        assertEquals("XXL", confirmedSizeBadge("XL", listOf("XXL")))
        assertNull(confirmedSizeBadge("XS", listOf("XS")))
        assertNull(confirmedSizeBadge("XS", listOf("XXL")))
        assertNull(confirmedSizeBadge("XS", listOf("XXS", "XS")))
        assertNull(confirmedSizeBadge("XS", listOf("!!XXSScat")))
    }

    @Test fun onlySmallHeightBadgesTriggerAnExtraRead() {
        assertNotNull(sizeBadgeCrop(OcrTextLine("XS", Rect(890,1405,917,1437)),1080,2400))
        assertNull(sizeBadgeCrop(OcrTextLine("!!XXSScat", Rect(353,1054,708,1130)),1080,2400))
        assertNull(sizeBadgeCrop(OcrTextLine("XL", Rect(890,1705,917,1737)),1080,2400))
        assertNull(sizeBadgeCrop(OcrTextLine("XXS", Rect(870,1405,917,1437)),1080,2400))
    }
    @Test fun missingBadgeReadUsesHeightAtBothScreenProportions() {
        for ((width, height, bounds) in listOf(
            Triple(1080, 2400, Rect(811,1355,915,1402)),
            Triple(1220, 2712, Rect(900,1410,1050,1470))
        )) {
            val measurement = OcrTextLine("0,80m", bounds)
            val crop = com.mewname.app.ocr.missingSizeBadgeCrop(listOf(measurement), width, height)
            assertNotNull(crop)
            assertEquals(bounds.top, crop!!.bottom)
            assertTrue(crop.top < bounds.top - 30)
            val recognized = OcrTextLine("XXS", Rect(bounds.left,bounds.top-40,bounds.right,bounds.top-10))
            assertNull(com.mewname.app.ocr.missingSizeBadgeCrop(listOf(measurement, recognized), width, height))
        }
    }

    @Test fun missingBadgeReadRejectsOtherFieldsAndAmbiguousText() {
        assertNull(com.mewname.app.ocr.missingSizeBadgeCrop(listOf(OcrTextLine("11,45kg", Rect(100,1300,270,1400))),1080,2400))
        assertNull(com.mewname.app.ocr.missingSizeBadgeCrop(listOf(OcrTextLine("0,80m", Rect(800,1900,940,1950))),1080,2400))
        assertEquals("XXS", com.mewname.app.ocr.unambiguousSizeBadge(listOf("XXS")))
        assertEquals("XXL", com.mewname.app.ocr.unambiguousSizeBadge(listOf("XXL")))
        assertNull(com.mewname.app.ocr.unambiguousSizeBadge(listOf("XXS", "XS")))
        assertNull(com.mewname.app.ocr.unambiguousSizeBadge(listOf("XXSFlamigo", "DOCES XL")))
    }    @Test fun goldenColorAloneCannotClassifySizeButTextStillCan() {
        val bitmap = Bitmap.createBitmap(1080,2400,Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.rgb(210,190,80))
            val method = OcrPokemonParser::class.java.getDeclaredMethod("detectSize", String::class.java,
                List::class.java, Rect::class.java, Bitmap::class.java).apply { isAccessible = true }
            fun detected(text: String): Any? {
                val lines = listOf(OcrTextLine(text, Rect(870,1310,940,1345)))
                return (method.invoke(OcrPokemonParser(),text,lines,Rect(0,0,1080,2400),bitmap) as Pair<*, *>).first
            }
            assertEquals(com.mewname.app.model.PokemonSize.NORMAL, detected(""))
            assertEquals(com.mewname.app.model.PokemonSize.XXS, detected("XXS"))
            assertEquals(com.mewname.app.model.PokemonSize.XXL, detected("XXL"))
        } finally { bitmap.recycle() }
    }}