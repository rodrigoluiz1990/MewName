package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PokedexEvolutionTest {
    private fun edges(): List<PokedexEvolution> = ApplicationProvider.getApplicationContext<Context>()
        .assets.open("pokemon/evolutions.json").bufferedReader().use { parsePokedexEvolutions(it.readText()) }

    @Test fun unorderedFamilyUsesActualEvolutionOrderAndCosts() {
        val paths = pokedexEvolutionPaths(listOf("Venusaur", "Bulbasaur", "Ivysaur"), edges())
        assertEquals(1, paths.size)
        assertEquals(listOf("Bulbasaur", "Ivysaur", "Venusaur", "Mega Venusaur"), paths.single().members)
        assertEquals(listOf(25, 100, null), paths.single().steps.map { it.candy })
    }
    @Test fun eeveeBranchesAreNotLinkedToEachOther() {
        val family = listOf("Eevee", "Vaporeon", "Jolteon", "Flareon", "Espeon", "Umbreon", "Leafeon", "Glaceon", "Sylveon")
        val paths = pokedexEvolutionPaths(family, edges())
        assertEquals(8, paths.size)
        assertTrue(paths.all { it.members.size == 2 && it.members.first() == "Eevee" })
        assertTrue(paths.all { it.steps.single().candy == 25 })
        assertTrue(paths.single { it.members.last() == "Sylveon" }.steps.single().requirements.has("questDisplay"))
    }
    @Test fun regionalFormsRemainSeparate() {
        val paths = pokedexEvolutionPaths(listOf("Rattata", "Rattata (Alola)", "Raticate", "Raticate (Alola)"), edges())
        assertEquals(setOf(listOf("Rattata", "Raticate"), listOf("Rattata (Alola)", "Raticate (Alola)")), paths.map { it.members }.toSet())
    }
    @Test fun isolatedSpeciesDoesNotInventACostOrArrow() {
        val path = pokedexEvolutionPaths(listOf("Mew"), edges()).single()
        assertEquals(listOf("Mew"), path.members)
        assertTrue(path.steps.isEmpty())
    }
    @Test fun catalogEdgesHaveNonNegativeCostsAndNoSelfEvolution() {
        val edges = edges()
        assertTrue(edges.isNotEmpty())
        assertTrue(edges.all { it.from != it.to && (it.candy == null || it.candy >= 0) })
        assertTrue(edges.any { it.tradeFree })
    }
    @Test fun finalStageIncludesAncestorsAndBothMegas() {
        val paths = pokedexEvolutionPaths(listOf("Charizard"), edges())
        assertEquals(setOf("Mega Charizard X", "Mega Charizard Y"), paths.map { it.members.last() }.toSet())
        assertTrue(paths.all { it.members.take(3) == listOf("Charmander", "Charmeleon", "Charizard") })
        assertTrue(paths.all { it.steps.last().megaEnergy == 200 && it.steps.last().candy == null })
    }
    @Test fun megaSelectionKeepsEntireFamilyAndSpritesResolve() {
        val paths = pokedexEvolutionPaths(listOf("Mega Venusaur"), edges())
        assertEquals(listOf("Bulbasaur", "Ivysaur", "Venusaur", "Mega Venusaur"), paths.single().members)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val images = PersonalCollectionImages(context)
        assertTrue(edges().filter { it.megaEnergy != null }.all { it.spriteFile != null && images.gameSprite(it.spriteFile) != null })
    }
    @Test fun gigantamaxIsAnAlternativeAlongsideBothMegasWithoutCosts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val images = PersonalCollectionImages(context)
        val forms = pokedexGigantamaxForms(context, images)
        assertTrue(forms.isNotEmpty())
        assertTrue(forms.all { it.gigantamax && it.candy == null && it.megaEnergy == null && images.gameSprite(it.spriteFile!!) != null })
        val paths = pokedexEvolutionPaths(listOf("Charmander"), edges() + forms)
        assertEquals(setOf("Mega Charizard X", "Mega Charizard Y", "Charizard Gigamax"), paths.map { it.members.last() }.toSet())
        assertTrue(paths.all { it.members.take(3) == listOf("Charmander", "Charmeleon", "Charizard") })
        assertEquals(listOf("Lapras", "Lapras Gigamax"), pokedexEvolutionPaths(listOf("Lapras"), edges() + forms).single().members)
        assertTrue(forms.none { it.from == "Meowth (Alola)" })
    }
    @Test fun candyPaletteIsSharedByWholeFamilyIncludingTemporaryForms() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val paths = pokedexEvolutionPaths(listOf("Ivysaur"), edges())
        val colors = pokedexCandyColors(context, paths)
        assertNotNull(colors["Bulbasaur"])
        assertEquals(colors["Bulbasaur"], colors["Ivysaur"])
        assertEquals(colors["Bulbasaur"], colors["Mega Venusaur"])
        val fireColors = pokedexCandyColors(context, pokedexEvolutionPaths(listOf("Charizard"), edges()))
        assertNotEquals(colors["Bulbasaur"], fireColors["Charizard"])
    }
}