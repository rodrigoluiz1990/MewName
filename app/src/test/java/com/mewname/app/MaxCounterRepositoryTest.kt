package com.mewname.app

import com.mewname.app.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class MaxCounterRepositoryTest {
    private fun sample(role: MaxRole) = JSONObject(javaClass.getResource("/raids/max-articuno-" + role.sort + ".json")!!.readText())
    private val metadata = RaidMetadata(emptyMap(), emptyMap())

    @Test fun followsProviderOrderForEachRoleWithoutFilteringUnknownSpecies() {
        val expected = mapOf(MaxRole.ATTACK to listOf("ZACIAN_CROWNED_SWORD_FORM", "ZAMAZENTA_CROWNED_SHIELD_FORM", "CINDERACE_GIGANTAMAX"),
            MaxRole.GUARD to listOf("WAILORD", "BLASTOISE", "GYARADOS"),
            MaxRole.HEAL to listOf("WAILORD", "BLISSEY", "SNORLAX_GIGANTAMAX"))
        expected.forEach { (role, ids) ->
            val raw = sample(role)
            val report = parseMaxCounters(raw, "ARTICUNO", "RAID_LEVEL_5_MAX", metadata)
            assertEquals(ids, report.counters.take(3).map { it.id })
            val providerBest = raw.getJSONArray("attackers").getJSONObject(0).getJSONObject("randomMove")
                .getJSONArray("defenders").let { it.getJSONObject(it.length() - 1) }
                .getJSONArray("byMove").let { it.getJSONObject(it.length() - 1) }
            assertEquals(providerBest.getString("move1"), report.counters.first().fast.id)
            assertEquals(report, RaidJson.decode(RaidJson.encode(report), metadata))
        }
    }

    @Test fun queryMatchesPublicMaxPageDefaultsAndUsesSelectedTier() {
        val path = maxCounterPath("ARTICUNO", "RAID_LEVEL_3_MAX", MaxRole.GUARD)
        assertTrue(path.contains("/levels/RAID_LEVEL_3_MAX/attackers/levels/40/"))
        assertTrue(path.contains("sort=TANK"))
        assertTrue(path.contains("dodgeStrategy=DODGE_100"))
        assertTrue(path.contains("aggregation=AVERAGE"))
        assertTrue(path.contains("weatherCondition=NO_WEATHER"))
        assertFalse(path.contains("includeMegas"))
    }

    @Test fun refusesResponseForAnotherBossOrTier() {
        assertThrows(IllegalArgumentException::class.java) {
            parseMaxCounters(sample(MaxRole.ATTACK), "MOLTRES", "RAID_LEVEL_5_MAX", metadata)
        }
        assertThrows(IllegalArgumentException::class.java) {
            maxCounterPath("../ARTICUNO", "RAID_LEVEL_5_MAX", MaxRole.ATTACK)
        }
    }
}