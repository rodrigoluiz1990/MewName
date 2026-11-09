package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PokemonScreenRoutingTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun unknownScreensDoNotFallThroughToPokemonNaming() {
        for (text in listOf("", "12:30 78%", "POKEMON GO CONFIGURACOES LOJA ITENS", "Bulbasaur", "PC 3218", "Nomes sugeridos Ralts IV Master 98")) {
            assertEquals(text, BubbleScreenRouter.Target.Unsupported, BubbleScreenRouter.route(context, text))
        }
    }

    @Test fun recognizesDetailsWithoutAppraisalInSupportedLanguages() {
        for (text in listOf(
            "PC3218 Moltres 169/169 PS FORTALECER",
            "CP3218 Moltres 169/169 HP POWER UP",
            "PC3218 Moltres 169/169 PS DAR MAS",
            "CP3218 WEIGHT HEIGHT CANDY STARDUST POWER UP",
            "PC3218 PESO ALTURA DOCES POEIRA ESTELAR FORTALECER",
            "PC3218 PESO ALTURA CARAMELOS POLVO ESTELAR DAR MAS",
            "PC32 Moltres 169/169 PS FORTALECER",
            "Moltres 169/169 PS PESO ALTURA")) {
            assertTrue(text, PokemonDetailScreenDetector.matches(text))
            assertEquals(text, BubbleScreenRouter.Target.Pokemon, BubbleScreenRouter.route(context, text))
        }
    }

    @Test fun isolatedCollectionAndShopInformationIsNotDetailEvidence() {
        for (text in listOf("Pikachu Altura Peso", "100 DOCES 2000 POEIRA ESTELAR", "PC 3218 PC 2700 FAVORITOS", "EVOLUIR FORTALECER")) {
            assertFalse(text, PokemonDetailScreenDetector.matches(text))
        }
    }

    @Test fun appraisalStillHasPriorityOverGenericFallback() {
        assertEquals(BubbleScreenRouter.Target.Pokemon,
            BubbleScreenRouter.route(context, "PC476 Teddiursa Ataque Defesa PS"))
    }
}
