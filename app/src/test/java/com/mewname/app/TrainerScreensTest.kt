package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.TrainerProfileDraft
import com.mewname.app.domain.TrainerProfileLine
import com.mewname.app.domain.TrainerProfileParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrainerScreensTest {
    private val friendship = """CÓDIGO DE
TREINADOR
CÓDIGO QR
RodWhite001
4231 0729 2574
COMPARTILHAR CÓDIGO
ADICIONAR AMIGOS
Envie um pedido de amizade
0000 0000 0000
OU
ESCANEAR CÓDIGO QR"""

    @Test fun friendshipScreenIgnoresEmptyInputAndFindsTrainer() {
        assertTrue(TrainerProfileParser.isTrainerScreen(friendship))
        val result = TrainerProfileParser.parse(friendship,
            listOf(TrainerProfileLine("RodWhite001", .35f, .22f)))
        assertEquals("RodWhite001", result.name)
        assertEquals("423107292574", result.friendCode)
        assertNull(result.level)
    }

    @Test fun profileAndFriendListAreNotConfusedWithPokemon() {
        assertTrue(TrainerProfileParser.isTrainerScreen("EU AMGOS SOCIAL TOTAL DE ATIVIDADES\nRodWhite001\n72 NÍVEL"))
        assertFalse(TrainerProfileParser.isTrainerScreen("EU AMIGOS SOCIAL ADICIONAR AMIGOS BUSCAR"))
        assertFalse(TrainerProfileParser.isTrainerScreen("PC 334 Wurmple 72 PS DOCES"))
    }

    @Test fun partialCapturePreservesPreviouslyReadFields() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("trainer_profile", 0)
        prefs.edit().clear().commit()
        try {
            TrainerScreenReader.save(context, TrainerProfileDraft(name = "RodWhite001", level = "72", team = "Mystic"))
            TrainerScreenReader.save(context, TrainerProfileParser.parse(friendship))
            assertEquals("72", prefs.getString("level", null))
            assertEquals("Mystic", prefs.getString("team", null))
            assertEquals("423107292574", prefs.getString("code", null))
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun allZeroPlaceholderAloneIsNotAFriendCode() {
        assertNull(TrainerProfileParser.parse("Trainer Code\n0000 0000 0000").friendCode)
    }
}