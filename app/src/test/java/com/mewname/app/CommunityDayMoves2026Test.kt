package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommunityDayMoves2026Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun cleanup() {
        PokemonMoveRepository.clearCache()
        GameCatalogRepository.clearCache()
    }

    @Test
    fun communityAttacksAreSelectableAndListedAsLegacyInEveryLanguage() {
        val cases = listOf(
            Triple("Rillaboom", "Frenzy Plant", false),
            Triple("Ninetales", "Energy Ball", false),
            Triple("Ninetales (Alola)", "Chilling Water", false),
            Triple("Cinderace", "Blast Burn", false),
            Triple("Tinkaton", "Gigaton Hammer", false),
            Triple("Oinkologne", "Mud Slap", true),
            Triple("Baxcalibur", "Glaive Rush", false),
            Triple("Inteleon", "Hydro Cannon", false),
            Triple("Thievul", "Icy Wind", false),
            Triple("Zoroark", "Sucker Punch", true)
        )
        val legacyCatalog = GameCatalogRepository.loadLegacyMoveCatalog(context)
        for ((species, attack, fast) in cases) {
            val catalogEntry = legacyCatalog.single { it.pokemon.equals(species, ignoreCase = true) }
            for (language in AppLanguage.values()) {
                val moves = PokemonMoveRepository.load(context, species, language)
                val selected = (if (fast) moves.fastMoves else moves.chargedMoves).single { it.name == attack }
                assertTrue("$species / $attack", selected.legacy)
                assertFalse((if (fast) moves.chargedMoves else moves.fastMoves).any { it.name == attack })
                assertTrue(moveDropdownOptions(listOf(selected), "[L]").single().label.endsWith(" [L]"))
                if (language == AppLanguage.PT_BR) {
                    assertNotEquals(attack, selected.localizedName)
                    assertTrue(catalogEntry.moves.contains(selected.localizedName))
                }
            }
        }
    }

    @Test
    fun regionalFormsKeepTheirOwnCommunityAttacksAcrossCacheOrders() {
        for (order in listOf(
            listOf("Ninetales", "Alolan Ninetales", "Zoroark", "Hisuian Zoroark"),
            listOf("Hisuian Zoroark", "Zoroark", "Alolan Ninetales", "Ninetales")
        )) {
            PokemonMoveRepository.clearCache()
            order.forEach { PokemonMoveRepository.load(context, it, AppLanguage.EN) }
            val kanto = PokemonMoveRepository.load(context, "Ninetales", AppLanguage.EN)
            val alola = PokemonMoveRepository.load(context, "Ninetales (Alola)", AppLanguage.EN)
            assertFalse(kanto.chargedMoves.any { it.name == "Chilling Water" })
            assertFalse(alola.chargedMoves.any { it.name == "Energy Ball" })
            assertFalse(alola.fastMoves.any { it.name == "Ember" })
            val hisui = PokemonMoveRepository.load(context, "Zoroark (Hisui)", AppLanguage.EN)
            assertFalse(hisui.fastMoves.any { it.name == "Sucker Punch" })
            assertTrue(hisui.chargedMoves.any { it.name == "Shadow Ball" })
        }
    }

    @Test
    fun newMoveStatsAndTranslationsSurviveEnrichment() {
        val catalog = GameInfoRepository.loadMoveCatalog(context).associateBy { it.name }
        for ((species, attack, translated) in listOf(
            Triple("Tinkaton", "Gigaton Hammer", "Martelo Gigatônico"),
            Triple("Baxcalibur", "Glaive Rush", "Fúria de Glaive"),
            Triple("Ninetales (Alola)", "Chilling Water", "Água Gelada")
        )) {
            val stats = catalog.getValue(attack)
            assertTrue(stats.power!! > 0)
            val raw = PokemonMoveRepository.load(context, species, AppLanguage.PT_BR)
            val enriched = PokemonMoveRepository.enrich(context, raw, AppLanguage.PT_BR)
            assertEquals(translated, enriched.chargedMoves.single { it.name == attack }.localizedName)
        }
        assertEquals(130, catalog.getValue("Gigaton Hammer").pvpPower)
        assertEquals(90, catalog.getValue("Glaive Rush").pvpPower)
    }
}
