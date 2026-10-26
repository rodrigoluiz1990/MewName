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
