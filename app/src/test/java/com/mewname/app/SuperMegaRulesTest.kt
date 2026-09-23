package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SuperMegaRulesTest {
    private fun category(tier: String, id: String) = RaidHistoryCategory(tier, "", "", "",
        listOf(RaidHistoryItem(id, "https://www.pokebattler.com/raids/" + id, "", "", battleTier = tier.removePrefix("live_"))))

    @Test fun strongMegasNeverBecomeShieldRaidsByLevelAlone() {
        listOf("GROUDON_PRIMAL", "KYOGRE_PRIMAL", "LATIAS_MEGA", "LATIOS_MEGA", "RAYQUAZA_MEGA").forEach { id ->
            val group = mergeSavedRaidCategories(listOf(category("RAID_LEVEL_MEGA_5", id)), emptyList()).single()
            assertEquals("RAID_LEVEL_MEGA", group.id)
            assertEquals("RAID_LEVEL_MEGA_5", raidBattleTier(group.items.single(), group))
            assertFalse(raidHasShields(raidCategoryTier(group)))
        }
    }

    @Test fun savedSuperMegasAreCompleteAndKeepProviderSimulationTier() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val groups = mergeSavedRaidCategories(GameInfoRepository.loadRaidHistory(context).categories, emptyList())
        val shields = groups.single { raidHasShields(it.id) }
        assertEquals(savedSuperMegaBosses, shields.items.map(::raidHistorySpriteId).toSet())
        val falinks = shields.items.single { raidHistorySpriteId(it) == "FALINKS_MEGA" }
        assertEquals("RAID_LEVEL_MEGA", raidBattleTier(falinks, shields))
        assertEquals("2026-03-01T18:00:00", shields.items.single { raidHistorySpriteId(it) == "DRAGONITE_MEGA" }.lastEnd)
        assertEquals("Raid · Super Mega", raidCategoryTabTitle(shields, AppLanguage.PT_BR))
    }

    @Test fun liveEncounterMechanicsTakePrecedenceOverHistoricalSpecies() {
        val groups = normalizeRaidCategories(listOf(
            category("live_RAID_LEVEL_MEGA", "MALAMAR_MEGA"),
            category("live_RAID_LEVEL_4_MEGA_ENHANCED", "STARAPTOR_MEGA"),
            category("live_RAID_LEVEL_5_MEGA_ENHANCED", "MEWTWO_MEGA_X")
        ), saved = false)
        assertEquals(2, groups.size)
        assertEquals("MALAMAR_MEGA", raidHistorySpriteId(groups.first().items.single()))
        assertFalse(raidHasShields(raidCategoryTier(groups.first())))
        assertEquals(2, groups.last().items.size)
        assertEquals("RAID_LEVEL_5_MEGA_ENHANCED", groups.last().items.last().battleTier)
    }
}