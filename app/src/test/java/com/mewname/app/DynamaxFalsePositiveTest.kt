package com.mewname.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.mewname.app.domain.OcrPokemonParser
import com.mewname.app.domain.PokemonReadSessionMerger
import com.mewname.app.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DynamaxFalsePositiveTest {
    @Suppress("UNCHECKED_CAST")
    private fun detect(text: String, bitmap: Bitmap? = null): Pair<Set<EvolutionFlag>, EvolutionIconDebugInfo> {
        val method = OcrPokemonParser::class.java.getDeclaredMethod("detectEvolutionFlags",
            String::class.java, List::class.java, String::class.java, Rect::class.java,
            Bitmap::class.java, Boolean::class.javaPrimitiveType)
        method.isAccessible = true
        return method.invoke(OcrPokemonParser(), text, emptyList<Any>(), "Staryu", null, bitmap, false)
            as Pair<Set<EvolutionFlag>, EvolutionIconDebugInfo>
    }

    @Test fun purplePixelsWithoutBadgeEvidenceDoNotMarkDynamax() {
        val bitmap = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb(80, 40, 160))
            val (flags, debug) = detect("STARYU CP323 50 / 50 PS AGUA MEGA ENERGIA DE STARMIE", bitmap)
            assertFalse(EvolutionFlag.DYNAMAX in flags)
            assertNull(debug.dynamaxKeyword)
            assertTrue(debug.notes.contains("cor isolada não confirma"))
        } finally { bitmap.recycle() }
    }

    @Test fun textualDynamaxAndGigantamaxStillWorkWithoutColourEvidence() {
        assertTrue(EvolutionFlag.DYNAMAX in detect("DYNAMAX").first)
        assertTrue(EvolutionFlag.DYNAMAX in detect("BATALHA MAX").first)
        assertTrue(EvolutionFlag.GIGANTAMAX in detect("GIGANTAMAX").first)
        assertFalse(EvolutionFlag.DYNAMAX in detect("GIGANTAMAX").first)
    }

    @Test fun oldColourOnlyDetectionIsNotInheritedButConfirmedDetectionIsKept() {
        val current = PokemonScreenData(pokemonName = "Staryu", cp = 323)
        val previous = current.copy(evolutionFlags = setOf(EvolutionFlag.DYNAMAX),
            evolutionIconDebugInfo = EvolutionIconDebugInfo(dynamaxKeyword = "VISUAL_MAX_BADGE"))
        val merger = PokemonReadSessionMerger()
        assertFalse(EvolutionFlag.DYNAMAX in merger.mergeIfSamePokemon(current, previous).evolutionFlags)
        val confirmed = previous.copy(evolutionIconDebugInfo = EvolutionIconDebugInfo(dynamaxKeyword = "DYNAMAX"))
        assertTrue(EvolutionFlag.DYNAMAX in merger.mergeIfSamePokemon(current, confirmed).evolutionFlags)
    }
}
