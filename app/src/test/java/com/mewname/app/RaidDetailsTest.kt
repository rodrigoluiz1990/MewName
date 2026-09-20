package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class RaidDetailsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun repository() = RaidCounterRepository(context)
    private fun fixture() = JSONObject(javaClass.getResourceAsStream("/raids/zacian.json")!!.bufferedReader().use { it.readText() })
    @Test fun resolvesZacianFormsWithoutLosingTheirTypes() {
        val m=repository().metadata()
        assertEquals(listOf("Fairy"),m.resolve("Zacian Hero")!!.types)
        assertEquals("ZACIAN_HERO_FORM",m.resolve("Zacian (Hero)")!!.id)
        assertEquals(listOf("Fairy","Steel"),m.resolve("Zacian Crowned Sword")!!.types)
        assertEquals("METAGROSS_MEGA",m.resolve("Mega Metagross")!!.id)
        assertNull(m.resolve("Unrecognized monster"))
    }
    @Test fun captureCpUsesCorrectLevelAndCapturedForm() {
        val m=repository().metadata(); val p=m.resolve("Zacian Hero")!!
        assertEquals(2100,raidCp(p,10,false)); assertEquals(2188,raidCp(p,15,false))
        assertEquals(2625,raidCp(p,10,true)); assertEquals(2735,raidCp(p,15,true))
        assertEquals("ZACIAN_HERO_FORM",raidCaptureId("ZACIAN_CROWNED_SWORD_FORM"))
        assertEquals("CHARIZARD",raidCaptureId("CHARIZARD_MEGA_X"))
        assertEquals(listOf("cloudy"),raidWeather(p.types))
    }
    @Test fun raidApiUsesAggregatedCountersAndTheirCorrespondingMoves() {
        val m=repository().metadata()
        val r=RaidJson.report(fixture(),"ZACIAN_HERO_FORM","RAID_LEVEL_5",40,m)
        assertEquals(3,r.counters.size)
        assertEquals("ZAMAZENTA_CROWNED_SHIELD_FORM",r.counters.first().id)
        assertEquals("METAL_CLAW_FAST",r.counters.first().fast.id)
        assertEquals("BEHEMOTH_BASH",r.counters.first().charged.id)
        assertEquals("Steel",r.counters.first().charged.type)
        assertTrue(r.fast.contains("SNARL_FAST")); assertTrue(r.charged.contains("PLAY_ROUGH"))
        assertEquals(r,RaidJson.decode(RaidJson.encode(r),m))
        assertTrue(r.counters.zipWithNext().all { it.first.estimator <= it.second.estimator })
    }
    @Test fun rejectsAnotherBossInsteadOfCachingItsSuggestions() {
        try { RaidJson.report(fixture(),"KYOGRE","RAID_LEVEL_5",40,repository().metadata()); fail("Wrong boss accepted") }
        catch(_: IllegalArgumentException) {}
    }
    @Test fun cacheSeparatesLevelAndTierAndWorksWithoutNetwork() {
        val repo=repository(); val report=RaidJson.report(fixture(),"ZACIAN_HERO_FORM","RAID_LEVEL_5",40,repo.metadata())
        val dir=File(context.filesDir,"raid-counters-v1").apply { mkdirs() }
        File(dir,"ZACIAN_HERO_FORM_RAID_LEVEL_5_40.json").writeText(RaidJson.encode(report).toString())
        assertEquals(report,repo.cached("ZACIAN_HERO_FORM","RAID_LEVEL_5",40))
        assertNull(repo.cached("ZACIAN_HERO_FORM","RAID_LEVEL_5",50))
        assertNull(repo.cached("ZACIAN_HERO_FORM","RAID_LEVEL_3",40))
        File(dir,"ZACIAN_HERO_FORM_RAID_LEVEL_5_40.json").writeText("broken")
        assertNull(repo.cached("ZACIAN_HERO_FORM","RAID_LEVEL_5",40))
    }
    @Test fun bubbleUsesBossTierFromSavedCatalog() {
        assertEquals("RAID_LEVEL_3",raidTier("MACHAMP",listOf(RaidChoice("MACHAMP","RAID_LEVEL_3"))))
        assertEquals("RAID_LEVEL_MEGA",raidTier("METAGROSS_MEGA",emptyList()))
        assertEquals("RAID_LEVEL_5_SHADOW",raidTier("MEWTWO_SHADOW_FORM",emptyList()))
    }
    @Test fun currentCatalogExcludesHistoricalAndFutureButKeepsMaxEntries() {
        val dir=File(context.filesDir,"raid-counters-v1").apply { mkdirs() }
        File(dir,"catalog.json").writeText("""{"tiers":[
            {"type":"RAID_TYPE_RAID","tier":"RAID_LEVEL_5","raids":[{"pokemonId":"ZACIAN_HERO_FORM"}]},
            {"type":"RAID_TYPE_RAID","tier":"RAID_LEVEL_5_LEGACY","raids":[{"pokemonId":"KYOGRE"}]},
            {"type":"RAID_TYPE_RAID","tier":"RAID_LEVEL_5_FUTURE","raids":[{"pokemonId":"MEWTWO"}]},
            {"type":"RAID_TYPE_RAID","tier":"RAID_LEVEL_5_MAX","raids":[{"pokemonId":"GENGAR"}]}]}""")
        assertEquals(listOf(
            RaidChoice("ZACIAN_HERO_FORM","RAID_LEVEL_5"),
            RaidChoice("GENGAR","RAID_LEVEL_5_MAX")
        ),repository().catalog())
    }
}
