package com.mewname.app

import com.mewname.app.model.NamingConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PresetOrderTest {
    private val configs = listOf(
        NamingConfig(id = "a", name = "First", customSeparator = "/"),
        NamingConfig(id = "b", name = "Second", maxLength = 15),
        NamingConfig(id = "c", name = "Third")
    )

    @Test fun `reorder retains complete presets through storage round trip`() {
        val reordered = orderedNamingConfigs(configs, listOf("c", "a", "b"))
        assertEquals(listOf(configs[2], configs[0], configs[1]), reordered)
        assertEquals(reordered, decodeSavedPresets(encodeSavedPresets(reordered)).configs)
    }

    @Test fun `stale or duplicate order cannot remove presets`() {
        listOf(listOf("a", "b"), listOf("a", "a", "c"), listOf("a", "b", "missing")).forEach {
            assertEquals(configs, orderedNamingConfigs(configs, it))
        }
    }
}
