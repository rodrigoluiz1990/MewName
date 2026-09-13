package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LeekDuckTest {
    private fun html(section: LeekSection) = javaClass.getResourceAsStream("/leekduck/${section.path}.html")!!.bufferedReader().use { it.readText() }
    @Test fun realRocketPagePreservesSlotsAndCatchablePokemon() {
        val entries = LeekDuckParser.parse(LeekSection.ROCKET, html(LeekSection.ROCKET), 10).entries
        assertTrue(entries.size > 15)
        assertTrue(entries.all { it.slots.map { s -> s.position } == listOf(1, 2, 3) })
        assertTrue(entries.all { it.slots.any { s -> s.encounter } })
        val giovanni = entries.single { it.group == "Giovanni" }
        assertEquals("Persian", giovanni.slots[0].pokemon.single().name)
        assertTrue(giovanni.slots[2].encounter)
        assertTrue(entries.any { it.group == "Grunts" && it.description.isNotBlank() })
    }
    @Test fun realEggPageKeepsDistanceOriginsRarityAndIncompleteNotes() {
        val entries = LeekDuckParser.parse(LeekSection.EGGS, html(LeekSection.EGGS), 10).entries
        assertEquals(9, entries.size)
        assertTrue(entries.single { it.title == "5 km Eggs" }.description.contains("incomplete"))
        assertEquals(2, entries.count { it.title.startsWith("7 km") })
        assertTrue(entries.any { it.title.contains("Adventure Sync") })
        assertTrue(entries.flatMap { it.pokemon }.any { it.rarity != null && it.shiny })
        assertTrue(entries.flatMap { it.pokemon }.any { it.name == "Meowth (Galar)" })
    }
    @Test fun realCodesKeepRewardsAndDoNotExposeHiddenExpiry() {
        val entries = LeekDuckParser.parse(LeekSection.CODES, html(LeekSection.CODES), 10).entries
        assertTrue(entries.size > 10)
        assertTrue(entries.any { it.expired })
        assertTrue(entries.any { it.unknownExpiry && it.expires == null })
        assertTrue(entries.any { it.expires != null })
        assertTrue(entries.all { it.code.isNotBlank() && it.pokemon.isNotEmpty() })
    }
    @Test fun invalidUpdateDoesNotOverwriteCachedCatalog() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = LeekDuckRepository(context)
        repository.storeValidated(LeekSection.ROCKET, html(LeekSection.ROCKET), 100)
        assertThrows(IllegalArgumentException::class.java) {
            repository.storeValidated(LeekSection.ROCKET, "<html>Service unavailable</html>", 200)
        }
        assertEquals(100L, repository.cached(LeekSection.ROCKET)!!.fetchedAt)
    }
    @Test fun shortcutsOpenNativeScreens() {
        assertEquals(AppScreen.ROCKET, bubbleAppShortcuts.single { it.key == "app_rocket" }.screen)
        assertEquals(AppScreen.RESEARCH, bubbleAppShortcuts.single { it.key == "app_research" }.screen)
        assertNull(bubbleAppShortcuts.single { it.key == "app_research" }.url)
        assertEquals(AppScreen.EGGS, bubbleAppShortcuts.single { it.key == "app_eggs" }.screen)
        assertEquals(AppScreen.PROMO_CODES, bubbleAppShortcuts.single { it.key == "app_promo" }.screen)
    }
    @Test fun bubbleCatalogNeverUsesNetworkEvenWhenRefreshIsRequested() {
        val now = 48 * 60 * 60 * 1000L
        assertFalse(shouldRefreshLeekCatalog(null, bubble = true, manual = false, now = now))
        assertFalse(shouldRefreshLeekCatalog(0, bubble = true, manual = false, now = now))
        assertFalse(shouldRefreshLeekCatalog(0, bubble = true, manual = true, now = now))
        assertFalse(shouldRefreshLeekCatalog(null, bubble = true, manual = true, now = now))
        assertTrue(shouldRefreshLeekCatalog(now, bubble = false, manual = true, now = now))
        assertTrue(shouldRefreshLeekCatalog(null, bubble = false, manual = false, now = now))
        assertTrue(shouldRefreshLeekCatalog(0, bubble = false, manual = false, now = now))
        assertFalse(shouldRefreshLeekCatalog(now, bubble = false, manual = false, now = now))
    }
    @Test fun researchSurvivesRepositoryRecreationAndCanBeMatchedOffline() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LeekDuckRepository(context).storeValidated(LeekSection.RESEARCH, html(LeekSection.RESEARCH), 100)
        val repository = LeekDuckRepository(context)
        val saved = repository.cached(LeekSection.RESEARCH)!!
        val capture = CatalogScreenMatcher.detect("PESQUISA\nHOJE\nFortalecer seus Pokémon 5 vezes.")!!
        val templates = GameTextRepository.researchTemplates(context, AppLanguage.PT_BR)
        val matches = CatalogScreenMatcher.matching(capture, saved, templates = templates)
        assertTrue(matches.isNotEmpty())
        assertTrue(matches.flatMap { it.pokemon }.isNotEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            repository.storeValidated(LeekSection.RESEARCH, "<html>Offline</html>", 200)
        }
        assertEquals(saved, repository.cached(LeekSection.RESEARCH))
    }
}
