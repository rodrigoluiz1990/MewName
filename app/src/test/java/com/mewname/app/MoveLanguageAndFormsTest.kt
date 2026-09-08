package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MoveLanguageAndFormsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun cleanup() {
        context.getSharedPreferences("mewname_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        PokemonMoveRepository.clearCache()
    }

    @Test
    fun `fast and charged options are already translated in the saved home language`() {
        val catalog = GameInfoRepository.loadMoveCatalog(context).associateBy { it.name }
        for (language in listOf(AppLanguage.PT_BR, AppLanguage.ES, AppLanguage.EN, AppLanguage.PT_BR)) {
            context.getSharedPreferences("mewname_prefs", Context.MODE_PRIVATE)
                .edit().putString("app_language", language.name).commit()
            assertEquals(language, savedAppLanguage(context))
            val moves = PokemonMoveRepository.load(context, "Zacian (Coroado)", savedAppLanguage(context))
            assertTrue(moves.fastMoves.isNotEmpty())
            assertTrue(moves.chargedMoves.isNotEmpty())
            for (move in moves.fastMoves + moves.chargedMoves) {
                val expected = catalog.getValue(move.name).localizedName(language)
                assertEquals(expected, move.localizedName)
                if (language != AppLanguage.EN) assertNotEquals(move.name, move.localizedName)
            }
            val blade = moveDropdownOptions(moves.chargedMoves, "[L]").first { it.value == "Behemoth Blade" }
            assertEquals(catalog.getValue("Behemoth Blade").localizedName(language) + " [L]", blade.label)
        }
    }

    @Test
    fun `crowned moves never leak into hero forms regardless of cache order`() {
        for (order in listOf(listOf("Zacian", "Zacian (Coroado)"), listOf("Zacian (Coroado)", "Zacian"))) {
            PokemonMoveRepository.clearCache()
            order.forEach { PokemonMoveRepository.load(context, it, AppLanguage.EN) }
            val hero = PokemonMoveRepository.load(context, "Zacian Hero", AppLanguage.EN)
            val crowned = PokemonMoveRepository.load(context, "Crowned Sword Zacian", AppLanguage.EN)
            assertTrue(hero.chargedMoves.any { it.name == "Iron Head" })
            assertFalse(hero.chargedMoves.any { it.name == "Behemoth Blade" })
            assertTrue(crowned.chargedMoves.any { it.name == "Behemoth Blade" && it.legacy })
            assertFalse(crowned.chargedMoves.any { it.name == "Iron Head" })
            assertEquals(setOf("Metal Claw", "Air Slash"), crowned.fastMoves.map { it.name }.toSet())
        }
    }

    @Test
    fun `special attacks belong to their specific forms`() {
        val cases = listOf(
            Triple("Zamazenta (Coroado)", "Zamazenta", "Behemoth Bash"),
            Triple("Dialga (Origem)", "Dialga", "Roar Of Time"),
            Triple("Palkia (Origem)", "Palkia", "Spacial Rend"),
            Triple("Necrozma (Juba do Crepúsculo)", "Necrozma", "Sunsteel Strike"),
            Triple("Necrozma (Asas da Alvorada)", "Necrozma", "Moongeist Beam")
        )
        for ((form, base, special) in cases) {
            assertTrue(form, PokemonMoveRepository.load(context, form, AppLanguage.EN).chargedMoves.any { it.name == special && it.legacy })
            assertFalse(base, PokemonMoveRepository.load(context, base, AppLanguage.EN).chargedMoves.any { it.name == special })
        }
        assertTrue(PokemonMoveRepository.load(context, "Rayquaza", AppLanguage.EN).chargedMoves.any { it.name == "Dragon Ascent" && it.legacy })
    }
    @Test
    fun `translations also cover moves whose stat entries are pending`() {
        val english = GameTextRepository.moveTranslations(context, AppLanguage.EN)
        for (language in listOf(AppLanguage.PT_BR, AppLanguage.ES)) {
            val translations = GameTextRepository.moveTranslations(context, language)
            for (name in listOf("Mystical Fire", "Wildbolt Storm")) {
                val id = english.entries.first { it.value == name }.key
                val raw = PokemonMoveSet(emptyList(), listOf(PokemonMove(name, name, false)))
                assertEquals(translations.getValue(id), PokemonMoveRepository.enrich(context, raw, language).chargedMoves.single().localizedName)
            }
        }
    }

    @Test
    fun `other confirmed missing attacks are selectable`() {
        for ((species, move) in listOf("Heracross" to "Rock Tomb", "Cinderace" to "Blast Burn")) {
            assertTrue(PokemonMoveRepository.load(context, species, AppLanguage.EN).chargedMoves.any { it.name == move })
        }
    }}