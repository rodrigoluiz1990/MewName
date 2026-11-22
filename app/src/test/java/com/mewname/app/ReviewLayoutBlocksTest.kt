package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReviewLayoutBlocksTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("home_layout", 0)
    @Before fun clear() { prefs.edit().clear().commit() }
    private val tab get() = defaultReviewScreenTabs.first().copy(blocks = listOf(
        ReviewLayoutBlock("ivs", "IVs", 100, listOf("IV_PERCENT", "IV_ATTACK", "IV_DEFENSE", "IV_STAMINA"))))

    @Test fun dragPreviewMovesNeighborsWithoutMutatingSavedOrder() {
        val saved = listOf("POKEMON_NAME", "block:ivs", "LEVEL")
        val slots = mapOf(
            "POKEMON_NAME" to androidx.compose.ui.geometry.Rect(0f, 0f, 100f, 50f),
            "block:ivs" to androidx.compose.ui.geometry.Rect(0f, 60f, 100f, 110f),
            "LEVEL" to androidx.compose.ui.geometry.Rect(0f, 120f, 100f, 170f))
        val preview = reviewDragOrder(saved, "block:ivs", androidx.compose.ui.geometry.Offset(50f, 25f), slots)
        assertEquals(listOf("block:ivs", "POKEMON_NAME", "LEVEL"), preview)
        assertEquals(listOf("POKEMON_NAME", "block:ivs", "LEVEL"), saved)
        val shifted = slots + ("block:ivs" to slots.getValue("POKEMON_NAME")) + ("POKEMON_NAME" to slots.getValue("block:ivs"))
        assertEquals(preview, reviewDragOrder(preview, "block:ivs", androidx.compose.ui.geometry.Offset(50f, 25f), shifted))
        assertEquals(saved, reviewDragOrder(saved, "block:ivs", androidx.compose.ui.geometry.Offset(300f, 25f), slots))
    }
    @Test fun oldTabsRemainUnchangedWhenNoBlocksExist() {
        saveReviewTabs(prefs, defaultReviewScreenTabs)
        assertEquals(defaultReviewScreenTabs, ReviewLayout.read(prefs).tabs)
        assertEquals(tab.fields, reviewTabItems(tab.copy(blocks = emptyList())))
    }
    @Test fun groupedFieldsAppearOnlyOnceAndKeepTheirOrder() {
        val items = reviewTabItems(tab)
        assertTrue("block:ivs" in items)
        assertFalse("IV_ATTACK" in items)
        val flattened = items.flatMap { key -> tab.blocks.firstOrNull { it.key == key }?.fields ?: listOf(key) }
        assertEquals(tab.fields.toSet(), flattened.toSet())
        assertEquals(flattened.size, flattened.distinct().size)
        assertEquals(listOf("IV_PERCENT", "IV_ATTACK", "IV_DEFENSE", "IV_STAMINA"), tab.blocks.single().fields)
    }
    @Test fun blockAssignmentAndRemovalPreserveFieldsAndIndependentOrder() {
        val assigned = assignReviewBlock(tab, "LEVEL", "ivs")
        assertEquals("LEVEL", assigned.blocks.single().fields.last())
        val removed = assignReviewBlock(assigned, "LEVEL", null)
        assertFalse("LEVEL" in removed.blocks.single().fields)
        assertTrue("LEVEL" in reviewTabItems(removed))
        assertEquals(tab.fields, removed.fields)
        assertEquals(tab, assignReviewBlock(tab, "LEVEL", "missing"))
    }
    @Test fun deletingBlockReleasesTagsAtTheBlockPosition() {
        val moved = tab.copy(order = listOf("block:ivs", "POKEMON_NAME"))
        val deleted = deleteReviewBlock(moved, "ivs")
        assertTrue(deleted.blocks.isEmpty())
        assertEquals(moved.blocks.single().fields, reviewTabItems(deleted).take(4))
        assertEquals(tab.fields.toSet(), reviewTabItems(deleted).toSet())
    }
    @Test fun movingTagToAnotherTabRemovesStaleMembership() {
        val tabs = listOf(tab) + defaultReviewScreenTabs.drop(1)
        val moved = moveReviewField(tabs, "IV_ATTACK", "extras")
        assertFalse("IV_ATTACK" in moved.first().blocks.single().fields)
        assertTrue("IV_ATTACK" in moved.last().fields)
        assertEquals(1, moved.sumOf { it.fields.count { f -> f == "IV_ATTACK" } })
    }
    @Test fun malformedMembershipIsRepairedWithoutLosingTags() {
        val malformed = tab.copy(blocks = listOf(
            ReviewLayoutBlock("a", width = 3, fields = listOf("LEVEL", "LEVEL", "missing")),
            ReviewLayoutBlock("b", fields = listOf("LEVEL", "CP"))))
        val result = normalizeReviewTabs(listOf(malformed)).first()
        assertEquals(listOf("LEVEL"), result.blocks[0].fields)
        assertEquals(listOf("CP"), result.blocks[1].fields)
        assertEquals(100, result.blocks[0].width)
        assertTrue("POKEMON_NAME" in result.fields)
    }
    @Test fun blocksOrdersAndWidthsSurviveBackup() {
        val configured = tab.copy(order = listOf("block:ivs", "LEVEL"),
            blocks = tab.blocks.map { it.copy(width = 75, fields = it.fields.reversed()) })
        saveReviewTabs(prefs, listOf(configured) + defaultReviewScreenTabs.drop(1))
        saveReviewHeight(prefs, "IV_ATTACK", 28)
        val backup = UserBackup.export(context)
        prefs.edit().clear().commit()
        UserBackup.restore(context, backup)
        val restored = ReviewLayout.read(prefs)
        assertEquals(configured, restored.tabs!!.first())
        assertEquals(28, restored.fieldHeight("IV_ATTACK"))
    }
}
