package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.MasterIvBadgeCatalog
import com.mewname.app.domain.PokemonFamilySuggester
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MasterIv67CoverageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val catalog = MasterIvBadgeCatalog()
    private fun asset(path: String) = context.assets.open(path).bufferedReader().use { it.readText() }

    @Test fun everyRuleDefinesTheUserConventionFor67Percent() {
        val rows = JSONArray(asset("pvp/master_iv_table.json"))
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val values = row.getJSONArray("67")
            assertEquals(row.getString("key"), listOf(10, 10, 10), (0 until values.length()).map { values.getInt(it) })
        }
    }

    @Test fun everyFamilyMemberRecognizesTenTenTenButNotOther67PercentDistributions() {
        val source = JSONObject(asset("pokemon/families.json"))
        val families = source.keys().asSequence().map { key ->
            when (val value = source.get(key)) {
                is JSONArray -> (0 until value.length()).map { value.getString(it) }
                else -> listOf(value.toString())
            }
        }.distinct().toList()
        for (family in families) for (selected in family) {
            assertEquals("$selected: $family", true, catalog.resolve(context, family, 67, 10, 10, 10, selected).isBestMatch)
            assertEquals("$selected: noncanonical 67%", false, catalog.resolve(context, family, 67, 10, 9, 11, selected).isBestMatch)
        }
    }

    @Test fun allStatsSpeciesAndFormsResolve67UsingTheActualFamilyLookup() {
        val stats = JSONObject(asset("pokemon/stats.json"))
        val suggester = PokemonFamilySuggester()
        for (name in stats.keys()) {
            val family = suggester.familyMembersFor(context, null, name)
            val result = catalog.resolve(context, family, 67, 10, 10, 10, name)
            assertEquals("$name: ${result.notes}", true, result.isBestMatch)
        }
    }
}
