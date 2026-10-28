package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Saved filters live in a string set; their drag-and-drop order is stored beside it. */
@RunWith(RobolectricTestRunner::class)
class SavedFilterOrderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val key = SAVED_POKEMON_FILTERS_KEY

    @Before fun clear() {
        context.getSharedPreferences("mewname_prefs", 0).edit().remove(key).remove(key + "_order").commit()
    }

    private fun values() = loadSavedFilters(context, key).map { it.value }

    @Test fun filtersWithoutStoredOrderKeepTheIdOrder() {
        val entries = listOf(SavedFilterEntry("a", "1"), SavedFilterEntry("c", "3"), SavedFilterEntry("b", "2"))
        assertEquals(listOf("c", "b", "a"), orderSavedFilters(entries, null).map { it.id })
    }

    @Test fun storedOrderWinsAndUnorderedEntriesComeFirst() {
        val entries = listOf(SavedFilterEntry("a", "1"), SavedFilterEntry("b", "2"), SavedFilterEntry("new", "3"))
        assertEquals(listOf("new", "b", "a"), orderSavedFilters(entries, "b\na").map { it.id })
    }

    @Test fun dropIndexFollowsTheDraggedCardCenter() {
        // Cards of 100 px with their middles at 50, 158 and 266; "b" is dragged.
        val middles = mapOf("a" to 50f, "b" to 158f, "c" to 266f)
        val order = listOf("a", "b", "c")
        assertEquals(1, savedFilterDropIndex(order, "b", 158f, middles::get))
        assertEquals(0, savedFilterDropIndex(order, "b", 40f, middles::get))
        assertEquals(2, savedFilterDropIndex(order, "b", 300f, middles::get))
        assertEquals(1, savedFilterDropIndex(order, "b", 260f, middles::get))
    }

    @Test fun newFilterGoesOnTopAndOrderSurvivesReload() {
        saveNewFilter(context, key, "shiny")
        saveNewFilter(context, key, "4*")
        saveNewFilter(context, key, "lendário")
        assertEquals(listOf("lendário", "4*", "shiny"), values())
        // The drag saves the list in its new order on release.
        persistSavedFilters(context, key, loadSavedFilters(context, key).let { listOf(it[2], it[0], it[1]) })
        assertEquals(listOf("shiny", "lendário", "4*"), values())
        saveNewFilter(context, key, "xxl")
        assertEquals(listOf("xxl", "shiny", "lendário", "4*"), values())
    }
}
