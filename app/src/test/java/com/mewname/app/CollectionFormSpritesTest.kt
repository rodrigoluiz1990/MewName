package com.mewname.app
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
class CollectionFormSpritesTest {
 private val context = ApplicationProvider.getApplicationContext<Context>()
 private fun target(name: String, variant: String = "") = PersonalCollectionTarget(JSONArray(listOf(name,variant)).toString(),name)
 @Test fun unownKeepsAllLettersInOrderAndResolvesTheExactNormalAndShinySprites() {
  val catalog = CollectionDexCatalog.load(context)
  val expected = ('A'..'Z').map { it.toString() } + listOf("!", "?")
  val targets = collectionCompactTargets(catalog, "Unown").map { it.second }
  assertEquals(expected, targets.map { JSONArray(it.key).getString(1) })
  val images = PersonalCollectionImages(context)
  for ((variant, target) in expected.zip(targets)) {
   val form = when (variant) { "!" -> "EXCLAMATION_POINT"; "?" -> "QUESTION_MARK"; else -> variant }
   for (shiny in listOf(false, true)) {
    val expectedFile = "pm201.fUNOWN_$form" + (if (shiny) ".s" else "") + ".icon.png"
    assertTrue("${target.key}: $expectedFile", requireNotNull(images.url(target, shiny)).endsWith(expectedFile))
   }
  }
 }
 @Test fun legendaryTechnicalAliasesDoNotCreateExtraRowsOrLoseMarks() {
  val prefs = PersonalCollection.preferences(context)
  prefs.edit().clear().commit()
  try {
   for ((dex, name) in listOf(243 to "RAIKOU", 244 to "ENTEI", 245 to "SUICUNE", 380 to "LATIAS", 381 to "LATIOS")) {
    val normal = target(name)
    val technical = target(name, "GAME_FORM:${name}_S")
    val legacy = org.json.JSONObject().put("key", technical.key).put("label", "$name S")
      .put("groups", JSONArray(listOf("SHINY")))
    val ordinary = org.json.JSONObject().put("key", normal.key).put("label", name)
      .put("groups", JSONArray(listOf("IV100")))
    prefs.edit().clear().putString("entries", JSONArray(listOf(legacy, ordinary)).toString()).commit()
    val snapshot = PersonalCollection.read(context)
    assertEquals(setOf(normal.key), snapshot.entries.keys)
    assertEquals(setOf(PersonalCollectionGroup.SHINY, PersonalCollectionGroup.IV100), snapshot.entries.getValue(normal.key).groups)
    val catalog = CollectionDexCatalog.load(context, listOf(technical))
    val forms = catalog.single { it.dex == dex }.targets
    assertEquals(listOf(normal.key), forms.map { it.key })
    assertEquals(name.lowercase().replaceFirstChar(Char::titlecase), forms.single().label)
    assertEquals(1, collectionCompactTargets(catalog, name).size)
    if (name == "LATIAS" || name == "LATIOS") {
     assertTrue(PersonalCollection.supports(normal, PersonalCollectionGroup.MEGA))
     assertTrue(PersonalCollectionImages(context).urls(normal, PersonalCollectionGroup.MEGA).isNotEmpty())
    }
    assertEquals(1, collectionQuickTargets(catalog, PersonalCollectionGroup.SHINY, name).size)
    PersonalCollection.mark(context, technical, PersonalCollectionGroup.SHINY, false)
    assertEquals(setOf(PersonalCollectionGroup.IV100), PersonalCollection.read(context).entries.getValue(normal.key).groups)
   }
  } finally { prefs.edit().clear().commit() }
 }
 @Test fun keldeoHasExactlyTwoNamedFormsAndNoTechnicalReversions() {
  val catalog = CollectionDexCatalog.load(context)
  val forms = catalog.single { it.dex == 647 }.targets
  assertEquals(setOf("Keldeo (Ordinary)", "Keldeo (Resolute)"), forms.map { it.label }.toSet())
  assertEquals(2,forms.size)
  assertTrue(catalog.flatMap { it.targets }.none { it.label.contains("Home Form Reversion") || it.label.contains("Home Reversion") })
  val images = PersonalCollectionImages(context)
  assertEquals(2,forms.map { images.url(it) }.filterNotNull().distinct().size)
 }
 @Test fun burmyAndDudunsparceFormsHaveDistinctSprites() {
  val catalog = CollectionDexCatalog.load(context); val images = PersonalCollectionImages(context)
  for ((dex,count) in listOf(412 to 3,982 to 2)) {
   val forms = catalog.single { it.dex == dex }.targets
   assertEquals(count,forms.size)
   assertEquals(count,forms.map { images.url(it) }.filterNotNull().distinct().size)
  }
 }
 @Test fun missingMappingsUseExistingExactFilesAndUltraDoesNotBorrowNormal() {
  val images = PersonalCollectionImages(context)
  for (t in listOf(target("UNOWN","!"),target("UNOWN","?"),target("ROTOM","NORMAL"),
   target("VIVILLON","POKE_BALL"),target("WORMADAM","GAME_FORM:WORMADAM_SANDY"))) assertNotNull(t.key,images.url(t))
  assertNull(images.url(target("NECROZMA (ULTRA)")))
 }
 @Test fun extractedMiniorSpritesAreBundledAndResolveOffline() {
  val images = PersonalCollectionImages(context)
  for (t in listOf(target("MINIOR"),target("MINIOR","GAME_FORM:MINIOR_GREEN"))) {
   for (shiny in listOf(false,true)) {
    val url=requireNotNull(images.url(t,shiny));assertTrue(url.startsWith("file:///android_asset/"))
    context.assets.open(url.removePrefix("file:///android_asset/")).use { assertTrue(it.read() >= 0) }
   }
  }
 }
 @Test fun ordinaryAliasCanBeMarkedAndUnmarkedThroughCanonicalTarget() {
  val prefs=PersonalCollection.preferences(context);prefs.edit().clear().commit()
  try {
   PersonalCollection.mark(context,target("KELDEO","GAME_FORM:KELDEO_ORDINARY"),PersonalCollectionGroup.SHINY,true)
   assertEquals(setOf(target("KELDEO").key),PersonalCollection.read(context).entries.keys)
   PersonalCollection.mark(context,target("KELDEO"),PersonalCollectionGroup.SHINY,false)
   assertTrue(PersonalCollection.read(context).entries.isEmpty())
  } finally {prefs.edit().clear().commit()}
 }
}
