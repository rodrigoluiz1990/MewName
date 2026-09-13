package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import com.mewname.app.model.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RegionalNamesAndManualRankTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun legacyRegionalAliasesResolveToParenthesizedCanonicalNamesAndStats() {
        val entries = JSONArray(context.assets.open("pokemon/names.json").bufferedReader().use { it.readText() })
        val calculator = PvpRankCalculator()
        val prefix = Regex("^(Alolan|Galarian|Hisuian|Paldean) .+", RegexOption.IGNORE_CASE)
        var checked = 0
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            val name = entry.getString("name")
            assertFalse(name, prefix.matches(name))
            val aliases = entry.optJSONArray("aliases") ?: continue
            for (j in 0 until aliases.length()) {
                val alias = aliases.getString(j)
                if (!prefix.matches(alias)) continue
                assertTrue(name, Regex(".* \\((Alola|Galar|Hisui|Paldea)\\)$").matches(name))
                assertEquals(name, calculator.canonicalName(context, alias))
                assertFalse(alias, prefix.matches(pokemonDisplayName(alias)))
                assertNotNull(name, calculator.estimateCpAtLevel(context, name, 12, 5, 10, 40.0))
                assertEquals(calculator.estimateCpAtLevel(context, name, 12, 5, 10, 40.0),
                    calculator.estimateCpAtLevel(context, alias, 12, 5, 10, 40.0))
                checked++
            }
        }
        assertTrue(checked > 15)
        assertNotNull(calculator.estimateCpAtLevel(context, "Wooper (Paldea)", 12, 5, 10, 40.0))
        val families = PokemonFamilySuggester()
        assertEquals(families.familyMembersFor(context, null, "Wooper (Paldea)"),
            families.familyMembersFor(context, null, "Paldean Wooper"))
        assertTrue(families.familyMembersFor(context, null, "Paldean Wooper").contains("Clodsire"))
    }

    @Test fun manualStruckRankSurvivesRecalculationAndIsUsedInSuggestedName() {
        val calculator = PvpRankCalculator()
        val data = PokemonScreenData(pokemonName = "Zacian Hero", level = 51.0, cp = 3682,
            attIv = 15, defIv = 15, staIv = 14, pvpLeague = PvpLeague.GREAT,
            pvpPokemonName = "Zacian Hero", pvpRank = 4096)
        for (options in listOf(PvpCalculationOptions(40), PvpCalculationOptions(50, true))) {
            val result = buildDerivedReviewData(context, data,
                listOf("Zacian (Hero)", "Zacian (Coroado)"), calculator, MasterIvBadgeCatalog(), options)
            val selected = result.familyPvpRanks.single {
                it.league == PvpLeague.GREAT && it.pokemonName == "Zacian Hero"
            }
            assertFalse(selected.eligible)
            assertNotNull(selected.rank)
            assertEquals(PvpLeague.GREAT, result.pvpLeague)
            assertEquals("Zacian Hero", result.pvpPokemonName)
            assertEquals(selected.rank, result.pvpRank)
            val config = NamingConfig(blocks = listOf(NamingBlock(type = NamingBlockType.VARIABLE,
                field = NamingField.PVP_RANK)))
            assertEquals(selected.rank.toString(), NameGenerator().generate(result, config))
        }
    }
}
