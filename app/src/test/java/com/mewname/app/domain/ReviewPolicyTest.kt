package com.mewname.app.domain

import com.mewname.app.model.Gender
import com.mewname.app.model.NamingBlock
import com.mewname.app.model.NamingBlockType
import com.mewname.app.model.NamingConfig
import com.mewname.app.model.NamingField
import com.mewname.app.model.PokemonScreenData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPolicyTest {
    @Test
    fun `collects distinct variable fields in display order`() {
        val configs = listOf(
            NamingConfig(
                blocks = listOf(
                    variableBlock(NamingField.POKEMON_NAME),
                    variableBlock(NamingField.CP)
                )
            ),
            NamingConfig(
                blocks = listOf(
                    variableBlock(NamingField.CP),
                    variableBlock(NamingField.IV_PERCENT)
                )
            )
        )

        assertEquals(
            listOf(NamingField.POKEMON_NAME, NamingField.CP, NamingField.IV_PERCENT),
            ReviewPolicy.reviewableFields(configs)
        )
    }

    @Test
    fun `opens review when a required field is missing`() {
        assertTrue(
            ReviewPolicy.shouldOpenReview(
                PokemonScreenData(pokemonName = "Zacian", cp = null),
                listOf(NamingField.CP)
            )
        )
    }

    @Test
    fun `does not open review when required data is complete`() {
        assertFalse(
            ReviewPolicy.shouldOpenReview(
                PokemonScreenData(
                    pokemonName = "Zacian",
                    cp = 3200,
                    attIv = 15,
                    defIv = 14,
                    staIv = 13,
                    level = 40.0,
                    gender = Gender.GENDERLESS
                ),
                listOf(
                    NamingField.POKEMON_NAME,
                    NamingField.CP,
                    NamingField.IV_COMBINATION,
                    NamingField.LEVEL,
                    NamingField.GENDER
                )
            )
        )
    }

    @Test
    fun `does not open review for manually editable evolution fields`() {
        assertFalse(
            ReviewPolicy.shouldOpenReview(
                PokemonScreenData(pokemonName = "Zacian"),
                listOf(NamingField.EVOLUTION_TYPE, NamingField.EVOLVE_MARKER)
            )
        )
    }

    private fun variableBlock(field: NamingField) = NamingBlock(
        type = NamingBlockType.VARIABLE,
        field = field
    )
}