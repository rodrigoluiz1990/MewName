package com.mewname.app
import com.mewname.app.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
class VivillonDefaultSymbolsTest {
 @Test fun allPatternsUseDistinctThreeLetterDefaults() {
  val values = VivillonPattern.entries.map { defaultSymbols().getValue(it.symbolKey) }
  assertEquals(20,values.distinct().size)
  assertTrue(values.all { it.matches(Regex("[A-Z]{3}")) })
  assertEquals("ARQ",defaultSymbols()["VIVILLON_ARCHIPELAGO"])
  assertEquals("PKB",defaultSymbols()["VIVILLON_POKE_BALL"])
 }
 @Test fun previousEmojiDefaultsMigrateButCustomAndEmptySymbolsRemain() {
  val config = NamingConfig(name="Personalizado", symbols=defaultSymbols()+mapOf(
   "VIVILLON_ARCHIPELAGO" to "\uD83C\uDFDD\uFE0F",
   "VIVILLON_POKE_BALL" to "PB",
   "VIVILLON_GARDEN" to "MeuJardim",
   "VIVILLON_SUN" to ""))
  val restored=decodeSavedPresets(encodeSavedPresets(listOf(config))).configs.single()
  assertEquals("ARQ",restored.symbols["VIVILLON_ARCHIPELAGO"])
  assertEquals("PKB",restored.symbols["VIVILLON_POKE_BALL"])
  assertEquals("MeuJardim",restored.symbols["VIVILLON_GARDEN"])
  assertEquals("",restored.symbols["VIVILLON_SUN"])
  assertEquals(restored,decodeSavedPresets(encodeSavedPresets(listOf(restored))).configs.single())
 }
}
