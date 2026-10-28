package com.mewname.app

import com.mewname.app.domain.PokemonReadSessionMerger
import com.mewname.app.model.BackgroundDebugInfo
import com.mewname.app.model.PokemonScreenData
import com.mewname.app.model.SpecialBackgroundType
import org.junit.Assert.*
import org.junit.Test

/** The game alternates a special card with the type background, so the second read may see only the latter. */
class PokemonReadSessionBackgroundTest {
    private val merger = PokemonReadSessionMerger()
    private val special = PokemonScreenData(pokemonName = "Zapdos", candyFamilyName = "Zapdos", cp = 2989,
        hasSpecialBackground = true, specialBackgroundType = SpecialBackgroundType.TEAM_LEADER,
        backgroundDebugInfo = BackgroundDebugInfo(detectedSpecialType = SpecialBackgroundType.TEAM_LEADER))
    private val normal = special.copy(hasSpecialBackground = false, specialBackgroundType = null,
        backgroundDebugInfo = BackgroundDebugInfo())

    @Test fun specialCardSeenEarlierSurvivesTypeBackgroundRead() {
        val result = merger.mergeIfSamePokemon(normal, special)
        assertTrue(result.hasSpecialBackground)
        assertEquals(SpecialBackgroundType.TEAM_LEADER, result.specialBackgroundType)
    }

    @Test fun anotherPokemonDoesNotInheritTheCard() {
        val other = normal.copy(cp = 2990)
        assertFalse(merger.mergeIfSamePokemon(other, special).hasSpecialBackground)
    }

    @Test fun sameFamilyWithoutCpDoesNotInheritTheCard() {
        // Regidrago's wing hides its CP, so consecutive Regidragos only share the family.
        val regiSpecial = special.copy(pokemonName = "Regidrago", candyFamilyName = "Regidrago", cp = null,
            specialBackgroundType = SpecialBackgroundType.SPECIAL)
        val regiNormal = normal.copy(pokemonName = "Regidrago", candyFamilyName = "Regidrago", cp = null)
        val result = merger.mergeIfSamePokemon(regiNormal, regiSpecial)
        assertFalse(result.hasSpecialBackground)
        assertNull(result.specialBackgroundType)
    }
}
