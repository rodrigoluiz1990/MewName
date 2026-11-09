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
class PalafinMasterIvTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val catalog = MasterIvBadgeCatalog()

    @Test fun finizenUsesHeroWhileEachSelectedFormKeepsItsOwnRule() {
        for (name in listOf("Finizen", "Palafin", "Palafin (Zero)", "Palafin (Hero)", "Palafin Heroi", "palafin_hero_form")) {
            val family = PokemonFamilySuggester().familyMembersFor(context, "Finizen", name)
            val hero = name != "Palafin" && name != "Palafin (Zero)"
            assertEquals(name, hero, catalog.resolve(context, family, 98, 14, 15, 15, name).isBestMatch)
            assertEquals(name, !hero, catalog.resolve(context, family, 98, 15, 15, 14, name).isBestMatch)
            assertEquals(name, if (hero) Triple(14,15,13) else Triple(15,15,12), catalog.bestCombinations(context,family,name)[93])
            assertEquals(if(hero) "Palafin (Hero)" else "Palafin (Zero)",catalog.combinationFamilyName(family,name))
        }
    }
    @Test fun namingOffersBothFormsWithoutDuplicateOrdinaryPalafin() {
        val suggester = PokemonFamilySuggester()
        for (name in listOf("Finizen", "Palafin", "Palafin (Zero)", "Palafin (Hero)")) {
            assertEquals(listOf("Finizen", "Palafin (Zero)", "Palafin (Hero)"), suggester.suggestionsFor(context,"Finizen",name))
        }
        assertNull(catalog.resolve(context, suggester.familyMembersFor(context,"Finizen",null),98,15,15,14).isBestMatch)
    }

    @Test fun explicitFormsKeepSeparateRules() {
        val zero = listOf("Palafin (Zero)")
        val hero = listOf("Palafin (Hero)")
        assertEquals(true, catalog.resolve(context, zero, 93, 15, 15, 12).isBestMatch)
        assertEquals(false, catalog.resolve(context, zero, 93, 14, 15, 13).isBestMatch)
        assertEquals(true, catalog.resolve(context, hero, 93, 14, 15, 13).isBestMatch)
        assertEquals(false, catalog.resolve(context, hero, 93, 15, 15, 12).isBestMatch)
    }
}
