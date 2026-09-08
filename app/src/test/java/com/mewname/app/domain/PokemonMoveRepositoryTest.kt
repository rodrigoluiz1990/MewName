package com.mewname.app.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PokemonMoveRepositoryTest {
    @Test
    fun `loads all current Zacian moves from bundled catalog`() {
        val moves = PokemonMoveRepository.load(
            ApplicationProvider.getApplicationContext(),
            "Zacian",
            AppLanguage.EN
        )

        assertEquals(4, moves.fastMoves.size)
        assertEquals(4, moves.chargedMoves.size)
        val enriched = PokemonMoveRepository.enrich(ApplicationProvider.getApplicationContext(), moves, AppLanguage.EN)
        assertTrue(enriched.fastMoves.all { it.rating != null })
        assertTrue(enriched.chargedMoves.all { it.rating != null })
    }

    @Test
    fun `uses base species moves for a regional form without a dedicated entry`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val baseMoves = PokemonMoveRepository.load(context, "Rapidash", AppLanguage.EN)
        val regionalMoves = PokemonMoveRepository.load(context, "Galarian Rapidash", AppLanguage.EN)

        assertFalse(baseMoves.fastMoves.isEmpty())
        assertEquals(baseMoves, regionalMoves)
    }

    @Test
    fun `returns an empty move set for an unknown Pokemon`() {
        val moves = PokemonMoveRepository.load(
            ApplicationProvider.getApplicationContext(),
            "Pokemon inexistente",
            AppLanguage.PT_BR
        )

        assertTrue(moves.fastMoves.isEmpty())
        assertTrue(moves.chargedMoves.isEmpty())
    }

    @Test
    fun `propagates malformed current moves data`() {
        val dataSource = FakeMoveDataSource(currentMoves = "{")

        assertThrows(JSONException::class.java) {
            PokemonMoveRepository.load("Zacian", AppLanguage.EN, dataSource)
        }
    }

    @Test
    fun `uses the requested language when caching a catalog`() {
        val dataSource = FakeMoveDataSource(
            currentMoves = """[{"name":"Zacian","fastMoves":[{"name":"Quick Attack"}],"chargedMoves":[]}]""",
            catalog = listOf(
                MoveCatalogEntry(
                    name = "Quick Attack",
                    namePt = "Ataque Rapido",
                    type = "Normal",
                    category = MoveCategory.FAST
                )
            )
        )

        val english = PokemonMoveRepository.enrich(PokemonMoveRepository.load("Zacian", AppLanguage.EN, dataSource), AppLanguage.EN, dataSource)
        val portuguese = PokemonMoveRepository.enrich(PokemonMoveRepository.load("Zacian", AppLanguage.PT_BR, dataSource), AppLanguage.PT_BR, dataSource)

        assertEquals("Quick Attack", english.fastMoves.single().localizedName)
        assertEquals("Ataque Rapido", portuguese.fastMoves.single().localizedName)
    }

    @Test
    fun `reloads moves after catalog caches are invalidated`() {
        val dataSource = FakeMoveDataSource(
            currentMoves = """[{"name":"Zacian","fastMoves":[],"chargedMoves":[]}]"""
        )
        PokemonMoveRepository.clearCache()

        PokemonMoveRepository.load("Zacian", AppLanguage.EN, dataSource)
        PokemonMoveRepository.load("Zacian", AppLanguage.EN, dataSource)
        assertEquals(1, dataSource.readCount)

        CatalogCache.invalidateAssets()
        PokemonMoveRepository.load("Zacian", AppLanguage.EN, dataSource)
        assertEquals(2, dataSource.readCount)
    }
    @Test
    fun `translated options are available without consulting the full statistics catalog`() {
        val dataSource = object : PokemonMoveDataSource {
            override fun loadMoveCatalog(): List<MoveCatalogEntry> =
                error("The slow statistics catalog must not block opening the selector")
            override fun readCurrentMoves() = """[{"name":"Zacian","fastMoves":[{"name":"Metal Claw"}],"chargedMoves":[{"name":"Behemoth Blade","legacy":true}]}]"""
            override fun loadMoveTranslations(language: AppLanguage) = mapOf(
                "Metal Claw" to "Garra de Metal",
                "Behemoth Blade" to "Espada Colossal"
            )
        }
        val moves = PokemonMoveRepository.load("Zacian", AppLanguage.PT_BR, dataSource)
        assertEquals("Garra de Metal", moves.fastMoves.single().localizedName)
        assertEquals("Espada Colossal", moves.chargedMoves.single().localizedName)
        assertTrue(moves.chargedMoves.single().legacy)
    }

    @Test
    fun `compact translations match source tables for all supported languages`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val labels = org.json.JSONObject(context.assets.open("catalogs/move_labels.json").bufferedReader().use { it.readText() })
        for (language in AppLanguage.entries) {
            for (entry in GameInfoRepository.loadMoveCatalog(context).sortedBy { it.moveId }.distinctBy { it.name }) {
                assertEquals(entry.name, entry.localizedName(language), labels.getJSONObject(entry.name).getString(language.name))
            }
        }
    }
    private class FakeMoveDataSource(
        private val currentMoves: String,
        private val catalog: List<MoveCatalogEntry> = emptyList()
    ) : PokemonMoveDataSource {
        override fun loadMoveCatalog(): List<MoveCatalogEntry> = catalog

        var readCount = 0
            private set

        override fun readCurrentMoves(): String {
            readCount += 1
            return currentMoves
        }
    }
}