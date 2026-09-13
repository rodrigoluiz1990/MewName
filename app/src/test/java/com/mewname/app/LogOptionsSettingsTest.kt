package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LogOptionsSettingsTest {
    @Test fun hiddenByDefaultAndPersistsAcrossContextsWithoutDiscardingDiagnosticData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = LogOptionsSettings.preferences(context)
        prefs.edit().clear().commit()
        val profile = context.getSharedPreferences("trainer_profile", 0)
        profile.edit().putString("last_read_log", "OCR diagnostic").commit()
        try {
            assertFalse(LogOptionsSettings.enabled(context))
            LogOptionsSettings.setEnabled(context, true)
            val otherContext = context.createPackageContext(context.packageName, 0)
            assertTrue(LogOptionsSettings.enabled(otherContext))
            LogOptionsSettings.setEnabled(otherContext, false)
            assertFalse(LogOptionsSettings.enabled(context))
            assertEquals("OCR diagnostic", profile.getString("last_read_log", null))
        } finally {
            prefs.edit().clear().commit()
            profile.edit().clear().commit()
        }
    }
}