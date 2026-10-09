package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.MasterIvBadgeCatalog
import com.mewname.app.domain.PokemonFamilySuggester
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ApprovedMasterIvReviewTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val catalog = MasterIvBadgeCatalog()
    private val families = PokemonFamilySuggester()

    @Test fun allApprovedRegionalBranchesReachTheirEvolutionRules() {
        val branches = listOf(
            listOf("Rattata (Alola)", "Raticate (Alola)"), listOf("Raichu (Alola)"),
            listOf("Sandshrew (Alola)", "Sandslash (Alola)"), listOf("Vulpix (Alola)", "Ninetales (Alola)"),
            listOf("Diglett (Alola)", "Dugtrio (Alola)"), listOf("Meowth (Alola)", "Persian (Alola)"),
            listOf("Growlithe (Hisui)", "Arcanine (Hisui)"), listOf("Geodude (Alola)", "Graveler (Alola)", "Golem (Alola)"),
            listOf("Ponyta (Galar)", "Rapidash (Galar)"), listOf("Slowpoke (Galar)", "Slowbro (Galar)", "Slowking (Galar)"),
            listOf("Grimer (Alola)", "Muk (Alola)"), listOf("Voltorb (Hisui)", "Electrode (Hisui)"),
            listOf("Exeggutor (Alola)"), listOf("Marowak (Alola)"), listOf("Weezing (Galar)"), listOf("Tauros (Paldea)")
        )
        for (branch in branches) {
            val target = branch.last()
            val expected = catalog.bestCombinations(context, listOf(target), target)
            assertEquals(target, setOf(98, 96, 93, 91), expected.keys)
            for (name in branch) {
                val family = families.familyMembersFor(context, null, name)
                assertEquals(name, expected, catalog.bestCombinations(context, family, name))
                expected.forEach { (percent, iv) ->
                    assertEquals(name, true, catalog.resolve(context, family, percent, iv.first, iv.second, iv.third, name).isBestMatch)
                }
                assertEquals(name, true, catalog.resolve(context, family, 67, 10, 10, 10, name).isBestMatch)
            }
        }
    }

    @Test fun newAlolanRuleDoesNotAcceptOrdinaryRaticateCombination() {
        val family = families.familyMembersFor(context, null, "Raticate (Alola)")
        assertEquals(true, catalog.resolve(context, family, 96, 15, 14, 14, "Raticate (Alola)").isBestMatch)
        val ordinary = catalog.bestCombinations(context, family, "Raticate")
        assertNotEquals(ordinary[96], catalog.bestCombinations(context, family, "Raticate (Alola)")[96])
        ordinary[96]!!.let { iv ->
            assertEquals(false, catalog.resolve(context, family, 96, iv.first, iv.second, iv.third, "Raticate (Alola)").isBestMatch)
        }
    }

    @Test fun approvedTieSelectionAndExcelDateRecoveryArePreserved() {
        assertEquals(Triple(15,14,15), catalog.bestCombinations(context, listOf("Electrode (Hisui)"), "Electrode (Hisui)")[98])
        assertEquals(Triple(14,13,15), catalog.bestCombinations(context, listOf("Electrode (Hisui)"), "Electrode (Hisui)")[93])
        assertEquals(Triple(15,12,15), catalog.bestCombinations(context, listOf("Spidops"), "Spidops")[93])
        assertEquals(Triple(13,14,15), catalog.bestCombinations(context, listOf("Arceus"), "Arceus")[93])
    }
}
