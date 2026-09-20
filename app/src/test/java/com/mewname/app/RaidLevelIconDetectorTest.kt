package com.mewname.app

import android.graphics.Bitmap
import android.graphics.Color
import com.mewname.app.domain.RaidLevelIconDetector
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RaidLevelIconDetectorTest {
    @Test fun countsThreeAndFiveRepeatedRaidSymbols() {
        assertEquals(3, RaidLevelIconDetector.detect(raidBitmap(3), cpTop = 240))
        assertEquals(5, RaidLevelIconDetector.detect(raidBitmap(5), cpTop = 240))
    }

    private fun raidBitmap(count: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(600, 1200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(115, 170, 205))
        val spacing = 48
        val first = 300 - (count - 1) * spacing / 2
        repeat(count) { index ->
            val center = first + index * spacing
            for (y in 177..213) for (x in center - 18..center + 18) {
                val color = if (((x - center + 18) / 3 + (y - 177) / 3) % 2 == 0) Color.WHITE else Color.rgb(35, 45, 55)
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }
}
