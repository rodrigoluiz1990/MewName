package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CatalogScreenMatcherTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun catalog(section: LeekSection) = LeekDuckParser.parse(section,
        javaClass.getResourceAsStream("/leekduck/${section.path}.html")!!.bufferedReader().use { it.readText() }, 0)

    @Test fun eggInventoryKeepsOriginsAndIgnoresIncubatorWalkingProgress() {
        val text = "TAGS\nPOKÉMON\nOVOS\n9 / 12\n3,4 / 5 km\n0 / 7 km\n0 / 10 km\n0 / 1 km\nEstoque bônus"
        val capture = CatalogScreenMatcher.detect(text)!!
        assertEquals(LeekSection.EGGS, capture.section)
        assertEquals(setOf(5, 7, 10), CatalogScreenMatcher.distances(text))
        val matches = CatalogScreenMatcher.matching(capture, catalog(LeekSection.EGGS))
        assertTrue(matches.isNotEmpty())
        assertTrue(matches.count { it.title.startsWith("7 km") } >= 2)
        assertFalse(matches.any { it.title.startsWith("2 km") || it.title.startsWith("12 km") })
        assertNull(CatalogScreenMatcher.detect("Wooper\n88 / 88 PS\n0,4m\n12 / 5 / 10"))
        assertNull(CatalogScreenMatcher.detect("TAGS\nPOKÉMON\nOVOS\n9 / 12\nPC 294"))
    }

    @Test fun rocketMatchesWholeLocalizedQuoteAndPreservesSharedLineups() {
        val quotes = AppLanguage.entries.flatMap { GameTextRepository.rocketQuotes(context, it) }
        val entries = catalog(LeekSection.ROCKET)
        for (language in AppLanguage.entries) {
            val local = GameTextRepository.rocketQuotes(context, language)
            for (entry in entries.entries) {
                val phrase = translatedRocketQuote(entry.description, entry.title, local)!!
                val capture = CatalogScreenMatcher.detect("GO Rocket\n$phrase\nBatalhar", quotes)
                assertNotNull("$language $phrase", capture)
                assertTrue(CatalogScreenMatcher.matching(capture!!, entries, quotes).any { it.id == entry.id })
            }
        }
        assertNull(CatalogScreenMatcher.detect("Normal\nAtaque\nDefesa", quotes))
    }

    @Test fun researchUsesExactTaskAndQuantityAndIncludesItemsAndEncounters() {
        val data = catalog(LeekSection.RESEARCH)
        assertTrue(data.entries.size > 30)
        val templates = AppLanguage.entries.flatMap { GameTextRepository.researchTemplates(context, it) }
        val capture = CatalogScreenMatcher.detect("PESQUISA\nHOJE\nFortalecer seus Pokémon 5 vezes.\n0\nChocar 1 Ovo.\nEVENTO")!!
        val matches = CatalogScreenMatcher.matching(capture, data, templates = templates, now = 0)
        assertTrue(matches.any { it.title == "Fortalecer seus Pokémon 5 vezes." })
        assertTrue(matches.any { it.title == "Chocar 1 Ovo." && it.description == "Event" })
        assertTrue(matches.any { it.title == "Chocar 1 Ovo." && it.description == "Regular" })
        assertTrue(matches.flatMap { it.pokemon }.any { it.name.contains("Energy") })
        assertFalse(matches.any { it.title.contains("10 vezes") || it.title.contains("3 vezes") })
        val expired = CatalogScreenMatcher.matching(capture, data, templates = templates, now = Long.MAX_VALUE)
        assertFalse(expired.any { it.expires != null })
    }

    @Test fun extendedTaskDoesNotMatchSimplerTaskAndNumbersStayExact() {
        val entry = LeekEntry("1", "Catch 5 Pokémon", "Catching", pokemon = listOf(LeekPokemon("Pikachu", "")))
        val data = LeekCatalog(listOf(entry), "", 0)
        val templates = listOf(ResearchTemplate("Catch {0} Pokémon", "Capturar {0} Pokémon."))
        fun match(task: String) = CatalogScreenMatcher.matching(CatalogCapture(LeekSection.RESEARCH, task), data, templates = templates)
        assertEquals(1, match("Capturar 5 Pokémon.").size)
        assertTrue(match("Capturar 15 Pokémon.").isEmpty())
        assertTrue(match("Capturar 5 Pokémon com clima favorável.").isEmpty())
        assertEquals(1, match("Capturar 5\nPokémon.").size)
    }
}