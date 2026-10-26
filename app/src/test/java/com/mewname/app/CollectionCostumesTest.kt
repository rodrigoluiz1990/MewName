package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import com.mewname.app.model.PokemonScreenData
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollectionCostumesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun costumesAreSeparatedInBothLayoutsWithoutLosingTargets() {
        val catalog = CollectionDexCatalog.load(context)
        val regular = collectionCompactTargets(catalog, "")
        val costumes = collectionCompactTargets(catalog, "", costumesOnly = true)
        val normalKeys = regular.map { it.second.key }.toSet()
        val costumeKeys = costumes.map { it.second.key }.toSet()
        assertTrue(normalKeys.intersect(costumeKeys).isEmpty())
        assertEquals(catalog.flatMap { it.targets }.map { it.key }.toSet(), normalKeys + costumeKeys)
        assertEquals(costumeKeys, collectionQuickTargets(catalog, PersonalCollectionGroup.NORMAL, "", costumesOnly = true).map { it.second.key }.toSet())
        for (code in listOf("PSYDUCK_SWIM_2025", "CHARMANDER_GOGGLES_2026", "CHARMELEON_GOGGLES_2026", "CHARIZARD_GOGGLES_2026", "PIKACHU_K_2026_A_01", "PIKACHU_GLASS_HELMET_2026")) {
            assertTrue(code, costumes.any { it.second.key.contains(code) })
        }
        assertEquals(costumes.map { it.first.dex }.sorted(), costumes.map { it.first.dex })
    }

    @Test fun armoredMewtwoKeepsIndependentStatsAndCollectionFlags() {
        val normal = requireNotNull(PersonalCollection.target(context, PokemonScreenData(pokemonName = "Mewtwo")))
        val armored = requireNotNull(PersonalCollection.target(context, PokemonScreenData(pokemonName = "Mewtwo (Armadura)")))
        val catalog = CollectionDexCatalog.load(context)
        val mewtwo = catalog.single { it.dex == 150 }
        assertTrue(armored.key in mewtwo.costumeKeys)
        assertFalse(normal.key in mewtwo.costumeKeys)
        val entries = GameInfoRepository.loadPokedexCatalog(context)
        assertEquals(300, entries.single { it.name == "Mewtwo" }.attack)
        assertEquals(182, entries.single { it.name == "Mewtwo (Armadura)" }.attack)
        val prefs = PersonalCollection.preferences(context)
        prefs.edit().clear().commit()
        try {
            PersonalCollection.mark(context, armored, PersonalCollectionGroup.NORMAL, true)
            val obtained = PersonalCollection.read(context).entries.keys
            assertFalse(normal.key in obtained)
            assertEquals(listOf(armored.key), collectionCompactTargets(catalog, "", obtained, costumesOnly = true).map { it.second.key })
            PersonalCollection.mark(context, armored, PersonalCollectionGroup.NORMAL, false)
            assertTrue(PersonalCollection.read(context).entries.isEmpty())
        } finally { prefs.edit().clear().commit() }
    }
}
