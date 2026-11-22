package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TaskIconCatalogTest {
    @Test fun compactGenderSymbolsAreSharedPersistableAndUseTheExistingGlyphs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val paths = listOf("filter_icons/app_gender_male.txt", "filter_icons/app_gender_female.txt")
        val symbols = listOf("\u2642", "\u2640")
        paths.forEachIndexed { index, path ->
            assertTrue(path in sharedTaskIcons(context))
            assertEquals(symbols[index], context.assets.open(path).bufferedReader().use { it.readText() })
            assertEquals(symbols[index], taskIconSymbol(path))
            assertTrue(taskIconIsMonochrome(path))
            assertTrue(taskIconMatchesSearch(path, if (index == 0) "masculino app" else "feminino app"))
            assertEquals(path, ReviewLayout(icons = mapOf(ReviewSelector.LEGACY to path)).icon(ReviewSelector.LEGACY))
            val bitmap = taskSymbolBitmap(path)
            assertNotNull(bitmap)
            assertEquals(96, bitmap!!.width)
            bitmap.recycle()
        }
        assertNull(taskIconSymbol("filter_icons/unknown.txt"))
    }

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
    @Test fun gameSearchIconsAreSelectableNamedAndAcceptedByReviewLayout() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalog = sharedTaskIcons(context)
        assertEquals(gameSearchIcons.size, gameSearchIcons.map { it.path }.distinct().size)
        assertEquals(18, gameSearchIcons.count { it.path.startsWith("types/") })
        for (icon in gameSearchIcons) {
            assertTrue(icon.path, icon.path in catalog)
            assertEquals(icon.label, filterIconLabel(icon.path))
            assertEquals(icon.path, ReviewLayout(icons = mapOf(ReviewSelector.LEGACY to icon.path)).icon(ReviewSelector.LEGACY))
            context.assets.open(icon.path).use { input ->
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeStream(input, null, bounds)
                assertTrue(icon.path, bounds.outWidth > 0 && bounds.outHeight > 0)
            }
        }
        assertTrue(gameSearchIcons.any { it.monochrome })
        assertFalse(taskIconIsMonochrome("types/POKEMON_TYPE_FIRE.png"))
    }

    @Test fun specialSearchCategoriesArePresentAndFindableWithoutAccents() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val icons = sharedTaskIcons(context)
        val expected = mapOf(
            "fundo especial" to "collection/background.png",
            "localidade" to "filter_icons/search_location.png",
            "efeito aventura" to "adventure_effects/ic_fieldmoveIcon.png",
            "traje" to "filter_icons/costume.png",
            "mitico" to "filter_icons/mythical.png",
            "MÍTICO" to "filter_icons/mythical.png"
        )
        expected.forEach { (query, path) ->
            assertTrue(path, path in icons)
            assertTrue(query, taskIconMatchesSearch(path, query))
            assertEquals(path, ReviewLayout(icons = mapOf(ReviewSelector.LEGACY to path)).icon(ReviewSelector.LEGACY))
        }
        assertFalse(taskIconMatchesSearch("filter_icons/mythical.png", "localidade"))
        assertTrue(taskIconMatchesSearch("filter_icons/mythical.png", "  "))
        assertTrue(taskIconIsMonochrome("adventure_effects/ic_fieldmoveIcon.png"))
    }

}
