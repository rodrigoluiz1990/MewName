package com.mewname.app
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import com.mewname.app.model.SpecialBackgroundType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
class BackgroundCategoryTest {
 @Test fun requestedBackgroundsArePresentInTheirTabsWithUniqueCollectionKeys() {
  val context=ApplicationProvider.getApplicationContext<Context>()
  val files=SpecialBackgroundCatalog.files(context)
  val names=files.map { it.substringAfterLast('/') }
  assertEquals(names.size,names.toSet().size)
  for ((file,category) in listOf("sb_10thAnniversary_Mewtwo.png" to BackgroundCategory.ANNIVERSARY,
   "sb_Community_2026.png" to BackgroundCategory.COMMUNITY_DAY,
   "sb_GoTour2026_mega.png" to BackgroundCategory.MEGA_EVOLUTION)) {
   val path=files.single { it.endsWith("/$file") }
   assertEquals(category,SpecialBackgroundCatalog.category(path))
   context.assets.open(path).use { assertNotNull(android.graphics.BitmapFactory.decodeStream(it)) }
  }
 }
 @Test fun categoryFolderSupportsNewFilesWithoutRenamingCollectionKeys() {
  assertEquals(BackgroundCategory.COMMUNITY_DAY,SpecialBackgroundCatalog.category("background_refs/location_cards/dia_da_comunidade/sb_new.png"))
  assertEquals(BackgroundCategory.LOCATION,SpecialBackgroundCatalog.category("background_refs/location_cards/go_fest/lc_new.png"))
 }
 @Test fun locationPrefixWinsOverEventWords() {
  assertEquals(BackgroundCategory.LOCATION,SpecialBackgroundCatalog.category("lc_GoFest2026_city.png"))
 }
 @Test fun knownAssetsHaveSpecificCategoriesIncludingAbbreviations() {
  val expected=mapOf("sb_9thAnniversary.png" to BackgroundCategory.ANNIVERSARY,
   "sb_2024_decemberCdRecap.png" to BackgroundCategory.COMMUNITY_DAY,
   "sb_Community_2026.png" to BackgroundCategory.COMMUNITY_DAY,
   "sb_GoFest2025.png" to BackgroundCategory.GO_FEST,
   "sb_GoTour2026_mega.png" to BackgroundCategory.MEGA_EVOLUTION,
   "sb_GOWA2025_Global.png" to BackgroundCategory.WILD_AREA,
   "sb_Season20_TalesOfTransformation.png" to BackgroundCategory.SEASON,
   "sb_TeamLeader_blue.png" to BackgroundCategory.TEAM_LEADER,
   "sb_FestivalofColors_2026.png" to BackgroundCategory.SPECIAL)
  expected.forEach { (file,category) -> assertEquals(file,category,SpecialBackgroundCatalog.category(file)) }
  assertEquals(SpecialBackgroundType.MEGA_EVOLUTION,SpecialBackgroundCatalog.type("sb_GoTour2026_mega.png"))
 }
 @Test fun allCollectionImagesRemainAvailableWithStableKeys() {
  val context=ApplicationProvider.getApplicationContext<Context>()
  val files=SpecialBackgroundCatalog.files(context).map { it.substringAfterLast('/') }
  assertTrue(files.isNotEmpty())
  assertEquals(files.size,files.groupBy { SpecialBackgroundCatalog.category(it) }.values.sumOf { it.size })
  assertTrue(files.all { it.startsWith("lc_") || it.startsWith("sb_") })
 }
 @Test fun everyTagCategoryCanBeSelectedAndRoundTripped() {
  for (language in AppLanguage.entries) for(type in SpecialBackgroundType.entries) {
   val label=type.localizedLabel(language)
   assertTrue(label in specialBackgroundSelectionOptions(language))
   assertEquals(type,specialBackgroundTypeFromSelection(label,language))
  }
 }
 @Test fun realCommunityAnimationIsRecognizedWithoutLabellingTheNormalPhase() {
  val context=ApplicationProvider.getApplicationContext<Context>()
  val matcher=BackgroundReferenceMatcher()
  for (phase in listOf(0,2)) {
   val bitmap=javaClass.getResourceAsStream("/backgrounds/vulpix-phase$phase.png")!!.use { android.graphics.BitmapFactory.decodeStream(it) }
   val result=matcher.debugSpecialBackground(context,bitmap)
   println("phase=$phase result=$result")
   if (phase==2) assertEquals(SpecialBackgroundType.COMMUNITY_DAY,result.specialType)
   else assertNull(result.specialType)
   bitmap.recycle()
  }
 }
}
