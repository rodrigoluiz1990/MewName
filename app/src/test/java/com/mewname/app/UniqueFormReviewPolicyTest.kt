package com.mewname.app

import com.mewname.app.domain.ReviewPolicy
import com.mewname.app.model.NamingField
import com.mewname.app.model.PokemonScreenData
import com.mewname.app.model.VivillonPattern
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniqueFormReviewPolicyTest {
    private val fields = listOf(NamingField.UNIQUE_FORM)

    @Test
    fun `unique form requests missing pattern for every Vivillon evolution`() {
        for (name in listOf("Scatterbug", "Spewpa", "Vivillon")) {
            val data = PokemonScreenData(pokemonName = name)
            assertTrue(ReviewPolicy.shouldOpenReview(data, fields))
            assertFalse(ReviewPolicy.shouldOpenReview(data.copy(vivillonPattern = VivillonPattern.entries.first()), fields))
        }
    }

    @Test
    fun `Unown review checks the letter used to generate the name`() {
        val data = PokemonScreenData(pokemonName = "Unown")
        assertTrue(ReviewPolicy.shouldOpenReview(data.copy(uniqueForm = "A"), fields))
        assertFalse(ReviewPolicy.shouldOpenReview(data.copy(unownLetter = "A"), fields))
    }

    @Test
    fun `catalog forms still require selection and other species do not`() {
        for (name in listOf("Furfrou", "Genesect", "Rotom", "Spinda")) {
            val data = PokemonScreenData(pokemonName = name)
            assertTrue(ReviewPolicy.shouldOpenReview(data, fields))
            assertFalse(ReviewPolicy.shouldOpenReview(data.copy(uniqueForm = "Selected"), fields))
        }
        assertFalse(ReviewPolicy.shouldOpenReview(PokemonScreenData(pokemonName = "Pikachu"), fields))
    }
}