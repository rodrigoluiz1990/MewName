package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.PokemonFamilySuggester
import com.mewname.app.domain.PvpRankCalculator
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.floor
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
class AuditedFormsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun allAuditedFormsResolveTheirOwnStatsAndFamilies() {
        val source = JSONArray(javaClass.getResourceAsStream("/audited-form-stats.json")!!.bufferedReader().use { it.readText() })
        val calculator = PvpRankCalculator()
        val families = PokemonFamilySuggester()
        assertEquals(36, source.length())
        for (i in 0 until source.length()) {
            val row = source.getJSONObject(i)
            val name = row.getString("name")
            val alias = row.getString("alias")
            assertEquals(alias, name, calculator.canonicalName(context, alias))
            val stats = row.getJSONObject("stats")
            val cp = floor((stats.getInt("attack")+1) * sqrt((stats.getInt("defense")+15).toDouble()) *
                sqrt((stats.getInt("stamina")+12).toDouble()) * 0.79030001 * 0.79030001 / 10).toInt()
            assertEquals(name, cp, calculator.estimateCpAtLevel(context, alias,1,15,12,40.0))
            assertTrue(name, families.familyMembersFor(context,null,name).contains(name))
        }
    }
    @Test fun sizedEvolutionsDoNotMixSizes() {
        val families = PokemonFamilySuggester()
        for (size in listOf("Small","Large","Super")) {
            val expected = listOf("Pumpkaboo ($size)", "Gourgeist ($size)")
            for (name in expected) assertEquals(expected, families.familyMembersFor(context,null,name))
        }
    }
    @Test fun nidoranOnlyHasGenderedStats() {
        val stats = JSONObject(context.assets.open("pokemon/stats.json").bufferedReader().use { it.readText() })
        assertFalse(stats.has("NIDORAN"))
        assertTrue(stats.has("NIDORAN\u2640"))
        assertTrue(stats.has("NIDORAN\u2642"))
    }
}
