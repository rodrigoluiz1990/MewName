package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RaidBossCpTest {
    private val meta get() = RaidCounterRepository(ApplicationProvider.getApplicationContext<Context>()).metadata()
    private val dialga = RaidChoice("DIALGA", "RAID_LEVEL_5")
    private val origin = RaidChoice("DIALGA_ORIGIN_FORM", "RAID_LEVEL_5")
    private val catalog = listOf(dialga, origin, RaidChoice("LUGIA", "RAID_LEVEL_5"))
    private fun line(text: String, y: Float = .19f) = BattleOcrLine(text, .25f, y, .8f, y + .05f)
    @Test fun knownBossValuesDistinguishDialgaForms() {
        assertEquals(53394, RaidBossResolver.bossCp(meta.pokemon.getValue(dialga.id), dialga.tier))
        assertEquals(54074, RaidBossResolver.bossCp(meta.pokemon.getValue(origin.id), origin.tier))
        listOf("53394", "P53394", "PC 53394", "CP53394", "53394-", "PC 53394 –", "& p53394-", "53394,", "53394.").forEach {
            assertEquals(dialga, RaidBossResolver.resolveBossCp(listOf(line(it), line("DUa", .29f)), meta, catalog, 5))
        }
        assertEquals(origin, RaidBossResolver.resolveBossCp(listOf(line("54074")), meta, catalog, 5))
    }
    @Test fun missingTierWrongTierAndAbsentBossDoNotGuess() {
        val lines = listOf(line("53394"))
        assertNull(RaidBossResolver.resolveBossCp(lines, meta, catalog, null))
        assertNull(RaidBossResolver.resolveBossCp(lines, meta, catalog, 4))
        assertNull(RaidBossResolver.resolveBossCp(lines, meta, catalog.drop(1), 5))
    }
    @Test fun ambiguousFormsAndConflictingReadingsStayManual() {
        assertNull(RaidBossResolver.resolveBossCp(listOf(line("53394")), meta,
            catalog + RaidChoice("DIALGA_SHADOW_FORM", "RAID_LEVEL_5_SHADOW"), 5))
        assertNull(RaidBossResolver.resolveBossCp(listOf(line("53394"), line("54074")), meta, catalog, 5))
    }
    @Test fun gymTimersCaptureCpAndDamagedDigitsAreNotBossCp() {
        listOf("53?94", "53O94", "0:53:394", "Academia 53394", "2307", "533-94", "53394-54074", "53394.0").forEach {
            assertNull(it, RaidBossResolver.resolveBossCp(listOf(line(it)), meta, catalog, 5))
        }
        assertNull(RaidBossResolver.resolveBossCp(listOf(line("53394", .05f)), meta, catalog, 5))
        assertNull(RaidBossResolver.resolveBossCp(listOf(line("53394", .5f)), meta, catalog, 5))
        assertNull(RaidBossResolver.bossCp(meta.pokemon.getValue(dialga.id), "RAID_LEVEL_6"))
    }
    @Test fun joinsAdjacentDigitsButRejectsSeparateNumbers() {
        val left = BattleOcrLine("53", .30f, .19f, .45f, .24f)
        val right = BattleOcrLine("394", .46f, .19f, .70f, .24f)
        assertEquals(dialga, RaidBossResolver.resolveBossCp(listOf(left,right), meta, catalog, 5))
        assertNull(RaidBossResolver.resolveBossCp(listOf(left,right.copy(left=.6f)), meta, catalog, 5))
        assertNull(RaidBossResolver.resolveBossCp(listOf(left,right.copy(top=.25f,bottom=.30f)), meta, catalog, 5))
        assertEquals(.19f, RaidLevelIconDetector.cpAnchor(listOf(left,right))!!.top)
    }
}
