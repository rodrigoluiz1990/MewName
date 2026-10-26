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
class LatestMasterIvReviewTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val catalog = MasterIvBadgeCatalog()
    private val families = PokemonFamilySuggester()
    private fun combinations(name: String, candy: String? = null) =
        catalog.bestCombinations(context, families.familyMembersFor(context, candy, name), name)

    @Test fun approvedMissingRulesAreReachableByDisplayName() {
        for (name in listOf("Articuno (Galar)", "Zapdos (Galar)", "Moltres (Galar)",
            "Typhlosion (Hisui)", "Overqwil", "Lilligant (Hisui)", "Darmanitan (Galar)",
            "Zoroark (Hisui)", "Stunfisk (Galar)", "Braviary (Hisui)", "Goodra (Hisui)",
            "Avalugg (Hisui)", "Walking Wake", "Iron Leaves", "Sinistcha (Comum)",
            "Sinistcha (Obra-Prima)", "Okidogi", "Munkidori", "Fezandipiti", "Ogerpon",
            "Gouging Fire", "Raging Bolt", "Iron Boulder", "Iron Crown", "Terapagos", "Pecharunt",
            "Gourgeist (Small)", "Gourgeist (Large)", "Gourgeist (Super)",
            "Kyurem (Preto)", "Kyurem (Branco)", "Minior (Core)", "Slither Wing")) {
            val rules = combinations(name)
            assertEquals(name, setOf(98, 96, 93, 91), rules.keys)
            val family = families.familyMembersFor(context, null, name)
            rules.forEach { (percent, iv) ->
                assertEquals(name, true, catalog.resolve(context, family, percent, iv.first, iv.second, iv.third, name).isBestMatch)
            }
            assertEquals(name, true, catalog.resolve(context, family, 67, 10, 10, 10, name).isBestMatch)
        }
    }

    @Test fun galarDarumakaReachesStandardAndZenWithoutLeakingToOrdinaryBranch() {
        val expected = mapOf(98 to Triple(15,15,14), 96 to Triple(14,15,14),
            93 to Triple(13,15,14), 91 to Triple(12,15,14))
        for (name in listOf("Darumaka (Galar)", "Darmanitan (Galar)", "Darmanitan (Galar Zen)",
            "Galarian Darmanitan Zen", "darmanitan_galarian_zen")) {
            assertEquals(name, expected, combinations(name, "Darumaka"))
        }
        val family = families.familyMembersFor(context, null, "Darumaka")
        assertTrue(family.containsAll(listOf("Darmanitan (Galar)", "Darmanitan (Galar Zen)")))
        for (name in listOf("Darumaka", "Darmanitan", "Darmanitan (Zen)")) {
            val result = catalog.resolve(context, family, 98, 15,15,14,name)
            assertTrue(result.notes, result.notes.startsWith("assinatura=DARMANITAN;"))
        }
    }

    @Test fun hisuiGrowlitheUsesApprovedArcanineRuleWithSharedCandyFamily() {
        for (name in listOf("Growlithe (Hisui)", "Hisuian Growlithe", "Arcanine (Hisui)", "Hisuian Arcanine")) {
            assertEquals(name, Triple(15,15,12), combinations(name, "Growlithe")[93])
            val family = families.familyMembersFor(context, "Growlithe", name)
            val result = catalog.resolve(context, family, 93,15,15,12,name)
            assertTrue(result.notes, result.notes.startsWith("assinatura=ARCANINEHISUI;"))
        }
    }

    @Test fun approvedFormsRejectAnotherFormsCombination() {
        for ((name, candy, wrong) in listOf(
            Triple("Kyurem (Preto)", "Kyurem", Triple(15,15,12)),
            Triple("Kyurem (Branco)", "Kyurem", Triple(15,15,12)),
            Triple("Gourgeist (Large)", "Pumpkaboo", Triple(15,12,15)),
            Triple("Pumpkaboo (Large)", "Pumpkaboo", Triple(15,12,15))
        )) {
            val family = families.familyMembersFor(context, candy, name)
            assertEquals(name, false, catalog.resolve(context, family, 93,wrong.first,wrong.second,wrong.third,name).isBestMatch)
        }
        assertEquals(Triple(15,13,14), combinations("Pumpkaboo (Large)", "Pumpkaboo")[93])
        assertEquals(Triple(12,15,15), combinations("Minior (Core)", "Minior")[93])
        assertNotEquals(combinations("Minior")[93], combinations("Minior (Core)", "Minior")[93])
        assertEquals(combinations("Sinistcha (Obra-Prima)"), combinations("Poltchageist (Artesao)", "Poltchageist"))
    }
}
