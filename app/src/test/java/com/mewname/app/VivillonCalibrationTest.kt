package com.mewname.app

import android.content.Context
import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.VivillonIconMatcher
import com.mewname.app.model.VivillonPattern
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VivillonCalibrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val matcher = VivillonIconMatcher()

    @Test fun identifiesBothRealPhonesAndBothPreEvolutionSpecies() {
        for ((file, expected) in listOf("phone-a.png" to VivillonPattern.GARDEN, "phone-b.png" to VivillonPattern.MARINE)) {
            val bitmap = javaClass.getResourceAsStream("/vivillon/" + file)!!.use { BitmapFactory.decodeStream(it) }!!
            for (name in listOf("Scatterbug", "Spewpa")) {
                val result = matcher.detectPattern(context, bitmap, name)
                assertEquals(result.debugInfo.toString(), expected, result.pattern)
                assertTrue(result.debugInfo.accepted)
            }
            bitmap.recycle()
        }
    }

    @Test fun findsReferenceBadgesAtDifferentVerticalPositionsAndSizes() {
        for ((file, expected) in listOf("01-neve congelada.jpg" to VivillonPattern.ICY_SNOW,
            "05-jardim.jpg" to VivillonPattern.GARDEN, "09-marinho.jpg" to VivillonPattern.MARINE,
            "12-deserto.jpg" to VivillonPattern.SANDSTORM, "16-solar.jpg" to VivillonPattern.SUN)) {
            val ref = context.assets.open("unique_pokemon_refs/vivillon/" + file).use { BitmapFactory.decodeStream(it) }!!
            for ((width, height) in listOf(1080 to 2400, 1220 to 2712)) {
                val screen = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(screen)
                canvas.drawColor(Color.rgb(116, 199, 147))
                val side = (width * .08).toInt()
                val top = (height * if (width == 1080) .84 else .72).toInt()
                val left = (width * .17).toInt()
                canvas.drawBitmap(ref, null, Rect(left, top, left + side, top + side), Paint(Paint.FILTER_BITMAP_FLAG))
                val result = matcher.detectPattern(context, screen, "Scatterbug")
                assertEquals(file + " " + width + ": " + result.debugInfo, expected, result.pattern)
                screen.recycle()
            }
            ref.recycle()
        }
    }

    @Test fun appraisalScreenWithoutEvolutionBadgeDoesNotGuess() {
        val bitmap = javaClass.getResourceAsStream("/ocr/scatterbug-redmi.png")!!.use { BitmapFactory.decodeStream(it) }!!
        val result = matcher.detectPattern(context, bitmap, "Scatterbug")
        assertNull(result.debugInfo.toString(), result.pattern)
        bitmap.recycle()
    }
    @Test fun absentBadgeDoesNotGuessAPattern() {
        for (color in listOf(Color.WHITE, Color.BLACK, Color.rgb(116,199,147))) {
            val bitmap = Bitmap.createBitmap(540,1200,Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(color)
            assertNull(matcher.detectPattern(context,bitmap,"Scatterbug").pattern)
            bitmap.recycle()
        }
    }
}