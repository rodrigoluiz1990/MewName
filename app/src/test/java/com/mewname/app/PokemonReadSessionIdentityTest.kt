package com.mewname.app

import com.mewname.app.domain.PokemonReadSessionMerger
import com.mewname.app.model.LevelDebugInfo
import com.mewname.app.model.PokemonScreenData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PokemonReadSessionIdentityTest {
    private val merger = PokemonReadSessionMerger()

    @Test fun maxHpSurvivesMerging() {
        val first = PokemonScreenData(pokemonName = "Regidrago", candyFamilyName = "Regidrago", cp = 1574,
            levelDebugInfo = LevelDebugInfo(maxHp = 190, hpLevel = 20.0))
        val second = first.copy(levelDebugInfo = LevelDebugInfo(ocrLevel = 20.0))
        val merged = merger.mergeIfSamePokemon(second, first)
        assertEquals(190, merged.levelDebugInfo?.maxHp)
        assertEquals(20.0, merged.levelDebugInfo?.hpLevel)
    }

    @Test fun nidoranFemaleAndMaleAreDifferentPokemon() {
        // With one CP missing, the same normalised name used to merge them and carry flags across.
        val female = PokemonScreenData(pokemonName = "Nidoran♀", candyFamilyName = "Nidoran♀", cp = 512, isShiny = true)
        val male = PokemonScreenData(pokemonName = "Nidoran♂", candyFamilyName = "Nidoran♂", cp = null)
        val merged = merger.mergeIfSamePokemon(male, female)
        assertFalse(merged.isShiny)
        assertEquals("Nidoran♂", merged.pokemonName)
    }

    @Test fun sameNidoranStillMerges() {
        val first = PokemonScreenData(pokemonName = "Nidoran♀", candyFamilyName = "Nidoran♀", cp = 512, isShiny = true)
        val second = first.copy(cp = null, isShiny = false)
        assertTrue(merger.mergeIfSamePokemon(second, first).isShiny)
    }
}
