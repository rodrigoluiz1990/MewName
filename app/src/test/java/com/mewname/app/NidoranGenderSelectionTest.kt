package com.mewname.app

import com.mewname.app.model.Gender
import com.mewname.app.model.PokemonScreenData
import com.mewname.app.model.PvpLeague
import com.mewname.app.model.PvpLeagueRankInfo
import com.mewname.app.model.PvpSpeciesRankInfo
import com.mewname.app.domain.OcrPokemonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class NidoranGenderSelectionTest {
    @Test
    fun `switching Nidoran female to male keeps the base stage and clears derived data`() {
        val data = PokemonScreenData(
            pokemonName = "Nidoran♀",
            candyFamilyName = "Nidoran♀",
            gender = Gender.FEMALE,
            selectedFastMove = "Bite",
            selectedChargedMove = "Poison Fang",
            pvpLeague = PvpLeague.GREAT,
            pvpRank = 123,
            pvpPokemonName = "Nidoqueen",
            pvpLeagueRanks = listOf(PvpLeagueRankInfo(league = PvpLeague.GREAT, pokemonName = "Nidoqueen")),
            familyPvpRanks = listOf(PvpSpeciesRankInfo(league = PvpLeague.GREAT, pokemonName = "Nidoqueen")),
            hasLegacyMove = true
        )

        val result = data.withSelectedGender(Gender.MALE)

        assertEquals("Nidoran♂", result.pokemonName)
        assertEquals("Nidoran♂", result.candyFamilyName)
        assertEquals(Gender.MALE, result.gender)
        assertNull(result.pvpLeague)
        assertNull(result.pvpRank)
        assertNull(result.pvpPokemonName)
        assertTrue(result.pvpLeagueRanks.isEmpty())
        assertTrue(result.familyPvpRanks.isEmpty())
        assertNull(result.selectedFastMove)
        assertNull(result.selectedChargedMove)
        assertFalse(result.hasLegacyMove)
    }

    @Test
    fun `switching Nidoqueen to male selects Nidoking at the same evolution stage`() {
        val result = PokemonScreenData(
            pokemonName = "Nidoqueen",
            candyFamilyName = "Nidoran♀",
            gender = Gender.FEMALE
        ).withSelectedGender(Gender.MALE)

        assertEquals("Nidoking", result.pokemonName)
        assertEquals("Nidoran♂", result.candyFamilyName)
        assertEquals(Gender.MALE, result.gender)
    }

    @Test
    fun `OCR resolves a generic Nidoran to the detected gender family before deriving data`() {
        val parser = OcrPokemonParser()

        assertEquals(
            "Nidoran♀" to "Nidoran♀",
            parser.resolveGenderedNidoranIdentity("Nidoran", "Nidoran", Gender.FEMALE)
        )
        assertEquals(
            "Nidoran♂" to "Nidoran♂",
            parser.resolveGenderedNidoranIdentity("Nidoran", "Nidoran", Gender.MALE)
        )
    }

    @Test
    fun `clearing the gender keeps the selected Nidoran family`() {
        val result = PokemonScreenData(
            pokemonName = "Nidorino",
            candyFamilyName = "Nidoran♂",
            gender = Gender.MALE
        ).withSelectedGender(Gender.GENDERLESS)

        assertEquals("Nidorino", result.pokemonName)
        assertEquals("Nidoran♂", result.candyFamilyName)
        assertEquals(Gender.GENDERLESS, result.gender)
    }
}