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
class LycanrocMasterIvTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val catalog = MasterIvBadgeCatalog()
    private val family = PokemonFamilySuggester().familyMembersFor(context, "Rockruff", "Rockruff")
    private val ordinary = mapOf(98 to Triple(15,15,14), 96 to Triple(14,15,14),
        93 to Triple(13,15,14), 91 to Triple(12,15,14))
    private val midnight = mapOf(98 to Triple(14,15,15), 96 to Triple(13,15,15),
        93 to Triple(12,15,15), 91 to Triple(11,15,15))

    private fun match(name: String, percent: Int, iv: Triple<Int,Int,Int>) =
        catalog.resolve(context, family, percent, iv.first, iv.second, iv.third, name).isBestMatch

    @Test fun selectedFormsNeverAcceptTheOtherFormsRules() {
        val forms = listOf("Lycanroc", "Lycanroc (Diurno)", "Lycanroc (Crepúsculo)",
            "Lycanroc (Midday)", "Lycanroc dusk", "Lycanroc forma crepusculo")
        for (name in forms) {
            assertEquals(ordinary, catalog.bestCombinations(context, family, name))
            ordinary.forEach { (percent, iv) ->
                assertEquals(name, true, match(name, percent, iv))
                assertEquals(name, false, match(name, percent, midnight.getValue(percent)))
            }
        }
        for (name in listOf("Lycanroc (Noturno)", "Lycanroc midnight", "Lycanroc forma noturna")) {
            assertEquals(midnight, catalog.bestCombinations(context, family, name))
            midnight.forEach { (percent, iv) ->
                assertEquals(name, true, match(name, percent, iv))
                assertEquals(name, false, match(name, percent, ordinary.getValue(percent)))
            }
        }
    }

    @Test fun rockruffCanMatchBothEvolutionRulesAndAllFormsShare67() {
        for (percent in ordinary.keys) {
            assertEquals(true, match("Rockruff", percent, ordinary.getValue(percent)))
            assertEquals(true, match("Rockruff", percent, midnight.getValue(percent)))
        }
        for (name in family) assertEquals(true, match(name, 67, Triple(10,10,10)))
    }
}