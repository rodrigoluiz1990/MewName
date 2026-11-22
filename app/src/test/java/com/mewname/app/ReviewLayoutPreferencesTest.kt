package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReviewLayoutPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("home_layout", 0)
    @Before fun clean() { prefs.edit().clear().commit() }
    @Test fun iconSizesAreSharedWithinGroupsAndSurviveBackup() {
        assertEquals(18, ReviewLayout.read(prefs).iconSize(ReviewSelector.EVOLVE))
        prefs.edit().putInt("review_icon_size_MALE", 12).putInt("review_icon_size_EVOLVE", 16).commit()
        val backup = UserBackup.export(context)
        prefs.edit().clear().commit()
        UserBackup.restore(context, backup)
        val layout = ReviewLayout.read(prefs)
        assertEquals(12, layout.iconSize(ReviewSelector.MALE))
        assertEquals(12, layout.iconSize(ReviewSelector.FEMALE))
        assertEquals(16, layout.iconSize(ReviewSelector.EVOLVE))
        assertEquals(18, layout.iconSize(ReviewSelector.PURIFY))
        assertTrue(layout.customized(ReviewSelector.FEMALE))
    }
    @Test fun invalidIconSizesFallBackToCompactDefault() {
        prefs.edit().putInt("review_icon_size_MALE", -1).putInt("review_icon_size_EVOLVE", 1000).commit()
        val layout = ReviewLayout.read(prefs)
        assertEquals(18, layout.iconSize(ReviewSelector.MALE))
        assertEquals(18, layout.iconSize(ReviewSelector.EVOLVE))
    }

    @Test fun previewOrderSurvivesBackupAndKeepsNewOptions() {
        prefs.edit().putString("review_tag_order", "FEMALE,MALE,FEMALE,UNKNOWN,XXL").commit()
        val backup = UserBackup.export(context)
        prefs.edit().clear().commit()
        UserBackup.restore(context, backup)
        val layout = ReviewLayout.read(prefs)
        assertEquals(listOf(ReviewSelector.FEMALE, ReviewSelector.MALE),
            layout.ordered(listOf(ReviewSelector.MALE, ReviewSelector.FEMALE)))
        assertEquals(listOf(ReviewSelector.XXL, ReviewSelector.XXS, ReviewSelector.XS),
            layout.ordered(listOf(ReviewSelector.XXS, ReviewSelector.XS, ReviewSelector.XXL)))
        assertEquals(listOf(ReviewSelector.SHADOW, ReviewSelector.PURIFIED),
            layout.ordered(listOf(ReviewSelector.SHADOW, ReviewSelector.PURIFIED)))
    }

    @Test fun everyNamingFieldHasAConfigurableGroup() {
        com.mewname.app.model.NamingField.entries.forEach { field ->
            val selector = reviewSelectorForField(field)
            org.junit.Assert.assertEquals(1, reviewTagGroups.count { selector in it.selectors })
        }
        listOf("IV %", "Atk", "Def", "HP", "PC").forEach { label ->
            org.junit.Assert.assertNotNull(ReviewSelector.forLabel(label, com.mewname.app.domain.AppLanguage.PT_BR))
        }
    }

    @Test fun existingInstallKeepsOriginalLayout() {
        val layout = ReviewLayout.read(prefs)
        assertFalse(layout.compact)
        assertTrue(ReviewSelector.entries.all { layout.style(it) == ReviewSelectorStyle.TEXT })
    }
    @Test fun selectorPreferencesAreIndependentAndUnknownStylesFallBack() {
        prefs.edit().putBoolean("review_compact", true).putString("review_selector_EVOLVE", "ICON")
            .putString("review_selector_PURIFY", "ICON_TEXT").putString("review_selector_LEGACY", "future-style").commit()
        val layout = ReviewLayout.read(prefs)
        assertTrue(layout.compact)
        assertEquals(ReviewSelectorStyle.ICON, layout.style(ReviewSelector.EVOLVE))
        assertEquals(ReviewSelectorStyle.ICON_TEXT, layout.style(ReviewSelector.PURIFY))
        assertEquals(ReviewSelectorStyle.TEXT, layout.style(ReviewSelector.LEGACY))
    }
    @Test fun backupRestoresLayoutWithoutChangingOtherAppearanceChoices() {
        prefs.edit().putBoolean("review_compact", true).putString("review_selector_LEGACY", "ICON")
            .putString("review_icon_LEGACY", "mission_icons/Item_1203.png").putString("style", "DARK").commit()
        val backup = UserBackup.export(context)
        prefs.edit().clear().commit()
        UserBackup.restore(context, backup)
        assertTrue(ReviewLayout.read(prefs).compact)
        assertEquals(ReviewSelectorStyle.ICON, ReviewLayout.read(prefs).style(ReviewSelector.LEGACY))
        assertEquals("DARK", prefs.getString("style", null))
        assertEquals("mission_icons/Item_1203.png", ReviewLayout.read(prefs).icon(ReviewSelector.LEGACY))
    }
    @Test fun selectorAssetsAreBundled() {
        ReviewSelector.entries.map { it.defaultIcon }.filter { it.isNotEmpty() }.forEach {
            context.assets.open(it).use { input -> assertTrue(input.read() >= 0) }
        }
    }
    @Test fun customIconsNoneAndInvalidPathsHavePredictableFallbacks() {
        prefs.edit().putString("review_icon_EVOLVE", "collection/mega.png")
            .putString("review_icon_PURIFY", "").putString("review_icon_LEGACY", "../../outside.png").commit()
        val layout = ReviewLayout.read(prefs)
        assertEquals("collection/mega.png", layout.icon(ReviewSelector.EVOLVE))
        assertNull(layout.icon(ReviewSelector.PURIFY))
        assertEquals("mission_icons/Item_1204.png", layout.icon(ReviewSelector.LEGACY))
    }

    @Test fun everyBackgroundAndEvolutionOptionHasAnIndependentSelector() {
        for (flag in com.mewname.app.model.EvolutionFlag.entries) {
            assertNotNull(ReviewSelector.valueOf(flag.name))
        }
        for (size in com.mewname.app.model.PokemonSize.entries.filter { it.name != "NORMAL" }) {
            assertNotNull(ReviewSelector.valueOf(size.name))
        }
        for (background in com.mewname.app.model.SpecialBackgroundType.entries) {
            val selector = ReviewSelector.valueOf("BG_${background.name}")
            for (language in com.mewname.app.domain.AppLanguage.entries) {
                assertEquals(selector, ReviewSelector.forLabel(background.localizedLabel(language), language))
            }
        }
        for (language in com.mewname.app.domain.AppLanguage.entries) {
            for (selector in ReviewSelector.entries) {
                assertEquals(selector, ReviewSelector.forLabel(selector.label(language), language))
            }
        }
        assertEquals(ReviewSelector.MALE, ReviewSelector.forLabel("♂", com.mewname.app.domain.AppLanguage.PT_BR))
        assertEquals(ReviewSelector.FEMALE, ReviewSelector.forLabel("♀", com.mewname.app.domain.AppLanguage.PT_BR))
    }

    @Test fun selectorRowsReserveWidthForTextAndToggle() {
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), reviewSelectorRows(listOf(160f, 180f, 190f), 300f, 4f))
        assertEquals(listOf(listOf(0, 1, 2)), reviewSelectorRows(listOf(48f, 48f, 48f), 300f, 4f))
        assertEquals(listOf(listOf(0, 1), listOf(2)), reviewSelectorRows(listOf(180f, 48f, 180f), 300f, 4f))
        assertEquals(listOf(listOf(0), listOf(1)), reviewSelectorRows(listOf(600f, 48f), 300f, 4f))
        assertTrue(reviewSelectorRows(emptyList(), 300f, 4f).isEmpty())
    }

    @Test fun toggleChoiceIsIndependentFromIconPresentation() {
        val layout = ReviewLayout(styles = mapOf(ReviewSelector.LEGACY to ReviewSelectorStyle.ICON),
            toggles = mapOf(ReviewSelector.LEGACY to true, ReviewSelector.EVOLVE to false))
        assertTrue(layout.usesToggle(ReviewSelector.LEGACY))
        assertFalse(layout.usesToggle(ReviewSelector.EVOLVE))
        assertTrue(layout.customized(ReviewSelector.EVOLVE))
        assertTrue(ReviewLayout().usesToggle(ReviewSelector.EVOLVE))
        assertFalse(ReviewLayout(styles = mapOf(ReviewSelector.LEGACY to ReviewSelectorStyle.ICON)).usesToggle(ReviewSelector.LEGACY))
    }
    @Test fun circleAndToggleOptionsSurviveBackupAndInvalidColorsFallBack() {
        prefs.edit().putBoolean("review_toggle_EVOLVE", false).putBoolean("review_circle_EVOLVE", true)
            .putString("review_color_EVOLVE", "PURPLE").putString("review_color_LEGACY", "future-color").commit()
        val backup = UserBackup.export(context)
        prefs.edit().clear().commit()
        UserBackup.restore(context, backup)
        val layout = ReviewLayout.read(prefs)
        assertFalse(layout.usesToggle(ReviewSelector.EVOLVE))
        assertTrue(ReviewSelector.EVOLVE in layout.circles)
        assertEquals(ReviewIconColor.PURPLE, layout.color(ReviewSelector.EVOLVE))
        assertEquals(ReviewIconColor.BLUE, layout.color(ReviewSelector.LEGACY))
    }
    @Test fun existingTypeAndRegionFramesAreNotDoubled() {
        assertTrue(reviewIconHasOwnFrame("types/POKEMON_TYPE_FIRE.png"))
        assertTrue(reviewIconHasOwnFrame("filter_icons/search_icon_region_01_Kanto.png"))
        assertFalse(reviewIconHasOwnFrame("filter_icons/mythical.png"))
    }

    @Test fun groupedTagsCoverEveryOptionOnceAndShareBehaviorButNotIcons() {
        val selectors = reviewTagGroups.flatMap { it.selectors }
        assertEquals(ReviewSelector.entries.toSet(), selectors.toSet())
        assertEquals(selectors.size, selectors.distinct().size)
        val layout = ReviewLayout(styles = mapOf(ReviewSelector.MALE to ReviewSelectorStyle.ICON),
            toggles = mapOf(ReviewSelector.MALE to false), circles = setOf(ReviewSelector.MALE),
            colors = mapOf(ReviewSelector.MALE to ReviewIconColor.GREEN),
            icons = mapOf(ReviewSelector.MALE to "collection/mega.png", ReviewSelector.FEMALE to "collection/purified.png"))
        assertEquals(layout.style(ReviewSelector.MALE), layout.style(ReviewSelector.FEMALE))
        assertFalse(layout.usesToggle(ReviewSelector.FEMALE))
        assertTrue(layout.hasCircle(ReviewSelector.FEMALE))
        assertEquals(ReviewIconColor.GREEN, layout.color(ReviewSelector.FEMALE))
        assertNotEquals(layout.icon(ReviewSelector.MALE), layout.icon(ReviewSelector.FEMALE))
        assertTrue(layout.segmentedGender)
    }
    @Test fun genderSegmentPreferenceSurvivesReload() {
        prefs.edit().putBoolean("review_gender_segmented", false).commit()
        assertFalse(ReviewLayout.read(prefs).segmentedGender)
    }

}
