package com.mewname.app

import android.graphics.Bitmap
import android.graphics.Color
import com.mewname.app.domain.RaidLevelIconDetector
import com.mewname.app.domain.BattleOcrLine
import org.junit.Assert.assertNull
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

    @Test fun separatePcLabelDoesNotMoveSymbolCropDown() {
        val digits = BattleOcrLine("53394", .28f, .20f, .85f, .26f)
        val label = BattleOcrLine("PC", .17f, .24f, .27f, .27f)
        val anchor = RaidLevelIconDetector.cpAnchor(listOf(label, digits))!!
        assertEquals(digits, anchor)
        assertEquals(5, RaidLevelIconDetector.detect(raidBitmap(5), (anchor.top * 1200).toInt()))
    }

    @Test fun anchorAcceptsJoinedPrefixButRejectsGymAndTimer() {
        val digits = BattleOcrLine("P53394", .25f, .20f, .85f, .26f)
        assertEquals(digits, RaidLevelIconDetector.cpAnchor(listOf(digits)))
        assertEquals(digits.copy(text="53394-"), RaidLevelIconDetector.cpAnchor(listOf(digits.copy(text="53394-"))))
        assertNull(RaidLevelIconDetector.cpAnchor(listOf(digits.copy(top=.05f, bottom=.09f))))
        assertNull(RaidLevelIconDetector.cpAnchor(listOf(digits.copy(text="0:53:394"))))
        assertNull(RaidLevelIconDetector.cpAnchor(listOf(digits.copy(text="PC"))))
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
