package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.MasterIvBadgeCatalog
import com.mewname.app.domain.PokemonFamilySuggester
import com.mewname.app.model.PokemonScreenData
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RegionalMasterIvTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val catalog = MasterIvBadgeCatalog()
    private val families = PokemonFamilySuggester()
    private fun family(name: String) = families.familyMembersFor(context, null, name)
    private fun match(name: String, percent: Int, a: Int, d: Int, s: Int) =
        catalog.resolve(context, family(name), percent, a, d, s, name).isBestMatch

    @Test fun farfetchdCannotUseGalarOrSirfetchdRulesAndViceVersa() {
        assertEquals(true, match("Farfetch'd", 96, 13, 15, 15))
        assertEquals(false, match("Farfetch'd", 96, 15, 15, 13))
        for (name in listOf("Farfetch'd (Galar)", "Galarian Farfetch'd", "Sirfetch'd")) {
            assertEquals(name, true, match(name, 96, 15, 15, 13))
            assertEquals(name, false, match(name, 96, 13, 15, 15))
        }
    }

    @Test fun changingSelectedNameRecalculatesTheBadgeAndDisplayedRules() {
        val shared = family("Farfetch'd")
        val data = PokemonScreenData(pokemonName = "Farfetch'd", ivPercent = 96,
            attIv = 13, defIv = 15, staIv = 15)
        assertEquals(true, buildMasterIvReviewData(context, data, shared, catalog).masterIvBadgeMatch)
        val changed = buildMasterIvReviewData(context, data.withReviewSpecies("Farfetch'd (Galar)"), shared, catalog)
        assertEquals(false, changed.masterIvBadgeMatch)
        assertEquals(15, changed.masterIvBadgeDebugInfo?.expectedAttack)
        assertEquals(Triple(15, 15, 13), catalog.bestCombinations(context, shared, "Farfetch'd (Galar)")[96])
        assertEquals(Triple(13, 15, 15), catalog.bestCombinations(context, shared, "Farfetch'd")[96])
    }

    @Test fun regionalReadDoesNotInheritPreviousNameOrPositiveBadge() {
        val previous = PokemonScreenData(pokemonName = "Farfetch'd", candyFamilyName = "Farfetch'd",
            cp = 1000, ivPercent = 96, attIv = 13, defIv = 15, staIv = 15, masterIvBadgeMatch = true)
        val current = previous.copy(pokemonName = "Farfetch'd (Galar)", masterIvBadgeMatch = false)
        val merged = com.mewname.app.domain.PokemonReadSessionMerger().mergeIfSamePokemon(current, previous)
        assertEquals(current.pokemonName, merged.pokemonName)
        assertEquals(false, merged.masterIvBadgeMatch)
    }

    @Test fun unknownNameDoesNotChooseTheOrdinaryBranchOfMixedCandyFamily() {
        assertNull(catalog.resolve(context, family("Farfetch'd"), 96, 13, 15, 15, "Meu parceiro").isBestMatch)
    }

    @Test fun hisuianDecidueyeUsesItsOwnSpreadsheetRule() {
        assertEquals(true, match("Decidueye", 98, 15, 15, 14))
        assertEquals(false, match("Decidueye", 98, 14, 15, 15))
        for (name in listOf("Decidueye (Hisui)", "Hisuian Decidueye")) {
            assertEquals(true, match(name, 98, 14, 15, 15))
            assertEquals(false, match(name, 98, 15, 15, 14))
        }
    }

    @Test fun regionalRulesDoNotFallBackToAnOrdinarySpecies() {
        for (name in listOf("Raichu (Alola)", "Vulpix (Alola)", "Ponyta (Galar)", "Slowpoke (Galar)")) {
            assertTrue(name, catalog.bestCombinations(context, family(name), name).isEmpty())
            assertNull(name, match(name, 98, 14, 15, 15))
        }
        assertTrue(catalog.bestCombinations(context, family("Farfetch'd")).isEmpty())
    }

    @Test fun exclusiveEvolutionsRemainInTheirRegionalBranch() {
        for ((regional, evolution) in listOf(
            "Wooper (Paldea)" to "Clodsire", "Meowth (Galar)" to "Perrserker",
            "Sneasel (Hisui)" to "Sneasler", "Yamask (Galar)" to "Runerigus",
            "Zigzagoon (Galar)" to "Obstagoon", "Corsola (Galar)" to "Cursola"
        )) {
            val expected = catalog.bestCombinations(context, listOf(evolution), evolution)
            assertFalse(evolution, expected.isEmpty())
            assertEquals(regional, expected, catalog.bestCombinations(context, family(regional), regional))
        }
    }

    @Test fun auditEveryRegionalNameAndLegacyAlias() {
        val names = JSONArray(context.assets.open("pokemon/names.json").bufferedReader().use { it.readText() })
        val regional = sortedSetOf<String>()
        val form = Regex(".*\\((Alola|Galar|Hisui|Paldea)\\)$")
        for (i in 0 until names.length()) {
            val entry = names.getJSONObject(i)
            val name = entry.getString("name")
            if (!form.matches(name)) continue
            regional += name
            val expected = catalog.bestCombinations(context, family(name), name)
            val aliases = entry.optJSONArray("aliases") ?: continue
            for (j in 0 until aliases.length()) {
                val alias = aliases.getString(j)
                if (!Regex("^(Alolan|Galarian|Hisuian|Paldean) .+").matches(alias)) continue
                assertEquals(alias, expected, catalog.bestCombinations(context, family(name), alias))
            }
        }
        val allFamilies = JSONObject(context.assets.open("pokemon/families.json").bufferedReader().use { it.readText() })
        allFamilies.keys().forEach { key ->
            val members = allFamilies.optJSONArray(key) ?: return@forEach
            for (i in 0 until members.length()) {
                val member = members.getString(i)
                if (form.matches(member)) regional += member
            }
        }
        assertTrue(regional.size > 35)
        regional.forEach { name ->
            val rules = catalog.bestCombinations(context, family(name), name)
            println("REGIONAL_AUDIT|" + name + "|" + if (rules.isEmpty()) "Sem regra regional"
                else rules.entries.joinToString("; ") { (percent, iv) ->
                    percent.toString() + "%: " + iv.first + "/" + iv.second + "/" + iv.third })
        }
    }
}