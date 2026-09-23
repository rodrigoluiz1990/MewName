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
}