package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PokedexInfoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun bulbasaurMetadataAndContinuousBounds() {
        val info = PokedexInfoCatalog.load(context, PokedexCatalogEntry(1, "Bulbasaur"))!!
        assertEquals(0.7, info.getDouble("pokedexHeightM"), 0.00001)
        assertEquals(6.9, info.getDouble("pokedexWeightKg"), 0.00001)
        assertEquals(25, info.getJSONObject("thirdMove").getInt("candyToUnlock"))
        val bounds = pokemonSizeBounds(info)
        assertEquals(5, bounds.size)
        assertEquals(0.343, bounds.first().minimum, 0.00001)
        assertEquals(1.225, bounds.last().maximum, 0.00001)
        assertEquals(0.525, bounds[1].maximum, 0.00001)
        bounds.zipWithNext().forEach { assertEquals(it.first.maximum, it.second.minimum, 0.0) }
    }
    @Test fun unknownFormsDoNotInheritOrdinaryMeasurements() {
        assertNull(PokedexInfoCatalog.load(context, PokedexCatalogEntry(1, "Bulbasaur (Unknown)")))
        val normal = PokedexInfoCatalog.load(context, PokedexCatalogEntry(58, "Growlithe"))!!
        val hisui = PokedexInfoCatalog.load(context, PokedexCatalogEntry(58, "Growlithe (Hisui)"))!!
        assertNotEquals(normal.getDouble("pokedexWeightKg"), hisui.getDouble("pokedexWeightKg"))
    }
    @Test fun missingOrInvalidSizeBoundsAreNotFabricated() {
        assertTrue(pokemonSizeBounds(null).isEmpty())
        assertTrue(pokemonSizeBounds(JSONObject("""{"sizes":{"xxsLowerBound":0.3}}""")).isEmpty())
    }
    @Test fun snapshotHasValidBoundsAndNoInventedReleaseFlags() {
        val entries = JSONObject(context.assets.open("pokemon/pokedex_info.json").bufferedReader().use { it.readText() }).getJSONObject("entries")
        entries.keys().forEach { key ->
            val info = entries.getJSONObject(key)
            if (info.has("sizes")) assertEquals(key, 5, pokemonSizeBounds(info).size)
            assertFalse(info.has("released"))
            assertFalse(info.has("shinyReleased"))
        }
    }
    @Test fun confirmedKantoReleaseDoesNotLeakToRegionalForms() {
        for (name in listOf("Bulbasaur", "Ditto", "Mewtwo", "Mew")) {
            val entry = GameInfoRepository.loadPokedexCatalog(context).first { it.name == name }
            val info = PokedexInfoCatalog.load(context, entry)!!
            assertTrue(name, info.getBoolean("released"))
            assertTrue(name, info.getBoolean("shinyReleased"))
        }
        val regional = PokedexInfoCatalog.load(context, PokedexCatalogEntry(58, "Growlithe (Hisui)"))!!
        assertFalse(regional.has("released"))
        assertFalse(regional.has("shinyReleased"))
    }

    @Test fun explicitMaximumIsNotAUniversalMultiplier() {
        val bulbasaur = PokedexInfoCatalog.load(context, PokedexCatalogEntry(1, "Bulbasaur"))!!
        val wailord = PokedexInfoCatalog.load(context, PokedexCatalogEntry(321, "Wailord"))!!
        assertEquals(1.225, pokemonHeightSummary(bulbasaur).maximum!!, 0.000001)
        assertEquals(29.0, pokemonHeightSummary(wailord).maximum!!, 0.000001)
        assertEquals(listOf(0.35, 0.525, 0.875, 1.05, 1.225), pokemonSizeBounds(bulbasaur).map { it.maximum })
    }

    @Test fun standardHeightRemainsAvailableWithoutSizeBounds() {
        val summary = pokemonHeightSummary(JSONObject("""{"pokedexHeightM":0.4}"""))
        assertEquals(0.4, summary.standard!!, 0.0)
        assertNull(summary.minimum)
        assertNull(summary.maximum)
        assertEquals(PokemonHeightSummary(null, null, null), pokemonHeightSummary(null))
    }

    @Test fun invalidBoundsAndVisualScaleCannotProduceAMaximum() {
        val info = JSONObject("""{"pokedexHeightM":-1,"modelScaleV2":100,
            "sizes":{"xxsLowerBound":1,"xsLowerBound":0.5,"mLowerBound":0.75,"mUpperBound":1.25,"xlUpperBound":1.5,"xxlUpperBound":2}}""")
        assertEquals(PokemonHeightSummary(null, null, null), pokemonHeightSummary(info))
    }

}
