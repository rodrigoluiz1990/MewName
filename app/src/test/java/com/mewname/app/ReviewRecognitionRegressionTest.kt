package com.mewname.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.AppLanguage
import com.mewname.app.domain.OcrPokemonParser
import com.mewname.app.domain.PokemonMoveRepository
import com.mewname.app.domain.PokemonReadSessionMerger
import com.mewname.app.model.Gender
import com.mewname.app.model.PokemonScreenData
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReviewRecognitionRegressionTest {
    @Test
    fun `colored backgrounds do not select female without a recognized gender`() {
        val bitmap = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.MAGENTA)
            assertEquals(Gender.UNKNOWN, OcrPokemonParser().detectGender("", emptyList(), null, "Pikachu", bitmap).first)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun `recognized symbols remain selected and Zacian forms stay neutral`() {
        val parser = OcrPokemonParser()
        assertEquals(Gender.MALE, parser.detectGender("♂", emptyList(), null, "Pikachu", null).first)
        assertEquals(Gender.FEMALE, parser.detectGender("♀", emptyList(), null, "Pikachu", null).first)
        for (name in listOf("Zacian Hero", "Zacian (Hero)", "Zacian (Coroado)")) {
            assertEquals(Gender.GENDERLESS, parser.detectGender("", emptyList(), null, name, null).first)
        }
    }

    @Test
    fun `unrecognized gender does not inherit the previous capture selection`() {
        val previous = PokemonScreenData(pokemonName = "Pikachu", cp = 500, gender = Gender.FEMALE)
        val current = previous.copy(gender = Gender.UNKNOWN)
        assertEquals(Gender.UNKNOWN, PokemonReadSessionMerger().mergeIfSamePokemon(current, previous).gender)
    }

    @Test
    fun `both move categories use configured legacy tag from the local catalogs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for ((species, fastName, chargedName) in listOf(
            Triple("Golem", "Mud Shot", "Ancient Power"),
            Triple("Charizard", "Dragon Breath", "Blast Burn")
        )) {
            val raw = PokemonMoveRepository.load(context, species, AppLanguage.EN)
            val enriched = PokemonMoveRepository.enrich(context, raw, AppLanguage.EN)
            for (moves in listOf(raw, enriched)) {
                val fast = moveDropdownOptions(moves.fastMoves, "[L]").first { it.value == fastName }
                val charged = moveDropdownOptions(moves.chargedMoves, "[L]").first { it.value == chargedName }
                assertTrue(fast.label.endsWith(" [L]"))
                assertTrue(charged.label.endsWith(" [L]"))
            }
        }
        val zacian = PokemonMoveRepository.load(context, "Zacian", AppLanguage.EN)
        assertTrue(zacian.fastMoves.none { it.legacy })
    }
}