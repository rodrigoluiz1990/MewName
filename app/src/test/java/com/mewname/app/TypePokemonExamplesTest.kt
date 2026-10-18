package com.mewname.app

import com.mewname.app.domain.PokedexCatalogEntry
import org.junit.Assert.*
import org.junit.Test

class TypePokemonExamplesTest {
    private val grass = PokedexCatalogEntry(152, "Chikorita", type1 = "Grass")
    private val dual = PokedexCatalogEntry(1, "Bulbasaur", type1 = "Grass", type2 = "Poison")
    private val fire = PokedexCatalogEntry(4, "Charmander", type1 = "Fire")
    @Test fun singleTypeExcludesDualTypePokemon() {
        assertEquals(listOf(grass), exactTypeExamples(listOf(grass, dual, fire), setOf("Grass")))
    }
    @Test fun dualTypesRequireExactMatchRegardlessOfOrderOrCase() {
        assertEquals(listOf(dual), exactTypeExamples(listOf(grass, dual, fire), setOf("poison", "GRASS")))
        assertTrue(exactTypeExamples(listOf(grass, dual, fire), setOf("Grass", "Fire")).isEmpty())
    }
    @Test fun noSelectionHasNoExamplesAndListIsLimitedAndSorted() {
        assertTrue(exactTypeExamples(listOf(grass), emptySet()).isEmpty())
        val entries = (20 downTo 1).map { grass.copy(number = it, name = "Pokemon $it") }
        assertEquals((1..6).toList(), exactTypeExamples(entries + entries, setOf("Grass")).map { it.number })
    }
}
