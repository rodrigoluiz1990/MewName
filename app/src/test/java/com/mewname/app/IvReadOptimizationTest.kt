package com.mewname.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import com.mewname.app.domain.OcrPokemonParser
import com.mewname.app.ocr.OcrTextLine
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IvReadOptimizationTest {
    private fun sample(name: String, width: Int, height: Int): Pair<Bitmap, List<OcrTextLine>> {
        val original = javaClass.getResourceAsStream("/ocr/$name.png")!!.use { BitmapFactory.decodeStream(it) }!!
        val scaled = Bitmap.createScaledBitmap(original, width, height, true)
        val bitmap = scaled.copy(Bitmap.Config.ARGB_8888, true)
        if (scaled !== original) scaled.recycle()
        val sx = width.toFloat() / original.width
        val sy = height.toFloat() / original.height
        val json = JSONArray(javaClass.getResourceAsStream("/ocr/$name.json")!!.bufferedReader().use {
            it.readText().removePrefix("\uFEFF")
        })
        val lines = List(json.length()) { index ->
            val item = json.getJSONObject(index)
            val rect = item.getJSONArray("rect")
            OcrTextLine(item.getString("text"), Rect(
                (rect.getInt(0) * sx).toInt(), (rect.getInt(1) * sy).toInt(),
                (rect.getInt(2) * sx).toInt(), (rect.getInt(3) * sy).toInt()))
        }
        original.recycle()
        return bitmap to lines
    }

    private fun read(parser: OcrPokemonParser, bitmap: Bitmap, lines: List<OcrTextLine>): List<Any?> {
        val method = OcrPokemonParser::class.java.getDeclaredMethod("detectIvBars", Bitmap::class.java, List::class.java)
            .apply { isAccessible = true }
        val result = method.invoke(parser, bitmap, lines)
        return listOf("attack", "defense", "stamina", "attackRatio", "defenseRatio", "staminaRatio",
            "isReliable", "appraisalPanelRect", "attackBarRect", "defenseBarRect", "staminaBarRect").map {
            result.javaClass.getDeclaredField(it).apply { isAccessible = true }.get(result)
        }
    }

    @Test fun cachedGeometryRechecksNewPokemonAndResolution() {
        val parser = OcrPokemonParser()
        for ((width, height) in listOf(1080 to 2400, 1220 to 2712, 1080 to 2400)) {
            for ((name, ivs) in listOf("psyduck-redmi" to listOf(12,15,15),
                "scatterbug-redmi" to listOf(10,10,10))) {
                val (bitmap, lines) = sample(name, width, height)
                try {
                    val cold = read(OcrPokemonParser(), bitmap, lines)
                    assertEquals(ivs, cold.take(3))
                    repeat(2) { assertEquals(cold, read(parser, bitmap, lines)) }
                    // Changing only pixels must invalidate geometry even with identical OCR.
                    for (y in height * 70 / 100 until height * 90 / 100) {
                        for (x in width * 8 / 100 until width * 40 / 100) bitmap.setPixel(x, y, Color.BLACK)
                    }
                    val changed = read(OcrPokemonParser(), bitmap, lines)
                    assertEquals(changed, read(parser, bitmap, lines))
                    assertNotEquals(cold, changed)
                    assertEquals(read(OcrPokemonParser(), bitmap, emptyList()), read(parser, bitmap, emptyList()))
                } finally { bitmap.recycle() }
            }
        }
    }

    @Test fun panelDisappearingCannotReusePreviousIvs() {
        val parser = OcrPokemonParser()
        val (bitmap, lines) = sample("psyduck-redmi", 1080, 2400)
        try {
            assertEquals(listOf(12,15,15), read(parser, bitmap, lines).take(3))
            bitmap.eraseColor(Color.BLACK)
            val result = read(parser, bitmap, emptyList())
            assertEquals(read(OcrPokemonParser(), bitmap, emptyList()), result)
            assertEquals(false, result[6])
        } finally { bitmap.recycle() }
    }
}
