package com.mewname.app

import com.mewname.app.domain.UniquePokemonCatalog
import com.mewname.app.domain.UniquePokemonReferenceMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Reference file names (app/src/main/assets/unique_pokemon_refs) mapped to the form each one shows. */
class UniqueFormLabelTest {
    private val matcher = UniquePokemonReferenceMatcher()
    private fun label(pokemon: String, file: String) = matcher.labelForFile(UniquePokemonCatalog.specFor(pokemon)!!, file)

    @Test fun everyUnownLetterKeepsItsOwnLabel() {
        // "unown" itself contains n, o, u and w: substring matching turned O..Z into N.
        for (letter in 'a'..'z') {
            assertEquals("unown-$letter", letter.uppercase(), label("Unown", "unown-$letter.png"))
            assertEquals("Unown_$letter", letter.uppercase(), label("Unown", "Unown_${letter.uppercase()}.png"))
        }
        assertEquals("!", label("Unown", "unown-em.png"))
        assertEquals("!", label("Unown", "Unown_Ex.png"))
        assertEquals("?", label("Unown", "unown-qm.png"))
        assertEquals("?", label("Unown", "Unown_Qu.png"))
        assertNull(label("Unown", "unown_bg.png"))
    }

    @Test fun spindaPatternsAreNotAllNumberThree() {
        assertEquals("Spinda #1", label("Spinda", "pm0327_00_Line1.png"))
        assertEquals("Spinda #2", label("Spinda", "pm0327_00_PachiAA.png"))
        assertEquals("Spinda #3", label("Spinda", "pm0327_00_PachiAA_Heart.png"))
        assertEquals("Spinda #6", label("Spinda", "pm0327_00_PachiBD_shiny.png"))
        assertEquals("Spinda #7", label("Spinda", "spinda-07.png"))
        assertNull(label("Spinda", "spinda_bg.png"))
    }

    @Test fun everyFurfrouTrimFileHasALabel() {
        val expected = mapOf("dandy" to "Cavalheiro", "debutante" to "Debutante", "diamond" to "Diamante",
            "heart" to "Coração", "kabuki" to "Kabuki", "la-reine" to "Aristocrático", "matron" to "Madame",
            "pharaoh" to "Faraó", "natural" to "Selvagem", "star" to "Estrela")
        expected.forEach { (trim, form) -> assertEquals(trim, form, label("Furfrou", "furfrou-$trim.png")) }
        assertEquals("Aristocrático", label("Furfrou", "la_reine_icon.png"))
        assertEquals("Selvagem", label("Furfrou", "pokeball_icon.png"))
        assertEquals("Estrela", label("Furfrou", "star_icon.png"))
    }
}
