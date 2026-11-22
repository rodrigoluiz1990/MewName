package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReviewScreenLayoutTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("home_layout", 0)
    @Before fun clear() { prefs.edit().clear().commit() }
    @Test fun appearanceOnlyChangesEnableCustomGridAndSurviveBackup() {
        assertNull(ReviewLayout.read(prefs).tabs)
        listOf("review_form_segmented", "review_gender_segmented", "review_compact").forEach { key ->
            prefs.edit().clear().putBoolean(key, true).commit()
            assertEquals(defaultReviewScreenTabs, ReviewLayout.read(prefs).tabs)
        }
        prefs.edit().clear().putBoolean("review_form_segmented", true).commit()
        val backup = UserBackup.export(context)
        prefs.edit().clear().commit()
        UserBackup.restore(context, backup)
        assertTrue(ReviewLayout.read(prefs).segmentedForm)
        assertEquals(defaultReviewScreenTabs, ReviewLayout.read(prefs).tabs)
        prefs.edit().clear().putString("review_icon_SHADOW", "").commit()
        assertNotNull(ReviewLayout.read(prefs).tabs)
        prefs.edit().clear().commit()
        assertNull(ReviewLayout.read(prefs).tabs)
    }
    @Test fun totalBlockHeightReservesHeadersAndWrappedRows() {
        assertEquals(28, reviewControlHeightForBlock(48, 24, 20f))
        assertEquals(24, reviewControlHeightForBlock(24, 24, 20f))
        assertEquals(48, reviewControlHeightForBlock(48, 28, 0f))
        // Two minimum-height rows plus a 20dp header: natural block height is 68dp.
        assertEquals(50, reviewControlHeightForBlock(120, 24, 44f, 2))
    }
    @Test fun groupWidthReservesCardInsetsAndBothGenderIcons() {
        val layout = ReviewLayout(iconSizes = mapOf(ReviewSelector.MALE to 24))
        assertEquals(84f, layout.minimumFieldWidth("gender"), .01f)
        assertEquals(76f, layout.copy(compact = true).minimumFieldWidth("gender"), .01f)
        val widths = reviewScreenWidths(listOf("gender"), layout.copy(spans = mapOf("gender" to 5)), 336f)
        assertEquals(84f, widths.getValue("gender"), .01f)
    }
    @Test fun largeCompactGenderSymbolsKeepSameMinimumHeightAsTextFields() {
        val layout = ReviewLayout(iconSizes = mapOf(ReviewSelector.MALE to 24),
            heights = mapOf("gender" to 24, "POKEMON_NAME" to 24, "LEVEL" to 24))
        assertEquals(layout.fieldHeight("POKEMON_NAME"), layout.fieldHeight("gender"))
        assertEquals(layout.fieldHeight("LEVEL"), layout.fieldHeight("gender"))
        val icons = layout.copy(styles = mapOf(ReviewSelector.MALE to ReviewSelectorStyle.ICON))
        assertEquals(24, icons.fieldHeight("gender"))
        assertEquals(28, icons.copy(circles = setOf(ReviewSelector.MALE)).fieldHeight("gender"))
    }
    @Test fun smallPercentageAndHeightPreserveConfiguredIconSize() {
        val layout = ReviewLayout(styles = mapOf(ReviewSelector.EVOLVE to ReviewSelectorStyle.ICON),
            toggles = mapOf(ReviewSelector.EVOLVE to false), spans = mapOf("EVOLVE" to 5),
            heights = mapOf("EVOLVE" to 24), iconSizes = mapOf(ReviewSelector.EVOLVE to 24))
        assertEquals(24, layout.iconSize(ReviewSelector.EVOLVE))
        assertEquals(32, layout.fieldHeight("EVOLVE"))
        assertEquals(32f, reviewScreenWidths(listOf("EVOLVE"), layout, 336f).getValue("EVOLVE"), .01f)
        assertEquals(80f, reviewScreenWidths(listOf("EVOLVE"), layout.copy(toggles = mapOf(ReviewSelector.EVOLVE to true)), 336f).getValue("EVOLVE"), .01f)
    }
    @Test fun legacyWidthsMigrateAndFivePercentStepsSurviveReload() {
        prefs.edit().putString("review_field_spans", """{"POKEMON_NAME":6,"LEVEL":2,"EVOLVE":-1,"PURIFY":4}""").commit()
        assertEquals(mapOf("POKEMON_NAME" to 50, "LEVEL" to 15, "EVOLVE" to -1, "PURIFY" to 35), readReviewSpans(prefs))
        for (percent in 5..100 step 5) {
            saveReviewSpan(prefs, "LEVEL", percent)
            assertEquals(percent, ReviewLayout.read(prefs).fieldSpan("LEVEL"))
        }
        assertEquals(50, readReviewSpans(prefs)["POKEMON_NAME"])
        assertFalse(prefs.contains("review_field_spans"))
    }
    @Test fun individualHeightsSurviveBackupWithoutChangingWidths() {
        saveReviewSpan(prefs, "POKEMON_NAME", 50)
        saveReviewHeight(prefs, "POKEMON_NAME", 24)
        saveReviewHeight(prefs, "LEVEL", 28)
        saveReviewHeight(prefs, "CP", -1)
        val backup = UserBackup.export(context)
        prefs.edit().clear().commit()
        UserBackup.restore(context, backup)
        val layout = ReviewLayout.read(prefs)
        assertEquals(24, layout.fieldHeight("POKEMON_NAME"))
        assertEquals(28, layout.fieldHeight("LEVEL"))
        assertEquals(36, layout.fieldHeight("CP"))
        assertEquals(50, layout.fieldSpan("POKEMON_NAME"))
        assertNotNull(layout.tabs)
    }
    @Test fun compactIconHeightAlsoLimitsItsFittedWidthAndProtectsToggle() {
        saveReviewSpan(prefs, "EVOLVE", -1)
        saveReviewHeight(prefs, "EVOLVE", 24)
        val layout = ReviewLayout.read(prefs)
        assertEquals(28, layout.fieldHeight("EVOLVE"))
        assertEquals(26f, reviewScreenWidths(listOf("EVOLVE"), layout, 336f).getValue("EVOLVE"), .01f)
        assertEquals(28, layout.copy(toggles = mapOf(ReviewSelector.EVOLVE to true)).fieldHeight("EVOLVE"))
        assertEquals(24, layout.copy(heights = mapOf("gender" to 24)).fieldHeight("gender"))
    }
    @Test fun legacyIvBlockExpandsInPlaceAndKeepsHiddenState() {
        val legacy = listOf(ReviewLayoutTab("custom", "Custom", listOf("LEVEL", "IV_COMBINATION", "CP")))
        val migrated = normalizeReviewTabs(legacy)
        assertEquals(listOf("LEVEL", "IV_ATTACK", "IV_DEFENSE", "IV_STAMINA", "CP"), migrated.single().fields.take(5))
        prefs.edit().putStringSet("review_hidden_fields", setOf("IV_COMBINATION")).commit()
        val layout = ReviewLayout.read(prefs)
        reviewIvFields.forEach { assertFalse(layout.fieldVisible(it)) }
        setReviewFieldVisible(prefs, "IV_COMBINATION", true)
        reviewIvFields.forEach { assertTrue(ReviewLayout.read(prefs).fieldVisible(it)) }
    }
    @Test fun widthsPackNameGenderLevelAndIndividualIvsWithoutStretching() {
        val layout = ReviewLayout()
        fun rows(keys: List<String>, config: ReviewLayout = layout) = reviewScreenRows(keys, reviewScreenWidths(keys, config, 336f), 336f)
        assertEquals(listOf(listOf("POKEMON_NAME", "gender", "LEVEL")), rows(listOf("POKEMON_NAME", "gender", "LEVEL")))
        assertEquals(listOf(listOf("IV_PERCENT") + reviewIvFields), rows(listOf("IV_PERCENT") + reviewIvFields))
        assertEquals(listOf(listOf("EVOLVE", "PURIFY"), listOf("POKEMON_NAME")), rows(listOf("EVOLVE", "PURIFY", "POKEMON_NAME")))
        assertEquals(listOf(listOf("POKEMON_NAME"), listOf("gender", "LEVEL")),
            rows(listOf("POKEMON_NAME", "gender", "LEVEL"), layout.copy(spans = mapOf("POKEMON_NAME" to 100))))
    }
    @Test fun fitIconUsesActualIconWidthAndCanMixWithPercentageFields() {
        saveReviewSpan(prefs, "EVOLVE", -1)
        saveReviewSpan(prefs, "PURIFY", -1)
        val layout = ReviewLayout.read(prefs)
        assertEquals(ReviewSelectorStyle.ICON, layout.style(ReviewSelector.EVOLVE))
        assertFalse(layout.usesToggle(ReviewSelector.EVOLVE))
        val keys = listOf("EVOLVE", "PURIFY", "POKEMON_NAME")
        val widths = reviewScreenWidths(keys, layout, 336f)
        assertEquals(26f, widths.getValue("EVOLVE"), .01f)
        assertEquals(26f, widths.getValue("PURIFY"), .01f)
        assertEquals(listOf(keys), reviewScreenRows(keys, widths, 336f))
        assertEquals(26f, reviewScreenWidths(keys, layout, 560f).getValue("EVOLVE"), .01f)
        assertEquals(20f, reviewScreenWidths(keys, layout.copy(iconSizes = mapOf(ReviewSelector.EVOLVE to 12)), 336f).getValue("EVOLVE"), .01f)
        val backup = UserBackup.export(context)
        prefs.edit().clear().commit()
        UserBackup.restore(context, backup)
        assertEquals(-1, ReviewLayout.read(prefs).fieldSpan("EVOLVE"))
    }
    @Test fun fitIconFallsBackWhenTextOrToggleIsEnabled() {
        val layout = ReviewLayout(spans = mapOf("EVOLVE" to -1, "POKEMON_NAME" to -1))
        assertEquals(defaultReviewSpan("EVOLVE"), layout.fieldSpan("EVOLVE"))
        assertEquals(defaultReviewSpan("POKEMON_NAME"), layout.fieldSpan("POKEMON_NAME"))
        assertEquals(defaultReviewSpan("EVOLVE"), layout.copy(styles = mapOf(ReviewSelector.EVOLVE to ReviewSelectorStyle.ICON),
            toggles = mapOf(ReviewSelector.EVOLVE to true)).fieldSpan("EVOLVE"))
    }
    @Test fun widthsSurviveBackupAndInvalidSizesAreIgnored() {
        saveReviewSpan(prefs, "POKEMON_NAME", 35)
        saveReviewSpan(prefs, "IV_ATTACK", 50)
        saveReviewSpan(prefs, "LEVEL", 0)
        assertEquals(25, ReviewLayout.read(prefs).fieldSpan("LEVEL"))
        val backup = UserBackup.export(context)
        prefs.edit().clear().commit()
        UserBackup.restore(context, backup)
        assertEquals(35, ReviewLayout.read(prefs).fieldSpan("POKEMON_NAME"))
        assertEquals(50, ReviewLayout.read(prefs).fieldSpan("IV_ATTACK"))
        assertNotNull(ReviewLayout.read(prefs).tabs)
    }

    @Test fun hidingAnEntireFieldPreservesItsTabAndInternalChoices() {
        val tabs = moveReviewField(defaultReviewScreenTabs + ReviewLayoutTab("custom", "Custom", emptyList()), "evolution", "custom")
        saveReviewTabs(prefs, tabs)
        setReviewOptionVisible(prefs, ReviewSelector.MEGA, false)
        setReviewFieldVisible(prefs, "evolution", false)
        assertFalse(ReviewLayout.read(prefs).fieldVisible("evolution"))
        assertEquals(tabs, ReviewLayout.read(prefs).tabs)
        setReviewFieldVisible(prefs, "evolution", true)
        assertTrue(ReviewLayout.read(prefs).fieldVisible("evolution"))
        assertFalse(ReviewLayout.read(prefs).optionVisible(ReviewSelector.MEGA))
    }
    @Test fun aGroupWithNoActiveOptionsDisappearsAndCanBeRestored() {
        val options = reviewTagGroups.first { it.key == "evolution" }.selectors
        options.forEach { setReviewOptionVisible(prefs, it, false) }
        assertFalse(ReviewLayout.read(prefs).fieldVisible("evolution"))
        setReviewOptionVisible(prefs, ReviewSelector.GIGANTAMAX, true)
        assertTrue(ReviewLayout.read(prefs).fieldVisible("evolution"))
        assertEquals(listOf(ReviewSelector.GIGANTAMAX), ReviewLayout.read(prefs).ordered(options))
        assertNotNull(ReviewLayout.read(prefs).tabs)
    }
    @Test fun reorderingVisibleFieldsDoesNotMoveHiddenFieldsToAnotherTab() {
        val original = listOf("LEVEL", "CP", "gender", "IV_PERCENT")
        assertEquals(listOf("IV_PERCENT", "CP", "gender", "LEVEL"),
            mergeReviewVisibleOrder(original, listOf("IV_PERCENT", "gender", "LEVEL")))
        assertEquals(original, mergeReviewVisibleOrder(original, emptyList()))
    }
    @Test fun visibilitySurvivesBackupAndKeepsExistingOrder() {
        setReviewFieldVisible(prefs, "CP", false)
        setReviewOptionVisible(prefs, ReviewSelector.FEMALE, false)
        prefs.edit().putString("review_tag_order", "FEMALE,MALE").commit()
        val backup = UserBackup.export(context)
        prefs.edit().clear().commit()
        UserBackup.restore(context, backup)
        val layout = ReviewLayout.read(prefs)
        assertFalse(layout.fieldVisible("CP"))
        assertTrue(layout.fieldVisible("gender"))
        assertEquals(listOf(ReviewSelector.MALE), layout.ordered(listOf(ReviewSelector.MALE, ReviewSelector.FEMALE)))
        setReviewOptionVisible(prefs, ReviewSelector.FEMALE, true)
        assertEquals(listOf(ReviewSelector.FEMALE, ReviewSelector.MALE), ReviewLayout.read(prefs).ordered(listOf(ReviewSelector.MALE, ReviewSelector.FEMALE)))
    }

    @Test fun everyNamingGroupIsAvailableInTheGeneralPreview() {
        assertEquals(reviewScreenKeys.size, reviewScreenKeys.distinct().size)
        reviewTagGroups.forEach { assertTrue(it.key, reviewScreenKey(it.key) in reviewScreenKeys) }
        assertNull(ReviewLayout.read(prefs).tabs)
    }
    @Test fun fieldCanMoveAcrossTabsWithoutLossOrDuplication() {
        val tabs = defaultReviewScreenTabs + ReviewLayoutTab("mine", "My tab", emptyList())
        val moved = moveReviewField(tabs, "IV_ATTACK", "mine")
        assertEquals(listOf("IV_ATTACK"), moved.last().fields)
        assertFalse("IV_ATTACK" in moved.first().fields)
        assertEquals(reviewScreenKeys.toSet(), moved.flatMap { it.fields }.toSet())
        assertEquals(reviewScreenKeys.size, moved.sumOf { it.fields.size })
        assertEquals(moved, moveReviewField(moved, "IV_ATTACK", "mine"))
        assertEquals(moved, moveReviewField(moved, "LEVEL", "missing"))
    }
    @Test fun allFieldsCanShareOneTabInCustomOrder() {
        val tabs = normalizeReviewTabs(listOf(ReviewLayoutTab("single", "All", reviewScreenKeys.reversed())))
        assertEquals(1, tabs.size)
        assertEquals(reviewScreenKeys.reversed(), tabs.single().fields)
    }
    @Test fun malformedAndOlderPreferencesDoNotLoseFields() {
        val tabs = normalizeReviewTabs(listOf(ReviewLayoutTab("general", fields = listOf("LEVEL", "LEVEL", "unknown")),
            ReviewLayoutTab("general", fields = listOf("CP")), ReviewLayoutTab("extra", fields = listOf("LEVEL", "CP"))))
        assertEquals(2, tabs.size)
        assertEquals(reviewScreenKeys.toSet(), tabs.flatMap { it.fields }.toSet())
        assertEquals(reviewScreenKeys.size, tabs.sumOf { it.fields.size })
        prefs.edit().putString("review_screen_tabs", "invalid").commit()
        assertNull(readReviewTabs(prefs))
    }
    @Test fun tabsAndInternalOrderSurviveBackupIndependently() {
        val tabs = moveReviewField(defaultReviewScreenTabs + ReviewLayoutTab("custom", "Custom", emptyList()), "gender", "custom")
        saveReviewTabs(prefs, tabs.map { if (it.id == "general") it.copy(fields = it.fields.reversed()) else it })
        prefs.edit().putString("review_tag_order", "FEMALE,MALE").commit()
        val expected = ReviewLayout.read(prefs)
        val backup = UserBackup.export(context)
        prefs.edit().clear().commit()
        UserBackup.restore(context, backup)
        assertEquals(expected.tabs, ReviewLayout.read(prefs).tabs)
        assertEquals(expected.order, ReviewLayout.read(prefs).order)
        assertEquals("Custom", ReviewLayout.read(prefs).tabs!!.last().title)
    }
}
