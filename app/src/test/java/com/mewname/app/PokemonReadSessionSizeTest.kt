package com.mewname.app

import com.mewname.app.domain.PokemonReadSessionMerger
import com.mewname.app.model.PokemonScreenData
import com.mewname.app.model.PokemonSize
import com.mewname.app.model.SizeDebugInfo
import org.junit.Assert.*
import org.junit.Test

class PokemonReadSessionSizeTest {
    private val merger = PokemonReadSessionMerger()
    private val previous = PokemonScreenData(pokemonName = "Scatterbug", candyFamilyName = "Scatterbug",
        cp = 202, attIv = 13, defIv = 15, staIv = 15, size = PokemonSize.XXL,
        sizeDebugInfo = SizeDebugInfo(detectedSize = PokemonSize.XXL, textMatch = "XXL"), isShiny = true)

    @Test fun differentCpCannotMergeEvenWithIdenticalSpeciesAndIvs() {
        val current = previous.copy(cp = 205, size = PokemonSize.NORMAL, sizeDebugInfo = SizeDebugInfo(), isShiny = false)
        assertEquals(current, merger.mergeIfSamePokemon(current, previous))
    }
    @Test fun differentIvsCannotMergeEvenWithIdenticalSpeciesAndCp() {
        val current = previous.copy(attIv = 12, size = PokemonSize.NORMAL, sizeDebugInfo = SizeDebugInfo(), isShiny = false)
        assertEquals(current, merger.mergeIfSamePokemon(current, previous))
    }
    @Test fun currentNormalSizeClearsEarlierXxlEvenWhenIdentityMatches() {
        val current = previous.copy(size = PokemonSize.NORMAL, sizeDebugInfo = SizeDebugInfo())
        val result = merger.mergeIfSamePokemon(current, previous)
        assertEquals(PokemonSize.NORMAL, result.size)
        assertEquals(current.sizeDebugInfo, result.sizeDebugInfo)
    }
    @Test fun newConfirmedBadgeWinsOverPreviousSize() {
        val current = previous.copy(size = PokemonSize.XXS, sizeDebugInfo = SizeDebugInfo(detectedSize = PokemonSize.XXS))
        assertEquals(PokemonSize.XXS, merger.mergeIfSamePokemon(current, previous).size)
    }
    @Test fun complementaryReadCanStillRecoverMissingFields() {
        val current = PokemonScreenData(pokemonName = "Scatterbug", cp = 202,
            size = PokemonSize.NORMAL, sizeDebugInfo = SizeDebugInfo())
        val result = merger.mergeIfSamePokemon(current, previous)
        assertEquals(13, result.attIv)
        assertEquals(PokemonSize.NORMAL, result.size)
    }
}
