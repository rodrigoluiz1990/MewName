package com.mewname.app

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class RaidSavedReportTest {
    @Test fun reportsRemainDiscoverableWithMissingOrBrokenCatalogAndNewRepository() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = java.nio.file.Files.createTempDirectory("raid-restart").toFile()
        val context = object : ContextWrapper(base) { override fun getFilesDir() = root }
        val dir = File(root,"raid-counters-v1").apply { mkdirs() }
        val report = RaidReport("BUZZWOLE","RAID_LEVEL_ULTRA_BEAST",50,1L,emptyList(),emptyList(),
            listOf(RaidCounter("MEWTWO",RaidMove("CONFUSION_FAST","Psychic"),RaidMove("PSYCHIC","Psychic"),1.2)))
        File(dir,"BUZZWOLE_RAID_LEVEL_ULTRA_BEAST_50.json").writeText(RaidJson.encode(report).toString())
        File(dir,"BROKEN_RAID_LEVEL_5_40.json").writeText("broken")
        for (brokenCatalog in listOf(false,true)) {
            if (brokenCatalog) File(dir,"catalog.json").writeText("broken")
            val repository = RaidCounterRepository(context)
            val choice = repository.resolveBoss("Buzzwole","BATALHA",4).selected
            assertEquals(RaidChoice("BUZZWOLE","RAID_LEVEL_ULTRA_BEAST"),choice)
            assertEquals(50,repository.initialLevel(choice!!.id,choice.tier))
            assertEquals(report,RaidCounterRepository(context).cached(choice.id,choice.tier,50))
            assertNull(repository.cached("BUZZWOLE","RAID_LEVEL_4",50))
            assertNull(repository.cached("BUZZWOLE","RAID_LEVEL_ULTRA_BEAST",40))
        }
    }
    @Test fun level20EstimateHasSeparateCacheAndDoesNotBecomeCounterPreference() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = java.nio.file.Files.createTempDirectory("raid-level20").toFile()
        val context = object : ContextWrapper(base) { override fun getFilesDir() = root }
        val dir = File(root, "raid-counters-v1").apply { mkdirs() }
        val report = RaidReport("BUZZWOLE", "RAID_LEVEL_ULTRA_BEAST", 20, 1L,
            emptyList(), emptyList(), listOf(RaidCounter("MEWTWO", RaidMove("CONFUSION_FAST", "Psychic"),
                RaidMove("PSYCHIC", "Psychic"), 3.1)))
        File(dir, "BUZZWOLE_RAID_LEVEL_ULTRA_BEAST_20.json").writeText(RaidJson.encode(report).toString())
        val repository = RaidCounterRepository(context)
        assertEquals(report, repository.cached(report.bossId, report.tier, 20))
        assertNull(repository.cached(report.bossId, report.tier, 40))
        assertNull(repository.cached(report.bossId, report.tier, 50))
        assertTrue(repository.cachedChoices().isEmpty())
        assertTrue(repository.initialLevel(report.bossId, report.tier) in listOf(40, 50))
        assertTrue(RaidCounterRepository.counterPath(report.bossId, report.tier, 20).contains("attackers/levels/20/"))
    }
    @Test fun preferredAttackerLevelSurvivesRepositoryRecreation() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val repository=RaidCounterRepository(context)
        val previous=repository.preferredLevel()
        try {
            repository.setPreferredLevel(50)
            assertEquals(50,RaidCounterRepository(context).preferredLevel())
        } finally { repository.setPreferredLevel(previous) }
    }
}
