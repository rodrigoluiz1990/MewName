package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import com.mewname.app.ocr.OcrResult
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RaidOccludedNameTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val meta get() = RaidCounterRepository(context).metadata()
    private val xerneas = RaidChoice("XERNEAS", "RAID_LEVEL_5")
    private fun line(text: String, y: Float = .28f) = BattleOcrLine(text, .32f, y, .64f, y + .04f)

    @Test fun observedErneFragmentResolvesOnlyWithMatchingCurrentRaidAndTier() {
        assertEquals(xerneas, RaidBossResolver.resolveOccludedName(listOf(line("erne")), meta, listOf(xerneas), 5))
        assertNull(RaidBossResolver.resolveOccludedName(listOf(line("erne")), meta, listOf(xerneas), 3))
        assertNull(RaidBossResolver.resolveOccludedName(listOf(line("erne")), meta, listOf(xerneas), null))
        assertNull(RaidBossResolver.resolveOccludedName(listOf(line("erne")), meta, emptyList(), 5))
    }
    @Test fun gymTextShortFragmentsAndAmbiguousFormsNeverGuessBoss() {
        assertNull(RaidBossResolver.resolveOccludedName(listOf(line("erne", .06f)), meta, listOf(xerneas), 5))
        assertNull(RaidBossResolver.resolveOccludedName(listOf(line("ern")), meta, listOf(xerneas), 5))
        val choices = listOf(RaidChoice("MEWTWO", "RAID_LEVEL_5"), RaidChoice("MEWTWO_SHADOW_FORM", "RAID_LEVEL_5_SHADOW"))
        assertNull(RaidBossResolver.resolveOccludedName(listOf(line("ewtw")), meta, choices, 5))
    }
    @Test fun explicitRaidSkipsRocketTranslations() {
        val stages = mutableListOf<String>()
        val result = BubbleScreenRouter.route(context,
            OcrResult("Xerneas PC45899 BATALHA usando um Passe de Reide Grupo privado", null, emptyList())) { stage, _ -> stages += stage }
        assertTrue(result is BubbleScreenRouter.Target.Battle)
        assertFalse(stages.any { it.contains("Rocket") })
        assertEquals("Xerneas", (result as BubbleScreenRouter.Target.Battle).advice.bossName)
    }
    @Test fun rocketQuoteStillRoutesToCatalog() {
        assertTrue(BubbleScreenRouter.route(context, "Normal e fraco sao duas coisas bem diferentes.") is BubbleScreenRouter.Target.Catalog)
    }
}
