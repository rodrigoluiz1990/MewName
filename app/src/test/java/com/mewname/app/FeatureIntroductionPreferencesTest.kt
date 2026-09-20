package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeatureIntroductionPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    @After
    fun clearPreferences() {
        context.getSharedPreferences("feature_introduction", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `guide is enabled on a fresh installation`() {
        assertTrue(FeatureIntroductionPreferences.isAutoShowEnabled(context))
    }

    @Test
    fun `guide preference can be disabled and enabled again`() {
        FeatureIntroductionPreferences.setAutoShowEnabled(context, false)
        assertFalse(FeatureIntroductionPreferences.isAutoShowEnabled(context))

        FeatureIntroductionPreferences.setAutoShowEnabled(context, true)
        assertTrue(FeatureIntroductionPreferences.isAutoShowEnabled(context))
    }
}
