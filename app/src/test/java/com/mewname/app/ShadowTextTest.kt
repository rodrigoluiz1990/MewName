package com.mewname.app

import com.mewname.app.domain.OcrVisualAttributeReader
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A Marshadow detail screen read on 2026-10-27 was marked shadow because its name contains SHADOW. */
@RunWith(RobolectricTestRunner::class)
class ShadowTextTest {
    private val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun shadowText(raw: String) =
        OcrVisualAttributeReader().detectSpecialBackground(context, raw, emptyList(), null, null, emptyList()).second.shadowTextMatch

    @Test fun shadowWordsStillCount() {
        assertEquals(true, shadowText("SHADOW MEWTWO"))
        assertEquals(true, shadowText("POKEMON SOMBROSO"))
        assertEquals(true, shadowText("SOMBROSA"))
    }

    @Test fun namesAndMovesContainingShadowDoNot() {
        assertEquals(false, shadowText("MARSHADOW\nDOCES DE MARSHADOW"))
        assertEquals(false, shadowText("SHADOW BALL\nSHADOW CLAW"))
        assertEquals(false, shadowText("SHADOWBALL"))
    }
}
