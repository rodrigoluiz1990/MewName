package com.mewname.app.domain

import com.mewname.app.model.NamingConfig
import com.mewname.app.model.NamingField
import com.mewname.app.model.PokemonScreenData
import org.junit.Assert.assertEquals
import org.junit.Test

class PokemonNicknameTest {
    @Test
    fun `omits form annotations and keeps compound names`() {
        val config = NamingConfig(name = "Name", maxLength = 30, blocks = emptyList(), fields = listOf(NamingField.POKEMON_NAME))
        val generator = NameGenerator()
        listOf(
            "Raichu (Alola)" to "Raichu",
            "Zacian Hero" to "Zacian",
            "Zacian Crowned Sword" to "Zacian",
            "Zacian (Hero)" to "Zacian",
            "Zacian (Coroado)" to "Zacian",
            "Zamazenta (Coroado)" to "Zamazenta",
            "GOUGING FIRE" to "GOUGING FIRE",
            "Mr. Mime (Galar)" to "Mr. Mime",
            "Gouging Fire (Example)" to "Gouging Fire",
            "One Two Three" to "One Two"
        ).forEach { (input, expected) ->
            assertEquals(input, expected, generator.generate(PokemonScreenData(pokemonName = input), config))
        }
    }
    @Test
    fun `Zacian form labels use the same parentheses as Zamazenta`() {
        assertEquals("Zacian (Hero)", pokemonDisplayName("Zacian Hero"))
        assertEquals("Zacian (Coroado)", pokemonDisplayName("Zacian Crowned Sword"))
        assertEquals("Zacian (Coroado)", pokemonDisplayName("Zacian (Coroado)"))
        assertEquals("Zamazenta (Hero)", pokemonDisplayName("Zamazenta Hero"))
        assertEquals("Zamazenta (Coroado)", pokemonDisplayName("Zamazenta Crowned Shield"))
        assertEquals("Gouging Fire", pokemonDisplayName("Gouging Fire"))
    }}