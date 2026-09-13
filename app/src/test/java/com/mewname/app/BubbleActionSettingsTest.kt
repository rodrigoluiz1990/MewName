package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BubbleActionSettingsTest {
    @Test fun choicesPersistAndCannotBecomeAnEmptyMenu() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("bubble_actions", 0)
        prefs.edit().clear().commit()
        try {
            assertEquals(4, BubbleActionSettings.selected(context).size)
            BubbleActionSettings.save(context, setOf("capture", "friends"))
            assertEquals(setOf("capture", "friends"), BubbleActionSettings.selected(context))
            BubbleActionSettings.save(context, emptySet())
            assertEquals(setOf("capture", "friends"), BubbleActionSettings.selected(context))
            for (count in 1..4) {
                val positions = BubbleMenuPlacement.positions(400, 800, 360, 400, 56, 8, count)
                assertEquals(count, positions.size)
                assertEquals(count, positions.distinct().size)
                positions.forEach { (x, y) ->
                    assertTrue(x >= 0 && x + 56 < 332 && y >= 0 && y + 56 <= 800)
                }
            }
        } finally { prefs.edit().clear().commit() }
    }
}