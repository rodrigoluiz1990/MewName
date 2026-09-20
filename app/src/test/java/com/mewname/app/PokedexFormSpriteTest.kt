package com.mewname.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PokedexFormSpriteTest {
    @Test fun buildsSpriteIdsForNormalRegionalCompoundAndCostumeForms() {
        assertEquals("BULBASAUR", pokedexFormSpriteId("Bulbasaur", "Normal", 1))
        assertEquals("RATTATA_ALOLA_FORM", pokedexFormSpriteId("Rattata", "Alola", 19))
        assertEquals("MR_MIME_GALARIAN_FORM", pokedexFormSpriteId("Mr. Mime", "Galarian", 122))
        assertEquals("DARMANITAN_GALARIAN_STANDARD_FORM", pokedexFormSpriteId("Darmanitan", "Galarian_standard", 555))
        assertEquals("PIKACHU_ADVENTURE_HAT_2020_FORM", pokedexFormSpriteId("Pikachu", "Adventure_hat_2020", 25))
        assertEquals("FARFETCHD_GALARIAN_FORM", pokedexFormSpriteId("Farfetch'd", "Galarian", 83))
        assertEquals("NIDORAN_FEMALE", pokedexFormSpriteId("Nidoran female", "Normal", 29))
        assertEquals("NIDORAN_MALE", pokedexFormSpriteId("Nidoran male", "Normal", 32))
    }
}