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
        assertEquals(48, params.height)
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
        assertEquals(48, CollectionOverlayWindow.draggedHeight(1100, 900f, 4000f, 2000, 2f))
        assertEquals(2000, CollectionOverlayWindow.draggedHeight(1100, 900f, -4000f, 2000, 2f))
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

    @Test fun compactRowsKeepOriginalBeforeRegionalsAndFilterExactTargets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val species = CollectionDexCatalog.load(context)
        val tauros = species.single { it.dex == 128 }
        assertEquals("Tauros", tauros.name)
        val rows = collectionCompactTargets(species, "Tauros").map { it.second }
        assertEquals("Tauros", rows.first().label)
        assertTrue(rows.any { it.label.contains("Paldea", true) })
        val meowth = collectionCompactTargets(species, "Meowth").map { it.second }
        assertEquals("Meowth", meowth.first().label)
        assertTrue(meowth.any { it.label.contains("Alola", true) })
        assertTrue(meowth.any { it.label.contains("Galar", true) })
        val hisui = collectionCompactTargets(species, "Growlithe (Hisui)").map { it.second }
        assertEquals(1, hisui.size)
        assertEquals(hisui, collectionCompactTargets(species, "", setOf(hisui.single().key)).map { it.second })
        val all = collectionCompactTargets(species, "")
        assertEquals(all.size, all.map { it.second.key }.distinct().size)
        assertTrue(all.none { it.second.key in it.first.costumeKeys })
        assertEquals(all.map { it.first.dex }.sorted(), all.map { it.first.dex })
    }

    @Test fun regionalGameAliasesDoNotDuplicateCatalogAndPerrserkerHasSprite() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalog = CollectionDexCatalog.load(context)
        val rows = collectionCompactTargets(catalog, "")
        assertTrue(rows.none { it.second.key.contains("GALARIAN") })
        assertEquals(1, rows.count { it.first.dex == 52 && it.second.label == "Meowth (Galar)" })
        val perrserker = rows.single { it.first.dex == 863 }
        assertEquals("Perrserker", perrserker.second.label)
        assertTrue(PersonalCollectionImages(context).url(perrserker.second)?.contains("pm863.icon.png") == true)
    }
    @Test fun legacyGalarianFlagsMergeAndCanBeUnmarkedWithoutResurrecting() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PersonalCollection.preferences(context)
        prefs.edit().clear().commit()
        val old = PersonalCollectionTarget("[\"MEOWTH\",\"GAME_FORM:MEOWTH_GALARIAN\"]", "Meowth · Galarian")
        val canonical = PersonalCollectionTarget("[\"MEOWTH (GALAR)\",\"\"]", "Meowth (Galar)")
        fun encoded(target: PersonalCollectionTarget, group: String) = org.json.JSONObject()
            .put("key", target.key).put("label", target.label).put("groups", org.json.JSONArray(listOf(group))).toString()
        try {
            prefs.edit().putStringSet("entry_keys", setOf(old.key, canonical.key))
                .putString("entry:" + old.key, encoded(old, "SHINY"))
                .putString("entry:" + canonical.key, encoded(canonical, "LUCKY")).commit()
            val snapshot = PersonalCollection.read(context)
            assertEquals(setOf(canonical.key), snapshot.entries.keys)
            assertEquals(setOf(PersonalCollectionGroup.SHINY, PersonalCollectionGroup.LUCKY), snapshot.entries[canonical.key]?.groups)
            PersonalCollection.mark(context, canonical, PersonalCollectionGroup.SHINY, false)
            assertEquals(setOf(PersonalCollectionGroup.LUCKY), PersonalCollection.read(context).entries[canonical.key]?.groups)
            assertFalse(prefs.contains("entry:" + old.key))
        } finally { prefs.edit().clear().commit() }
    }
}
