package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MaxSupportSuggestionsTest {
    private fun entry(name: String, types: List<String>) =
        BattleSuggestionEntry("Dynamax " + name, types, searchTerms = listOf(name.lowercase()))
    private fun stats(id: String, defense: Int, hp: Int) =
        RaidPokemon(id, emptyList(), 100, defense, hp, emptyList(), emptyList(), emptyList())

    @Test fun shieldsUseCombinedResistanceAndHealingPrioritizesHp() {
        val dual = entry("Charizard", listOf("Fire", "Flying"))
        val single = entry("Blastoise", listOf("Water"))
        val healer = entry("Blissey", listOf("Normal"))
        val metadata = RaidMetadata(listOf(stats("CHARIZARD", 200, 200), stats("BLASTOISE", 250, 200),
            stats("BLISSEY", 20, 500)).associateBy { it.id }, emptyMap())
        val roles = maxSupportSuggestions(listOf(dual, single, healer), listOf("Ground"),
            mapOf("Ground" to mapOf("Fire" to 1.6, "Flying" to 0.390625)), metadata)
        // Product is 0.625, versus the old incorrect average 0.9953125.
        assertEquals(dual, roles.first.first())
        assertEquals(healer, roles.second.first())
    }

    @Test fun unknownStatsAreNotInvented() {
        val unknown = entry("Unknown", listOf("Normal"))
        val result = maxSupportSuggestions(listOf(unknown), emptyList(), emptyMap(), RaidMetadata(emptyMap(), emptyMap()))
        assertTrue(result.first.isEmpty())
        assertTrue(result.second.isEmpty())
    }

    @Test fun bundledRosterIncludesHealersAndCopyUsesSelectedRoleOnly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val roster = GameInfoRepository.loadMaxBattleRoster()
        val roles = maxSupportSuggestions(roster, listOf("Fire"), GameInfoRepository.loadTypeEffectiveness(context),
            RaidCounterRepository(context).metadata())
        assertEquals("Dynamax Blissey", roles.second.first().name)
        assertTrue(roles.first.isNotEmpty())
        val copied = BattleAdvisor.fastCopyTextFor(context, BattleMode.MAX, listOf(roles.second.first()))
        assertTrue(copied.contains("blissey"))
        assertFalse(copied.substringAfter("&").contains("zacian"))
        assertFalse(copied.substringAfter("&").contains("zamazenta"))
    }
    @Test fun resolvesEachRosterEntryOnlyOnceBeforeSorting() {
        var catalogScans = 0
        val backing = (1..20).associate { index ->
            val id = "SPECIES_" + index
            id to stats(id, 100 + index, 200 + index)
        }
        val tracked = object : Map<String, RaidPokemon> by backing {
            override val values: Collection<RaidPokemon>
                get() { catalogScans++; return backing.values }
        }
        val roster = (1..20).map { entry("Species-" + it, listOf("Normal")) }
        val roles = maxSupportSuggestions(roster, emptyList(), emptyMap(), RaidMetadata(tracked, emptyMap()))
        assertEquals(10, roles.first.size)
        assertEquals(10, roles.second.size)
        assertTrue("Sorting must not repeatedly scan the full catalog: " + catalogScans,
            catalogScans <= roster.size)
    }
}