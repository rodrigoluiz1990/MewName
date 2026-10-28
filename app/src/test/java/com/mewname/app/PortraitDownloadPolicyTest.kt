package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PortraitDownloadPolicyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun failedDownloadWaitsForExplicitRefresh() {
        val url = "https://static.pokebattler.com/assets/pokemon/256/test-${System.nanoTime()}.png"
        assertTrue(PortraitDownloadPolicy.allowsNetwork(context, url, refreshToken = 0))

        PortraitDownloadPolicy.recordResult(context, url, downloaded = false)
        assertFalse(PortraitDownloadPolicy.allowsNetwork(context, url, refreshToken = 0))
        assertTrue(PortraitDownloadPolicy.allowsNetwork(context, url, refreshToken = 1))
        assertTrue(context.getSharedPreferences("portrait_downloads", Context.MODE_PRIVATE)
            .getStringSet("failed", emptySet())!!.contains(url))

        PortraitDownloadPolicy.recordResult(context, url, downloaded = true)
        assertTrue(PortraitDownloadPolicy.allowsNetwork(context, url, refreshToken = 0))
    }
}
