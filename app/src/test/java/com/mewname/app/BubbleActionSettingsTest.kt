package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BubbleActionSettingsTest {
    @Test fun reorderSurvivesDisablingAndRestoringAnOption() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("bubble_actions", 0)
        prefs.edit().clear().commit()
        try {
            BubbleActionSettings.saveOrder(context, listOf("app_collections", "friends", "capture", "friends", "obsolete"))
            BubbleActionSettings.save(context, setOf("app_collections", "capture", "friends"))
            assertEquals(listOf("app_collections", "friends", "capture"), BubbleActionSettings.selectedInOrder(context))
            BubbleActionSettings.save(context, setOf("capture", "app_collections"))
            assertEquals(listOf("app_collections", "capture"), BubbleActionSettings.selectedInOrder(context))
            BubbleActionSettings.save(context, setOf("capture", "friends", "app_collections"))
            assertEquals(listOf("app_collections", "friends", "capture"), BubbleActionSettings.selectedInOrder(context))
            assertEquals(BubbleActionSettings.keys.toSet(), BubbleActionSettings.order(context).toSet())
            assertEquals(BubbleActionSettings.keys.size, BubbleActionSettings.order(context).size)
        } finally { prefs.edit().clear().commit() }
    }
    @Test fun oldPreferencesAndMalformedOrderKeepEveryShortcut() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("bubble_actions", 0)
        prefs.edit().clear().putStringSet("selected", setOf("app_collections", "capture")).putString("order", "broken").commit()
        try {
            assertEquals(BubbleActionSettings.keys, BubbleActionSettings.order(context))
            assertEquals(listOf("capture", "app_collections"), BubbleActionSettings.selectedInOrder(context))
        } finally { prefs.edit().clear().commit() }
    }
    @Test fun choicesPersistAndCannotBecomeAnEmptyMenu() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("bubble_actions", 0)
        prefs.edit().clear().commit()
        try {
            assertEquals(4, BubbleActionSettings.selected(context).size)
            BubbleActionSettings.save(context, setOf("capture", "friends"))
            assertEquals(setOf("capture", "friends"), BubbleActionSettings.selected(context))
            BubbleActionSettings.save(context, emptySet())
            assertEquals(setOf("capture", "friends"), BubbleActionSettings.selected(context))
        } finally { prefs.edit().clear().commit() }
    }
}