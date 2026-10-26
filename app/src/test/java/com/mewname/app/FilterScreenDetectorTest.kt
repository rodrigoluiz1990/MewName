package com.mewname.app

import com.mewname.app.domain.FilterScreen
import com.mewname.app.domain.FilterScreenDetector
import com.mewname.app.domain.BattleOcrLine
import org.junit.Assert.*
import org.junit.Test

class FilterScreenDetectorTest {
    @Test fun `recognizes friends list controls across supported languages`() {
        for (text in listOf("EU AMIGOS 285 SOCIAL ONLINE ADICIONAR\nAMIGOS BUSCAR CONVIDAR RodBlack001 pegou Voltorb",
            "ME FRIENDS 285 SOCIAL ONLINE ADD FRIEND SEARCH INVITE",
            "YO AMIGOS SOCIAL ANADIR AMIGOS BUSCAR INVITAR")) {
            assertEquals(FilterScreen.FRIENDS, FilterScreenDetector.detect(text))
        }
    }
    @Test fun `recognizes storage grid across supported languages`() {
        for (text in listOf("TAGS POKÉMON OVOS 5792 / 8000 9/12 Buscar PC334 Wurmple PC 672 Wobbuffet",
            "TAGS POKEMON EGGS 5792/8000 SEARCH CP 334 CP 672",
            "TAGS POKEMON EGGS 1/8000 SEARCH CP 334",
            "TAGS POKEMON EGGS 0/8000 SEARCH NO POKEMON",
            "ETIQUETAS POKÉMON HUEVOS 5792/8000 BUSCAR PC334 PC672")) {
            assertEquals(FilterScreen.POKEMON, FilterScreenDetector.detect(text))
        }
    }
    @Test fun `does not mistake detail eggs or social tabs for lists`() {
        for (text in listOf("PC334 Wurmple 92/92 PS EVOLUIR FORTALECER",
            "TAGS POKÉMON OVOS 5792/8000 9/12 2km 5km",
            "EU AMIGOS SOCIAL RodBlack001", "Amigos Pokémon Voltorb")) {
            assertNull(FilterScreenDetector.detect(text))
        }
    }
    @Test fun openCustomTagGridDoesNotRequireStorageTabs() {
        assertEquals(FilterScreen.POKEMON,FilterScreenDetector.detect("Favorito Buscar PC3218 Moltres PC2700 Dragonite"))
        assertEquals(FilterScreen.POKEMON,FilterScreenDetector.detect("Trade SEARCH CP3218 CP2700"))
        assertNull(FilterScreenDetector.detect("Moltres PC3218 169/169 PS Buscar PC3465 FORTALECER"))
    }
    @Test fun populatedSearchCanBeRecognizedByCpGridWithoutSearchPlaceholder() {
        val lines = listOf(
            BattleOcrLine("PC3218", .08f, .23f, .26f, .26f),
            BattleOcrLine("PC2404", .40f, .23f, .58f, .26f),
            BattleOcrLine("PC1971", .08f, .39f, .26f, .42f)
        )
        assertEquals(FilterScreen.POKEMON, FilterScreenDetector.detect("Favoritos mol", lines))
        assertNull(FilterScreenDetector.detect("Moltres FORTALECER", lines))
        assertNull(FilterScreenDetector.detect("Favoritos mol", lines.take(2)))
    }
}
