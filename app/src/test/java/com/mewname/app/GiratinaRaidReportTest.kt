package com.mewname.app
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
class GiratinaRaidReportTest {
 private val meta get() = RaidCounterRepository(ApplicationProvider.getApplicationContext<Context>()).metadata()
 private fun fixture() = JSONObject(javaClass.getResourceAsStream("/raids/giratina-altered.json")!!.bufferedReader().use { it.readText() })
 @Test fun alteredAliasAcceptsRealResponseAndKeepsRequestedCacheIdentity() {
  for (id in listOf("GIRATINA", "GIRATINA_ALTERED_FORM")) for (level in listOf(20,40,50)) {
   val report=RaidJson.report(fixture(),id,"RAID_LEVEL_5",level,meta)
   assertEquals(id,report.bossId)
   assertEquals(3,report.counters.size)
   assertEquals(report,RaidJson.decode(RaidJson.encode(report),meta))
  }
 }
 @Test fun originShadowAndWrongTierStillRejected() {
  for ((id,tier) in listOf("GIRATINA_ORIGIN_FORM" to "RAID_LEVEL_5", "GIRATINA_SHADOW_FORM" to "RAID_LEVEL_5", "GIRATINA_ALTERED_FORM" to "RAID_LEVEL_3")) {
   assertThrows(IllegalArgumentException::class.java) { RaidJson.report(fixture(),id,tier,40,meta) }
  }
 }
}
