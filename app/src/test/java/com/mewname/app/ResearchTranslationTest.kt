package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResearchTranslationTest {
    @Test fun indexedTranslationsPreserveEveryLegacyVariantInAllLanguages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val html = javaClass.getResourceAsStream("/leekduck/research.html")!!.bufferedReader().use { it.readText() }
        val titles = LeekDuckParser.parse(LeekSection.RESEARCH, html, 0).entries.map { it.title }.distinct()
        for (language in AppLanguage.entries) {
            val templates = GameTextRepository.researchTemplates(context, language)
            val index = GameTextRepository.researchIndex(context, language)
            for (title in titles) {
                val expected = templates.mapNotNull { it.translate(title) }.distinct()
                assertEquals("$language: $title", expected, index.variants(title))
                assertEquals(expected, index.variants(title))
            }
        }
    }
    @Test fun currentResearchHasPortugueseTranslations() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val html = javaClass.getResourceAsStream("/leekduck/research.html")!!.bufferedReader().use { it.readText() }
        val templates = GameTextRepository.researchTemplates(context, AppLanguage.PT_BR)
        val missing = LeekDuckParser.parse(LeekSection.RESEARCH, html, 0).entries.map { it.title }.distinct()
            .filter { task -> templates.none { it.translate(task) != null } }
        assertTrue("Missing translations: $missing", missing.isEmpty())
    }
    @Test fun smallGrammarAndOcrDifferencesAreAcceptedButConditionsAreNot() {
        assertTrue(ResearchTaskComparison.matches("Fortalecer seus Pokémon 5 vezes.", "Fortaleça Pokémon 5 vezes"))
        assertTrue(ResearchTaskComparison.matches("Fortalecer seus Pokémon 5 vezes.", "Fortalecer Pokemon 5 vezes"))
        assertTrue(ResearchTaskComparison.matches("Fortalecer seus Pokémon 5 vezes.", "Fortalecer Pokernon 5 vezes"))
        assertTrue(ResearchTaskComparison.matches("Capturar 5 Pokémon", "Capturar 5 Pokemn"))
        assertTrue(ResearchTaskComparison.matches("Chocar 1 Ovo", "Choque um ovo"))
        assertFalse(ResearchTaskComparison.matches("Capturar 5 Pokémon", "Capturar 15 Pokémon"))
        assertFalse(ResearchTaskComparison.matches("Capturar 5 Pokémon", "Capturar 5 Pokémon com clima favorável"))
        assertFalse(ResearchTaskComparison.matches("Capturar 5 Pokémon de fogo", "Capturar 5 Pokémon de gelo"))
        assertFalse(ResearchTaskComparison.matches("Fazer 3 ótimos arremessos", "Fazer 3 excelentes arremessos"))
        assertFalse(ResearchTaskComparison.matches("Fazer 3 ótimos arremessos", "Fazer 3 ótimos arremessos seguidos"))
        assertFalse(ResearchTaskComparison.matches("Capturar 3 Gible", "Capturar 3 Golett"))
    }
    @Test fun translatedCatalogFindsGrammarVariantsWithoutMatchingOtherTasks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val html = javaClass.getResourceAsStream("/leekduck/research.html")!!.bufferedReader().use { it.readText() }
        val catalog = LeekDuckParser.parse(LeekSection.RESEARCH, html, 0)
        val templates = GameTextRepository.researchTemplates(context, AppLanguage.PT_BR)
        val capture = CatalogCapture(LeekSection.RESEARCH, "PESQUISA\nHOJE\nFortaleça Pokémon 5 vezes\nCapture 10 Flamigo")
        val matched = CatalogScreenMatcher.matching(capture, catalog, templates = templates, now = 0)
        assertTrue(matched.any { it.title == "Fortalecer seus Pokémon 5 vezes." })
        assertTrue(matched.any { it.title == "Capturar 10 Flamigo." })
        assertFalse(matched.any { it.title.contains("3 vezes") || it.title.contains("7 vezes") || it.title.contains("15 vezes") })
        val basic = LeekCatalog(listOf(LeekEntry("basic", "Catch 5 Pokémon", "Regular")), "", 0)
        assertTrue(CatalogScreenMatcher.matching(CatalogCapture(LeekSection.RESEARCH,
            "Capturar 5 Pokémon\ncom clima favorável"), basic, templates = templates).isEmpty())
        assertTrue(CatalogScreenMatcher.matching(CatalogCapture(LeekSection.RESEARCH,
            "Capturar 5 Pokémon\ndo tipo Fogo"), basic, templates = templates).isEmpty())
    }
    @Test fun researchRewardNamesAndGroupsUseSelectedLanguage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pt = GameTextRepository.researchDisplayTerms(context, AppLanguage.PT_BR)
        assertEquals("Megaenergia de Venusaur ×10", ResearchDisplayText.reward("Venusaur Mega Energy ×10", pt, AppLanguage.PT_BR))
        assertFalse(ResearchDisplayText.reward("Stardust ×500", pt, AppLanguage.PT_BR).contains("Stardust"))
        assertFalse(ResearchDisplayText.reward("Razz Berry ×3", pt, AppLanguage.PT_BR).contains("Razz Berry"))
        assertEquals("Pikachu", ResearchDisplayText.reward("Pikachu", pt, AppLanguage.PT_BR))
        assertEquals("Arremessos", ResearchDisplayText.group("Throwing Tasks", AppLanguage.PT_BR))
        assertEquals("PC máximo 500 PC mínimo 450", ResearchDisplayText.detail("Max CP 500 Min CP 450", AppLanguage.PT_BR))
        val en = GameTextRepository.researchDisplayTerms(context, AppLanguage.EN)
        assertEquals("Stardust ×500", ResearchDisplayText.reward("Stardust ×500", en, AppLanguage.EN))
    }
}