package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TaskIconCatalogTest {
    @Test fun missionsAndTagsShareAnExistingUniqueCatalog() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val icons = missionIcons(context)
        assertEquals(icons, filterTagIcons(context))
        assertEquals(icons.size, icons.distinct().size)
        assertTrue("filter_icons/favorite.png" in icons)
        assertTrue("filter_icons/favorite_empty.png" in icons)
        (icons + "filter_icons/no_icon_pikachu.png").forEach { path ->
            context.assets.open(path).use { assertTrue("Missing/empty asset: $path", it.read() >= 0) }
        }
    }
    @Test fun missionWithoutIconSurvivesReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = DailyMissionStore(context)
        store.preferences.edit().clear().commit()
        val mission = DailyMission(name = "Task without icon", icon = "")
        store.save(mission)
        assertEquals(mission, DailyMissionStore(context).all().single())
        store.complete(mission.id, true)
        assertTrue(store.all().single().completed())
    }
}
