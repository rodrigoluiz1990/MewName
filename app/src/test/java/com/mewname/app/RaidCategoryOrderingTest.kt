package com.mewname.app

import com.mewname.app.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class RaidCategoryOrderingTest {
    private fun category(id: String, boss: String = "PIKACHU") = RaidHistoryCategory(
        id, id, id, id, listOf(RaidHistoryItem(boss, "https://www.pokebattler.com/raids/" + boss, "", "")))

    @Test fun dynamaxIsNumericAndMegaPrecedesEveryEnhancedMega() {
        val ids = listOf("RAID_LEVEL_5_MAX", "RAID_LEVEL_1_MAX", "RAID_LEVEL_3_MAX",
            "RAID_LEVEL_2_MAX", "RAID_LEVEL_4_MAX", "RAID_LEVEL_4_MEGA_ENHANCED",
            "RAID_LEVEL_MEGA_5", "RAID_LEVEL_MEGA")
        val sorted = orderedRaidCategories(ids.map { category("live_" + it) }).map { raidCategoryTier(it) }
        assertEquals(listOf("RAID_LEVEL_MEGA", "RAID_LEVEL_4_MEGA_ENHANCED", "RAID_LEVEL_MEGA_5",
            "RAID_LEVEL_1_MAX", "RAID_LEVEL_2_MAX", "RAID_LEVEL_3_MAX", "RAID_LEVEL_4_MAX", "RAID_LEVEL_5_MAX"), sorted)
        assertEquals(listOf("mega", "megaSuper"), orderedRaidCategories(listOf(category("megaSuper"), category("mega"))).map { it.id })
    }

    @Test fun legacyIncludesEveryLevelButNeverFutureOrUnset() {
        val data = JSONObject("""{"tiers":[
          {"tier":"RAID_LEVEL_1_LEGACY","type":"RAID_TYPE_RAID","raids":[{"pokemonId":"PIKACHU"}]},
          {"tier":"RAID_LEVEL_2_LEGACY","type":"RAID_TYPE_RAID","raids":[{"pokemonId":"MAGIKARP"}]},
          {"tier":"RAID_LEVEL_4_LEGACY","type":"RAID_TYPE_RAID","raids":[{"pokemonId":"BLASTOISE"}]},
          {"tier":"RAID_LEVEL_3_SHADOW_LEGACY","type":"RAID_TYPE_RAID","raids":[{"pokemonId":"SNEASEL"}]},
          {"tier":"RAID_LEVEL_2_MAX_LEGACY","type":"RAID_TYPE_RAID","raids":[{"pokemonId":"PIKACHU"}]},
          {"tier":"RAID_LEVEL_1","type":"RAID_TYPE_RAID","raids":[{"pokemonId":"EEVEE"}]},
          {"tier":"RAID_LEVEL_5_FUTURE","type":"RAID_TYPE_RAID","raids":[{"pokemonId":"MEWTWO"}]},
          {"tier":"RAID_LEVEL_UNSET","type":"RAID_TYPE_RAID","raids":[{"pokemonId":"MEW"}]}
        ]}""")
        assertEquals(5, raidCatalogChoices(data, saved = true).size)
        assertEquals(listOf("EEVEE"), raidCatalogChoices(data, saved = false).map { it.id })
        assertTrue(raidCatalogChoices(data, saved = true).none { it.id in listOf("EEVEE", "MEW", "MEWTWO") })
    }

    @Test fun historyMergesWithoutLosingDatesOrTierAndDynamaxSplitsByLevel() {
        val dated = category("raids5").let { it.copy(items = it.items.map { boss -> boss.copy(lastEnd = "2025-01-01") }) }
        val max = category("dynamax").let { it.copy(items = listOf(
            RaidHistoryItem("Pikachu", "https://www.pokebattler.com/raids/PIKACHU/RAID_LEVEL_1_MAX", "", ""))) }
        val merged = mergeSavedRaidCategories(listOf(dated, max),
            listOf(category("RAID_LEVEL_5"), category("RAID_LEVEL_1_MAX"), category("RAID_LEVEL_1", "EEVEE")))
        assertEquals(3, merged.size)
        assertEquals("2025-01-01", merged.single { it.id == "RAID_LEVEL_5" }.items.single().lastEnd)
        assertEquals(1, merged.single { it.id == "RAID_LEVEL_1_MAX" }.items.size)
        assertEquals("RAID_LEVEL_1", raidCategoryTier(merged.first()))
        assertEquals("PIKACHU", raidHistorySpriteId(max.items.single()))
    }
}