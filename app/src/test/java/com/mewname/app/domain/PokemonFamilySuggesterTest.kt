package com.mewname.app.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PokemonFamilySuggesterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `keeps regional evolution branches in the same selectable family`() {
        assertEquals(
            listOf("Slowpoke", "Slowpoke (Galar)", "Slowbro", "Slowbro (Galar)", "Slowking", "Slowking (Galar)"),
            PokemonFamilySuggester().familyMembersFor(context, null, "Slowpoke")
        )
        assertEquals(
            listOf("Slowpoke", "Slowpoke (Galar)", "Slowbro", "Slowbro (Galar)", "Slowking", "Slowking (Galar)"),
            PokemonFamilySuggester().familyMembersFor(context, null, "Slowpoke (Galar)")
        )
        assertEquals(
            listOf("Farfetch'd", "Farfetch'd (Galar)", "Sirfetch'd"),
            PokemonFamilySuggester().familyMembersFor(context, null, "Farfetch'd (Galar)")
        )
        assertEquals(
            listOf("Mime Jr.", "Mr. Mime", "Mr. Mime (Galar)", "Mr. Rime"),
            PokemonFamilySuggester().familyMembersFor(context, null, "Mr. Mime")
        )
    }

    @Test
    fun `includes supported regional evolutions and recent family extensions`() {
        assertEquals(
            listOf("Geodude", "Geodude (Alola)", "Graveler", "Graveler (Alola)", "Golem", "Golem (Alola)"),
            PokemonFamilySuggester().familyMembersFor(context, null, "Graveler (Alola)")
        )
        assertEquals(
            listOf("Mankey", "Primeape", "Annihilape"),
            PokemonFamilySuggester().familyMembersFor(context, null, "Primeape")
        )
        assertEquals(
            listOf("Pawniard", "Bisharp", "Kingambit"),
            PokemonFamilySuggester().familyMembersFor(context, null, "Bisharp")
        )
    }

    @Test
    fun `keeps special forms available in their base Pokemon family`() {
        assertEquals(
            listOf("Kyurem", "Kyurem (Preto)", "Kyurem (Branco)"),
            PokemonFamilySuggester().familyMembersFor(context, null, "Kyurem")
        )
        assertEquals(
            listOf("Zacian (Hero)", "Zacian (Coroado)"),
            PokemonFamilySuggester().familyMembersFor(context, null, "Zacian")
        )
    }

    @Test
    fun `does not overwrite a special form family with a generic family entry`() {
        assertEquals(
            listOf("Poltchageist (Artesao)", "Sinistcha (Obra-Prima)"),
            PokemonFamilySuggester().familyMembersFor(context, null, "Poltchageist (Artesao)")
        )
    }
    @Test
    fun `regional labels resolve to their own stats`() {
        val calculator = PvpRankCalculator()
        val canonical = calculator.estimateCpAtLevel(context, "Alolan Raichu", 15, 15, 15, 40.0)
        org.junit.Assert.assertNotNull(canonical)
        assertEquals(canonical, calculator.estimateCpAtLevel(context, "Raichu (Alola)", 15, 15, 15, 40.0))
        org.junit.Assert.assertNull(calculator.estimateCpAtLevel(context, "Zamazenta (Coroado)", 15, 15, 15, 40.0))
    }
    @Test
    fun `Nidoran without gender offers only its two evolution branches`() {
        val suggester = PokemonFamilySuggester()
        assertEquals(setOf("Nidoran♀", "Nidorina", "Nidoqueen", "Nidoran♂", "Nidorino", "Nidoking"),
            suggester.suggestionsFor(context, "Nidoran", "Nidoran").toSet())
        assertEquals(listOf("Nidoran♀", "Nidorina", "Nidoqueen"),
            suggester.suggestionsFor(context, "Nidoran", "Nidorina"))
        assertEquals(listOf("Nidoran♂", "Nidorino", "Nidoking"),
            suggester.suggestionsFor(context, "Nidoran", "Nidoran♂"))
    }

    @Test
    fun `unmapped family never suggests neighbors from the name catalog`() {
        assertEquals(listOf("Unknown family"), PokemonFamilySuggester().suggestionsFor(context, "Unknown family", "Unknown family"))
    }}