package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollectionMissionPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun reset() {
        context.getSharedPreferences("daily_missions", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("personal_collection_v1", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun reorderingPreservesCompletionAndSurvivesReload() {
        val store = DailyMissionStore(context)
        val first = DailyMission("a", "First", "icon-a", "2026-10-20")
        val second = DailyMission("b", "Second", "icon-b")
        store.save(first); store.save(second)
        store.move("b", -1)
        assertEquals(listOf(second, first), DailyMissionStore(context).all())
        store.move("b", -1); store.move("missing", 1); store.move("a", 1)
        assertEquals(listOf(second, first), store.all())
        store.move("b", 1)
        assertEquals(listOf(first, second), store.all())
    }
    @Test fun stickerChangesPreserveOtherCollectionData() {
        val prefs = context.getSharedPreferences("personal_collection_v1", Context.MODE_PRIVATE)
        prefs.edit().putString("existing", "keep").commit()
        val store = StickerCollectionStore(context)
        store.mark("one.webp", true); store.mark("two.webp", true); store.mark("one.webp", false)
        assertEquals(setOf("two.webp"), StickerCollectionStore(context).collected())
        assertEquals("keep", prefs.getString("existing", ""))
    }
    @Test fun defaultsAreCreatedOnlyOnceAndNeverOverwriteExistingMissions() {
        val store = DailyMissionStore(context)
        val defaults = defaultDailyMissions(com.mewname.app.domain.AppLanguage.PT_BR)
        assertEquals(6, defaults.size)
        defaults.forEach { context.assets.open(it.icon).close() }
        store.initializeDefaults(defaults)
        assertEquals(defaults,store.all())
        defaults.forEach { store.delete(it.id) }
        store.initializeDefaults(defaults)
        assertTrue(store.all().isEmpty())
        store.save(DailyMission("custom", "Custom", "icon"))
        store.initializeDefaults(defaults)
        assertEquals(listOf("custom"),store.all().map { it.id })
    }
}
