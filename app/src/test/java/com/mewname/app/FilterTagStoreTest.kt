package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FilterTagStoreTest {
    @Test fun editKeepsIdentityAndDeleteRemovesOnlyItsPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("mewname_prefs", 0)
        prefs.edit().clear().commit()
        val store = FilterTagStore(context)
        val a = store.save(null, "Troca", "#troca")
        val b = store.save(null, "Pikachu", "25")
        store.icon(a.id, "mission_icons/pokeball_sprite.png")
        prefs.edit().putStringSet("favorite_filter_tokens", setOf(a.id, b.id, "shiny")).commit()
        val edited = store.save(a.id, "Trocas", "#trocas")
        assertEquals(a.id, edited.id)
        assertEquals(2, FilterTagStore(context).tags().size)
        assertEquals("mission_icons/pokeball_sprite.png", FilterTagStore(context).icon(a.id))
        store.delete(a.id)
        assertEquals(listOf(b), store.tags())
        assertNull(store.icon(a.id))
        assertEquals(setOf(b.id, "shiny"), prefs.getStringSet("favorite_filter_tokens", emptySet()))
    }
    @Test fun reorderedCustomTagsPreserveTheirChosenOperators() {
        val groups = linkedMapOf("token:a" to listOf(FilterExpression("#troca")),
            "token:b" to listOf(FilterExpression("!shiny", ",")),
            "type:Fire" to listOf(FilterExpression("fire")))
        assertEquals("fire&#troca,!shiny", buildOrderedFilter(listOf("type:Fire", "token:a", "token:b"), groups))
    }
    @Test fun editSavedFilterPreservesIdentityOrderAndOtherEntries() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val key = "test_saved_filter_edit"
        context.getSharedPreferences("mewname_prefs", 0).edit()
            .putStringSet(key, setOf("a|shiny&!favorite", "b|25")).commit()
        val result = updateSavedFilter(context, key, "a", " !favorite,shiny ")
        assertEquals(2, result.size)
        assertEquals("!favorite,shiny", result.first { it.id == "a" }.value)
        assertEquals("25", result.first { it.id == "b" }.value)
        assertEquals(result, loadSavedFilters(context, key))
        assertEquals(result, updateSavedFilter(context, key, "a", " "))
    }
}
