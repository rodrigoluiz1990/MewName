package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import com.mewname.app.model.PvpLeague
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NidoranCatalogTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `canonical symbols and old aliases preserve families rankings and distinct moves`() {
        val names = JSONArray(context.assets.open("pokemon/names.json").bufferedReader().use { it.readText() })
        val calculator = PvpRankCalculator()
        for ((symbol, alias, dex) in listOf(Triple("♀", "NidoranF", 29), Triple("♂", "NidoranM", 32))) {
            val name = "Nidoran$symbol"
            val entries = (0 until names.length()).map { names.getJSONObject(it) }.filter { it.optInt("dex") == dex }
            assertEquals(listOf(name), entries.map { it.getString("name") })
            assertEquals(name, calculator.canonicalName(context, alias))
            val family = PokemonFamilySuggester().suggestionsFor(context, null, name)
            assertEquals(3, family.size)
            assertEquals(family, PokemonFamilySuggester().suggestionsFor(context, null, alias))
            val rank = calculator.calculateRank(context, name, 15, 14, 15, PvpLeague.GREAT)
            assertNotNull(rank)
            assertEquals(rank, calculator.calculateRank(context, alias, 15, 14, 15, PvpLeague.GREAT))
            val moves = PokemonMoveRepository.load(context, name, AppLanguage.PT_BR)
            assertEquals(moves, PokemonMoveRepository.load(context, alias, AppLanguage.PT_BR))
            assertTrue(moves.fastMoves.any { it.name == if (symbol == "♀") "Bite" else "Peck" })
            assertFalse(moves.fastMoves.any { it.name == if (symbol == "♀") "Peck" else "Bite" })
            assertTrue(moves.chargedMoves.any { it.name == if (symbol == "♀") "Poison Fang" else "Horn Attack" })
        }
    }
}