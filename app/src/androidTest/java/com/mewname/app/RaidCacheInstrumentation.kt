package com.mewname.app
import android.app.Instrumentation
import android.os.Bundle
import com.mewname.app.domain.RaidCounterRepository
class RaidCacheInstrumentation : Instrumentation() {
 override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
 override fun onStart() {
  val result=Bundle()
  try {
   repeat(2) {
    val repository=RaidCounterRepository(targetContext)
    val choice=repository.resolveBoss("Buzzwole","Buzzwole BATALHA",4).selected ?: error("Missing boss")
    check(choice.tier=="RAID_LEVEL_ULTRA_BEAST") { choice.toString() }
    val level=repository.initialLevel(choice.id,choice.tier)
    val report=repository.cached(choice.id,choice.tier,level) ?: error("Missing saved report")
    check(report.counters.isNotEmpty())
    result.putString("stream","PASS: ${choice.id} ${choice.tier}, level=$level, counters=${report.counters.size}; disk-only, repository recreated\n")
   }
   finish(-1,result)
  } catch(e:Exception) { result.putString("stream",e.stackTraceToString());finish(0,result) }
 }
}
