package com.mewname.app

import android.app.Instrumentation
import android.os.Bundle
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mewname.app.domain.PvpRankCalculator
import com.mewname.app.domain.PvpCalculationOptions
import com.mewname.app.model.PvpLeague
import java.io.File

/** Device rendering probe: the phone's Meowth case, actual rank cell in glass mode. */
class RankStrikeInstrumentation : Instrumentation() {
 override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
 override fun onStart() {
  val result=Bundle()
  try {
   val rank=PvpRankCalculator().calculateFamilySpeciesLeagueRanks(targetContext,listOf("Persian"),0,14,12,
    currentPokemonName="Meowth",currentLevel=17.0,currentCp=307,options=PvpCalculationOptions(maxLevel=40))
    .single { it.league==PvpLeague.LITTLE }
   check(rank.rank==4 && !rank.eligible)
   val intent=targetContext.packageManager.getLaunchIntentForPackage(targetContext.packageName)!!
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
   val activity=startActivitySync(intent)
   runOnMainSync {
    activity.setContentView(ComposeView(activity).apply { setContent {
     MaterialTheme {
      CompositionLocalProvider(LocalGlassReviewStyle provides true) {
       Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        androidx.compose.material3.Text("Meowth 307 PC · nível 17 · IV 0/14/12")
        androidx.compose.material3.Text("Persian · Copinha · 737 PC: inelegível")
        CompactSelectableField("4",false,struckThrough=!rank.eligible,onClick={},modifier=Modifier.width(100.dp))
        androidx.compose.material3.Text("Controle elegível (sem risco)")
        CompactSelectableField("13",false,onClick={},modifier=Modifier.width(100.dp))
       }
      }
     }
    } })
   }
   waitForIdleSync()
   Thread.sleep(1500)
   val screenshot=uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
   File(targetContext.filesDir,"rank-strike-test.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }
   result.putString("stream","PASS: Persian rank=4 eligible=false; screenshot=files/rank-strike-test.png\n")
   finish(-1,result)
  } catch (e:Exception) { result.putString("stream",e.stackTraceToString());finish(0,result) }
 }
}
