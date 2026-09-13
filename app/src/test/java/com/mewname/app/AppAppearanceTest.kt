package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppAppearanceTest {
    @Test fun existingCompactPreferenceIsPreservedWithoutAStyle() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("appearance_test", 0)
        prefs.edit().clear().putBoolean("compact", true).commit()
        val value = AppAppearance.read(prefs)
        assertEquals(AppearanceStyle.GLASS, value.style)
        assertTrue(value.compact)
    }

    @Test fun styleAndArrangementAreStoredIndependently() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("appearance_test", 0)
        for (style in AppearanceStyle.entries) {
            for (compact in listOf(false, true)) {
                prefs.edit().clear().putString("style", style.name).putBoolean("compact", compact).commit()
                assertEquals(AppAppearance(style, compact),
                    AppAppearance.read(context.getSharedPreferences("appearance_test", 0)))
            }
        }
    }

    @Test fun unknownStyleFallsBackWithoutLosingArrangement() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("appearance_test", 0)
        prefs.edit().clear().putString("style", "UNKNOWN").putBoolean("compact", true).commit()
        assertEquals(AppAppearance(AppearanceStyle.GLASS, true), AppAppearance.read(prefs))
    }
}