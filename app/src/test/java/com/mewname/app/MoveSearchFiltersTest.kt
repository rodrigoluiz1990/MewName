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
    @Test fun mewtwoEffectsKeepPlusMovesDistinctFromRegularAttacks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val effects = GameCatalogRepository.loadAdventureEffectCatalog(context)
        val x = effects.single { it.pokemon == "Mega Mewtwo X" }
        val y = effects.single { it.pokemon == "Mega Mewtwo Y" }
        assertEquals("Dynamic Punch+", x.move)
        assertEquals("Future Sight+", y.move)
        assertEquals("Fighting", x.moveType)
        assertEquals("Psychic", y.moveType)
        val index = GameInfoRepository.loadMoveCatalog(context).associateBy { it.name.uppercase(java.util.Locale.US) }
        assertNull(adventureMoveDetails(x.copy(combatDetails = null), index))
        assertNull(adventureMoveDetails(y.copy(combatDetails = null), index))
        val xMove = requireNotNull(adventureMoveDetails(x, index))
        val yMove = requireNotNull(adventureMoveDetails(y, index))
        assertEquals("Dynamic Punch+", xMove.name)
        assertEquals("Future Sight+", yMove.name)
        assertEquals(130, xMove.power)
        assertEquals(140, yMove.power)
        listOf(xMove, yMove).forEach {
            assertEquals(-100, it.energyDelta)
            assertEquals(2500, it.duration)
            assertEquals(130, it.pvpPower)
            assertEquals(-80, it.pvpEnergyDelta)
            assertTrue(matchesMoveFilters(it, MoveCategory.CHARGED, it.type))
        }
        effects.forEach {
            assertNotNull(it.move, adventureMoveDetails(it, index))
        }
        listOf(x, y).forEach {
            assertTrue(it.descriptionEn.isNotBlank())
            assertTrue(it.descriptionEs.isNotBlank())
            assertTrue(it.normalizedMoveAliases.all { alias -> alias.endsWith("+") })
        }
    }
    @Test fun nationalDexResolvesAllLegacyAndAdventurePokemon() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val numbers = GameInfoRepository.loadPokedexCatalog(context).flatMap { entry ->
            (listOfNotNull(entry.name, entry.namePt, entry.nameEs) + entry.aliases).map { normalizeMoveLookup(it) to entry.number }
        }.toMap()
        val names = GameCatalogRepository.loadLegacyMoveCatalog(context).map { it.pokemon } +
            GameCatalogRepository.loadAdventureEffectCatalog(context).map { it.pokemon }
        names.forEach { assertNotNull(it, nationalDexNumber(it, numbers)) }
    }
}