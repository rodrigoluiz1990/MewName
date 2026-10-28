package com.mewname.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.mewname.app.domain.colorToHsv
import com.mewname.app.domain.forEachSampledPixel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PixelSamplingTest {
    @Test fun hsvMatchesAndroidExactly() {
        val channels = (0..255 step 5) + listOf(1, 2, 3, 4, 127, 128, 254)
        val expected = FloatArray(3)
        val actual = FloatArray(3)
        for (r in channels) for (g in channels) for (b in channels) {
            val color = Color.rgb(r, g, b)
            Color.colorToHSV(color, expected)
            colorToHsv(color, actual)
            for (i in 0..2) assertEquals("rgb($r,$g,$b)[$i]", expected[i].toRawBits(), actual[i].toRawBits())
        }
    }

    @Test fun rowSamplingVisitsTheSamePixelsAsGetPixel() {
        val bitmap = Bitmap.createBitmap(37, 23, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            bitmap.setPixel(x, y, Color.rgb(x * 7, y * 11, (x + y) * 3))
        }
        val region = Rect(3, 2, 35, 21)
        val expected = mutableListOf<Triple<Int, Int, Int>>()
        var y = region.top
        while (y < region.bottom) {
            var x = region.left
            while (x < region.right) {
                expected += Triple(x, y, bitmap.getPixel(x, y))
                x += 4
            }
            y += 3
        }
        val actual = mutableListOf<Triple<Int, Int, Int>>()
        bitmap.forEachSampledPixel(region, 4, 3) { x, sampledY, color -> actual += Triple(x, sampledY, color) }
        assertEquals(expected, actual)
    }
}
