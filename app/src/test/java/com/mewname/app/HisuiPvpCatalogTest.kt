package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.PokemonFamilySuggester
import com.mewname.app.domain.PvpRankCalculator
import com.mewname.app.domain.PvpCalculationOptions
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.floor
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
class HisuiPvpCatalogTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val expected = JSONArray(javaClass.getResourceAsStream("/hisui-stats.json")!!.bufferedReader().use { it.readText() })

    @Test fun allSixteenFormsHaveIndependentAliasesStatsAndCp() {
        assertEquals(16, expected.length())
        val stats = JSONObject(context.assets.open("pokemon/stats.json").bufferedReader().use { it.readText() })
        val calculator = PvpRankCalculator()
        for (i in 0 until expected.length()) {
            val entry = expected.getJSONObject(i)
            val name = entry.getString("name")
            val hisui = "$name (Hisui)"
            assertEquals(name, calculator.canonicalName(context, name))
            for (alias in listOf(hisui, "Hisuian $name", "$name de Hisui", "$name Hisui", "$name (Hisuian)")) {
                assertEquals(alias, hisui, calculator.canonicalName(context, alias))
            }
            for ((form, key) in listOf(name to "normal", hisui to "hisui")) {
                val base = entry.getJSONArray(key)
                val actual = stats.getJSONObject(form.uppercase())
                for ((index, stat) in listOf("attack", "defense", "stamina").withIndex()) {
                    assertEquals("$form $stat", base.getInt(index), actual.getInt(stat))
                }
                val cp = floor((base.getInt(0)+1) * sqrt((base.getInt(1)+15).toDouble()) *
                    sqrt((base.getInt(2)+12).toDouble()) * 0.79030001 * 0.79030001 / 10).toInt()
                assertEquals(form, cp, calculator.estimateCpAtLevel(context,form,1,15,12,40.0))
            }
        }
    }

    @Test fun allFormsRemainSeparateRowsInEveryLeagueAndFamily() {
        val calculator = PvpRankCalculator()
        val families = PokemonFamilySuggester()
        for (i in 0 until expected.length()) {
            val name = expected.getJSONObject(i).getString("name")
            val hisui = "$name (Hisui)"
            val family = families.familyMembersFor(context,null,hisui)
            assertTrue(name, family.contains(name))
            assertTrue(hisui, family.contains(hisui))
            val ranks = calculator.calculateFamilySpeciesLeagueRanks(context,listOf(name,hisui),1,15,12,
                options = PvpCalculationOptions(maxLevel = 50))
            assertEquals(name, 8, ranks.size)
            assertEquals(4, ranks.count { it.pokemonName == name && it.rank != null })
            assertEquals(4, ranks.count { it.pokemonName == hisui && it.rank != null })
        }
    }
}