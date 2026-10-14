package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MoveSearchFiltersTest {
    @Test fun legacyMeteorBeamResolvesDespiteDifferentTranslation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = GameCatalogRepository.loadLegacyMoveCatalog(context).single { it.pokemon == "GIGALITH" }
        val move = GameInfoRepository.loadMoveCatalog(context).single { it.moveId == 372 }
        val index = mapOf("METEOR BEAM" to move)
        assertEquals(move, legacyMoveDetails(entry, "Feixe Meteórico", index))
        assertEquals("Raio Meteórico", move.namePt)
        assertTrue(matchesMoveFilters(move, MoveCategory.CHARGED, "Rock"))
        assertFalse(matchesMoveFilters(move, MoveCategory.FAST, "Rock"))
        assertFalse(matchesMoveFilters(move, MoveCategory.CHARGED, "Water"))
    }
    @Test fun unknownMoveRemainsVisibleOnlyWithoutSpecificFilters() {
        assertTrue(matchesMoveFilters(null, null, null))
        assertFalse(matchesMoveFilters(null, MoveCategory.FAST, null))
        assertFalse(matchesMoveFilters(null, null, "Rock"))
    }
    @Test fun everyLegacyMoveHasCombatDetailsAndWellFormedTranslations() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val raw = org.json.JSONObject(context.assets.open("catalogs/game_catalogs.json").bufferedReader().use { it.readText() }).getJSONObject("legacyMoves")
        raw.keys().forEach { assertEquals(it, 0, raw.getJSONArray(it).length() % 3) }
        val index = GameInfoRepository.loadMoveCatalog(context).associateBy { it.name.uppercase(java.util.Locale.US) }
        GameCatalogRepository.loadLegacyMoveCatalog(context).forEach { entry ->
            entry.moves.forEach { name -> assertNotNull("${entry.pokemon}: $name", legacyMoveDetails(entry, name, index)) }
        }
    }
    @Test fun necrozmaFormsUsePortugueseAndKeepCanonicalIdentity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val effects = GameCatalogRepository.loadAdventureEffectCatalog(context)
        assertEquals("Necrozma (Juba Crepúsculo)", effects.single { it.pokemon == "Dusk Mane Necrozma" }.displayPokemonPt)
        assertEquals("Necrozma (Asas Alvorada)", effects.single { it.pokemon == "Dawn Wings Necrozma" }.displayPokemonPt)
    }
}