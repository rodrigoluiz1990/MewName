package com.mewname.app
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import com.mewname.app.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
class PersianEligibilityTest {
 @Test fun phoneMeowthEvolutionExceedsLittleCap() {
  val context=ApplicationProvider.getApplicationContext<Context>()
  val calculator=PvpRankCalculator()
  val cp=calculator.estimateCpAtLevel(context,"Persian",0,14,12,17.0)!!
  println("Persian projected CP=$cp")
  assertEquals(737,cp)
  val result=buildDerivedReviewData(context,PokemonScreenData(pokemonName="Meowth",cp=307,level=17.0,attIv=0,defIv=14,staIv=12),listOf("Meowth","Persian"),calculator,MasterIvBadgeCatalog(),PvpCalculationOptions(maxLevel=40))
  val rank=result.familyPvpRanks.single { it.pokemonName=="Persian" && it.league==PvpLeague.LITTLE }
  assertEquals(4,rank.rank)
  assertFalse(rank.eligible)
 }
 @Test fun missingLevelIsInferredFromSourceCpWithoutHidingTheoreticalRank() {
  val context=ApplicationProvider.getApplicationContext<Context>()
  val ranks=PvpRankCalculator().calculateFamilySpeciesLeagueRanks(context,listOf("Meowth","Persian"),0,14,12,
   currentPokemonName="Meowth",currentCp=307,options=PvpCalculationOptions(maxLevel=40))
  val persian=ranks.single { it.pokemonName=="Persian" && it.league==PvpLeague.LITTLE }
  assertEquals(4,persian.rank)
  assertFalse(persian.eligible)
  assertTrue(ranks.single { it.pokemonName=="Meowth" && it.league==PvpLeague.LITTLE }.eligible)
 }
 @Test fun lowerLevelPersianCanStillFitTheCpLimit() {
  val context=ApplicationProvider.getApplicationContext<Context>()
  val ranks=PvpRankCalculator().calculateFamilySpeciesLeagueRanks(context,listOf("Persian"),0,14,12,
   currentPokemonName="Meowth",currentLevel=10.0,options=PvpCalculationOptions(maxLevel=40))
  assertTrue(ranks.single { it.league==PvpLeague.LITTLE }.eligible)
 }
}
