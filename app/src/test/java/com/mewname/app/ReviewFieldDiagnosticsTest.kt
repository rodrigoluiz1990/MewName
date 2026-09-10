package com.mewname.app

import com.mewname.app.model.*
import org.junit.Assert.*
import org.junit.Test

class ReviewFieldDiagnosticsTest {
    @Test fun `empty selection exports every field and preserves all stages`() {
        val raw = PokemonScreenData(gender = Gender.UNKNOWN)
        val merged = raw.copy(gender = Gender.FEMALE)
        val current = merged.copy(gender = Gender.MALE)
        val fields = ReviewFieldDiagnostics.collect(raw, merged, current, emptySet())
        assertEquals(NamingField.entries.size, fields.size)
        val gender = fields.single { it.field == NamingField.GENDER }
        assertEquals("UNKNOWN", gender.captured)
        assertEquals("FEMALE", gender.merged)
        assertEquals("MALE", gender.displayed)
        assertTrue(gender.evidence.contains("Sem evidencia"))
    }
    @Test fun `single field export does not include unrelated fields`() {
        val d = PokemonScreenData(gender = Gender.GENDERLESS,
            genderDebugInfo = GenderDebugInfo(source = "species_catalog", detectedGender = Gender.GENDERLESS))
        val fields = ReviewFieldDiagnostics.collect(d, d, d, setOf(NamingField.GENDER))
        assertEquals(1, fields.size)
        assertTrue(ReviewFieldDiagnostics.render(fields).contains("species_catalog"))
        assertEquals("GENDERLESS", fields.single().displayed)
    }
    @Test fun `unchanged values and evidence are printed only once`() {
        val d = PokemonScreenData(gender = Gender.UNKNOWN,
            genderDebugInfo = GenderDebugInfo(source = "unrecognized", pokemonName = "Wurmple", candidateLines = listOf("Wurmple")))
        val log = ReviewFieldDiagnostics.render(ReviewFieldDiagnostics.collect(d, d, d, setOf(NamingField.GENDER)))
        assertFalse(log.contains("Historico:"))
        assertFalse(log.contains("GenderDebugInfo("))
        assertEquals(1, Regex("Origem:").findAll(log).count())
        assertTrue(log.contains("OCR regional: Wurmple"))
        assertTrue(log.contains("masculino=false; feminino=false"))
    }
    @Test fun `changed evidence and intermediate values are retained`() {
        val raw = PokemonScreenData(gender = Gender.UNKNOWN)
        val merged = raw.copy(gender = Gender.FEMALE, genderDebugInfo = GenderDebugInfo(source = "previous_capture"))
        val current = merged.copy(gender = Gender.UNKNOWN)
        val log = ReviewFieldDiagnostics.render(ReviewFieldDiagnostics.collect(raw, merged, current, setOf(NamingField.GENDER)))
        assertTrue(log.contains("captura=UNKNOWN -> combinado=FEMALE -> revisao=UNKNOWN"))
        assertTrue(log.contains("previous_capture"))
    }
}