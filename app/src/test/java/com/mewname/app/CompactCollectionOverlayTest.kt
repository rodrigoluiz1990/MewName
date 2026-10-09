package com.mewname.app

import android.content.Context
import android.view.Gravity
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CompactCollectionOverlayTest {
    @Test fun panelDoesNotDimOrBlockOutsideTouchesAndShrinksTheActualWindow() {
        val params = CollectionOverlayWindow.params(2000, 2f)
        assertEquals(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, params.gravity)
        assertEquals(1100, params.height)
        assertEquals(0f, params.dimAmount)
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        assertNotEquals(0, params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        CollectionOverlayWindow.resize(params, 0, 2000, 2f)
        assertEquals(192, params.height)
        assertNotEquals(0, params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        CollectionOverlayWindow.resize(params, 1437, 2000, 2f)
        assertEquals(1437, params.height)
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        assertNotEquals(0, params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    @Test fun dragTracksScreenCoordinatesWithoutSnappingAndClampsAtEdges() {
        assertEquals(1237, CollectionOverlayWindow.draggedHeight(1100, 900f, 763f, 2000, 2f))
        assertEquals(963, CollectionOverlayWindow.draggedHeight(1100, 900f, 1037f, 2000, 2f))
        assertEquals(192, CollectionOverlayWindow.draggedHeight(1100, 900f, 4000f, 2000, 2f))
        assertEquals(1880, CollectionOverlayWindow.draggedHeight(1100, 900f, -4000f, 2000, 2f))
    }

    @Test fun quickMarkingKeepsRegionalFormsSeparateAndFiltersByTheSelectedGroup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PersonalCollection.preferences(context)
        prefs.edit().clear().commit()
        val normal = PersonalCollectionTarget("[\"GROWLITHE\",\"\"]", "Growlithe")
        val hisui = PersonalCollectionTarget("[\"GROWLITHE (HISUI)\",\"\"]", "Growlithe (Hisui)")
        val costume = PersonalCollectionTarget("[\"GROWLITHE\",\"GAME_COSTUME:test\"]", "Growlithe traje")
        val species = listOf(CollectionSpecies(58, "Growlithe", listOf(normal, hisui, costume), setOf(costume.key)))
        try {
            assertEquals(listOf(normal,hisui), collectionQuickTargets(species,PersonalCollectionGroup.SHINY,"").map { it.second })
            assertEquals(listOf(hisui), collectionQuickTargets(species,PersonalCollectionGroup.SHINY,"Hisui").map { it.second })
            PersonalCollection.mark(context,hisui,PersonalCollectionGroup.SHINY,true)
            PersonalCollection.mark(context,normal,PersonalCollectionGroup.LUCKY,true)
            val shinyKeys = PersonalCollection.read(context).entries.filterValues { PersonalCollectionGroup.SHINY in it.groups }.keys
            assertEquals(listOf(hisui), collectionQuickTargets(species,PersonalCollectionGroup.SHINY,"",shinyKeys).map { it.second })
            PersonalCollection.mark(context,hisui,PersonalCollectionGroup.SHINY,false)
            assertFalse(PersonalCollection.read(context).entries.containsKey(hisui.key))
            assertEquals(setOf(PersonalCollectionGroup.LUCKY), PersonalCollection.read(context).entries[normal.key]?.groups)
            assertTrue(collectionQuickTargets(species,PersonalCollectionGroup.MEGA,"").isEmpty())
        } finally { prefs.edit().clear().commit() }
    }
}
