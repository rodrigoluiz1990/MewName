package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PokedexIdentityRegressionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun target(name: String) = PersonalCollectionTarget(JSONArray(listOf(name.uppercase(), "")).toString(), name)

    @Test fun spritesRespectExactFormsAndShinies() {
        val images = PersonalCollectionImages(context)
        val expected = mapOf("Zacian Crowned Sword" to "pm888.fCROWNED_SWORD",
            "Zacian Hero" to "pm888.fHERO", "Zacian (Coroado)" to "pm888.fCROWNED_SWORD",
            "Raichu (Alola)" to "pm26.fALOLA", "Meowth (Galar)" to "pm52.fGALARIAN",
            "Kyurem Black" to "pm646.fBLACK", "Kyurem White" to "pm646.fWHITE")
        expected.forEach { (name, file) ->
            assertTrue(name, images.url(target(name))!!.endsWith("$file.icon.png"))
            assertTrue(name, images.url(target(name), true)!!.endsWith("$file.s.icon.png"))
        }
        assertTrue(images.url(target("Raichu"))!!.endsWith("pm26.icon.png"))
        assertNull(images.url(target("Zacian (Unknown)")))
    }

    @Test fun translatedSpeciesNamesRetainRegionalAndBattleForms() {
        assertEquals("Raichu (Alola)", PokedexCatalogEntry(26, "Raichu (Alola)", namePt = "Raichu").localizedName(AppLanguage.PT_BR))
        assertEquals("Zacian (Coroado)", PokedexCatalogEntry(888, "Zacian Crowned Sword", namePt = "Zacian").localizedName(AppLanguage.PT_BR))
        assertEquals("Kyurem Black", PokedexCatalogEntry(646, "Kyurem Black", namePt = "Kyurem").localizedName(AppLanguage.PT_BR))
    }

    @Test fun catalogKeepsExplicitFormsAndRemovesOnlyGenericDuplicates() = runBlocking {
        val catalog = GameInfoRepository.loadPokedexCatalog(context)
        assertFalse(catalog.any { isRedundantPokedexSpecies(it.name) })
        assertEquals(3, catalog.count { it.number == 745 })
        assertEquals(2, catalog.count { it.number == 888 })
        assertEquals(2, catalog.count { it.number == 26 })
        GameInfoRepository.loadPokedexCatalogProgressive(context) { batch ->
            assertFalse(batch.any { isRedundantPokedexSpecies(it.name) })
        }
        Unit
    }

    @Test fun nationalGridHasExactlyOneEntryForEverySpecies() {
        val entries = GameInfoRepository.loadPokedexCatalog(context)
        val national = nationalPokedexEntries(entries)
        assertEquals((1..1025).toList(), national.map { it.number })
        assertEquals("Zacian Hero", national.single { it.number == 888 }.name)
        assertEquals("Raichu", national.single { it.number == 26 }.name)
        assertEquals("Zacian", nationalPokedexName(national.single { it.number == 888 }, AppLanguage.PT_BR))
    }

    @Test fun specialFormsRemainAccessibleThroughFamily() {
        val repository = PokedexDetailsRepository(context)
        val entries = GameInfoRepository.loadPokedexCatalog(context)
        for ((name, forms) in mapOf(
            "Zacian Hero" to setOf("Zacian Hero", "Zacian Crowned Sword"),
            "Kyurem" to setOf("Kyurem", "Kyurem Black", "Kyurem White"),
            "Necrozma" to setOf("Necrozma", "Necrozma (Juba do Crepúsculo)", "Necrozma (Asas da Alvorada)"))) {
            val details = repository.local(entries.single { it.name == name }, AppLanguage.PT_BR)
            assertTrue(name, details.evolutionPaths.flatMap { it.members }.containsAll(forms))
        }
    }

    @Test fun dipplinLocalDetailsLoadWithStatsSizesAndFamily() {
        val entry = GameInfoRepository.loadPokedexCatalog(context).single { it.name == "Dipplin" }
        val details = PokedexDetailsRepository(context).local(entry, AppLanguage.PT_BR)
        assertEquals(listOf(173, 184, 190), details.stats)
        assertEquals(0.4, details.info!!.getDouble("pokedexHeightM"), 0.0001)
        assertNotNull(details.cp[50]!!.second)
        assertNotNull(details.sprite)
        assertTrue(details.evolutionPaths.flatMap { it.members }.contains("Hydrapple"))
    }

    @Test fun allExplicitSpriteBindingsExistInIndex() {
        val mappings = JSONObject(context.assets.open("pokemon/pokedex_sprites.json").bufferedReader().use { it.readText() })
        val images = PersonalCollectionImages(context)
        mappings.keys().forEach { assertNotNull(it, images.gameSprite(mappings.getString(it))) }
    }

    @Test fun alolanRaichuRemainsSeparateWithoutInventedEvolution() {
        val details = PokedexDetailsRepository(context).local(PokedexCatalogEntry(25, "Pikachu"), AppLanguage.PT_BR)
        assertTrue(details.familySprites["Raichu (Alola)"]!!.endsWith("pm26.fALOLA.icon.png"))
        assertTrue(details.evolutionPaths.any { it.members == listOf("Raichu (Alola)") && it.steps.isEmpty() })
        assertTrue(details.evolutionPaths.any { it.members.take(3) == listOf("Pichu", "Pikachu", "Raichu") })
    }
}
