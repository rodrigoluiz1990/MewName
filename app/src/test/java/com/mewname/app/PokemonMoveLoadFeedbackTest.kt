package com.mewname.app

import com.mewname.app.domain.AppLanguage
import com.mewname.app.domain.LoadState
import com.mewname.app.domain.PokemonMoveSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PokemonMoveLoadFeedbackTest {
    @Test
    fun `shows localized error message for failed move loading`() {
        val state = LoadState.Error(IllegalStateException("invalid catalog"))

        assertEquals(
            "Nao foi possivel carregar os ataques. Consulte o log do app.",
            PokemonMoveLoadFeedback.message(state, "Zacian", AppLanguage.PT_BR)
        )
        assertEquals(
            "Could not load moves. Check the app log.",
            PokemonMoveLoadFeedback.message(state, "Zacian", AppLanguage.EN)
        )
    }

    @Test
    fun `does not show a message when moves are loaded`() {
        assertNull(
            PokemonMoveLoadFeedback.message(
                LoadState.Success(PokemonMoveSet(emptyList(), emptyList())),
                "Zacian",
                AppLanguage.PT_BR
            )
        )
    }
}