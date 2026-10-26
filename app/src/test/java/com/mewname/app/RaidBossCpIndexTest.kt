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
class RaidBossCpIndexTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val meta get() = RaidCounterRepository(context).metadata()
    private val axew = RaidChoice("AXEW", "RAID_LEVEL_1")
    private val gastly = RaidChoice("GASTLY_SHADOW_FORM", "RAID_LEVEL_1_SHADOW")
    private val lines = listOf(BattleOcrLine("4458", .25f, .19f, .75f, .24f))
    private fun candidates(text: String = "", catalog: List<RaidChoice> = listOf(axew, gastly),
        input: List<BattleOcrLine> = lines, level: Int? = 1) =
        RaidBossResolver.bossCpCandidates(input, text, meta, catalog, level, RaidBossCpIndex.load(context))

    @Test fun indexIsDexOrderedAndAgreesWithRuntimeFormula() {
        val json = JSONObject(context.assets.open("raids/boss_cp.json").bufferedReader().use { it.readText() })
        val rows = json.getJSONArray("pokemon")
        var previous = 0
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            assertTrue(row.getInt("dex") >= previous)
            previous = row.getInt("dex")
            val values = row.getJSONObject("bossCp")
            for (tier in values.keys()) assertEquals(values.getInt(tier),
                RaidBossResolver.bossCp(meta.pokemon.getValue(row.getString("id")), tier))
        }
    }
    @Test fun axewCollisionDoesNotGuessWithoutEvidence() {
        assertEquals(setOf(axew, gastly), candidates().toSet())
        assertEquals(listOf(gastly), candidates("Shadow raid"))
        assertEquals(listOf(axew), candidates("Raid comum"))
        assertEquals(setOf(axew, gastly), candidates("Shadow Park").toSet())
    }
    @Test fun exactNameBreaksTieAndIndexWorksWithoutWeeklyCatalog() {
        val named = lines + BattleOcrLine("Axew", .30f, .29f, .70f, .33f)
        assertEquals(listOf(axew), candidates(input=named))
        assertTrue(candidates(catalog=emptyList()).contains(axew))
        assertTrue(candidates(catalog=emptyList(), input=named).all { it.id.startsWith("AXEW") })
        assertEquals(listOf(axew), candidates("Raid comum", catalog=emptyList(), input=named))
    }
    @Test fun missingLevelConflictingCpAndCaptureNumbersDoNotMatch() {
        assertTrue(candidates(level=null).isEmpty())
        assertTrue(candidates(input=lines + lines.first().copy(text="53394")).isEmpty())
        assertTrue(candidates(input=listOf(lines.first().copy(top=.7f,bottom=.75f))).isEmpty())
    }
}
