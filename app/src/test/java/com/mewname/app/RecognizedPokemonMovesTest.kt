package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecognizedPokemonMovesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun readsPortugueseEnglishAndSpanishIntoCanonicalSelectorNames() {
        val english = PokemonMoveRecognition.read(context, "Pikachu", listOf("Thunder Shock 5", "Thunderbolt 80"))
        assertEquals("Thunder Shock", english.fast)
        assertEquals("Thunderbolt", english.charged)
        val portuguese = PokemonMoveRecognition.read(context, "Pikachu", listOf("TROVOADA DE CHOQUES", "5", "RELAMPAGO 80"))
        assertEquals(english, portuguese)
        assertEquals(english, PokemonMoveRecognition.read(context, "Pikachu", listOf("Impactrueno", "Atactrueno")))
    }

    @Test fun rejectsForeignMovesAndPartialMatches() {
        val result = PokemonMoveRecognition.read(context, "Pikachu", listOf("Scratch", "Thunde", "Solar Beam"))
        assertNull(result.fast)
        assertNull(result.charged)
        assertNull(PokemonMoveRecognition.read(context, null, listOf("Thunder Shock")).fast)
    }

    @Test fun keepsFirstVisibleChargedMoveAndAllowsPartialReading() {
        val moves = PokemonMoveSet(listOf(PokemonMove("Fast", "Fast", false)),
            listOf(PokemonMove("Second", "Second", false), PokemonMove("First", "First", true)))
        val result = PokemonMoveRecognition.match(listOf("First 100", "Second 50"), moves, emptyMap())
        assertNull(result.fast)
        assertEquals("First", result.charged)
    }

    @Test fun onlyAcceptsMovesAvailableForSelectedForm() {
        val moves = PokemonMoveSet(listOf(PokemonMove("Bite", "Bite", false)), emptyList())
        assertNull(PokemonMoveRecognition.match(listOf("Snarl"), moves, emptyMap()).fast)
        assertEquals("Bite", PokemonMoveRecognition.match(listOf("Bite 6"), moves, emptyMap()).fast)
    }
}
