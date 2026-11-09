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
class NovemberMasterIvReviewTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val catalog = MasterIvBadgeCatalog()
    @Test fun approvedFormsResolveAllPercentagesInTheirRealCandyFamilies() {
        val cases = listOf(
            "Mewtwo (Armadura)" to listOf(Triple(15,15,14),Triple(15,14,14),Triple(15,13,14),Triple(15,12,14)),
            "Wormadam (Trash)" to listOf(Triple(15,14,15),Triple(15,13,15),Triple(15,12,15),Triple(15,11,15)),
            "Rotom (Fan)" to listOf(Triple(15,14,15),Triple(15,13,15),Triple(15,12,15),Triple(15,11,15)),
            "Rotom (Frost)" to listOf(Triple(15,14,15),Triple(15,13,15),Triple(15,12,15),Triple(15,11,15)),
            "Rotom (Heat)" to listOf(Triple(15,14,15),Triple(15,13,15),Triple(15,12,15),Triple(15,11,15)),
            "Rotom (Mow)" to listOf(Triple(15,14,15),Triple(15,13,15),Triple(15,12,15),Triple(15,11,15)),
            "Rotom (Wash)" to listOf(Triple(15,14,15),Triple(15,13,15),Triple(15,12,15),Triple(15,11,15)),
            "Basculegion (F\u00eamea)" to listOf(Triple(15,15,14),Triple(15,15,13),Triple(15,15,12),Triple(15,15,11)),
            "Wishiwashi (School)" to listOf(Triple(14,15,15),Triple(13,15,15),Triple(12,15,15),Triple(11,15,15)),
            "Eiscue (Noice)" to listOf(Triple(15,15,14),Triple(14,15,14),Triple(13,15,14),Triple(12,15,14)),
            "Indeedee (F\u00eamea)" to listOf(Triple(15,14,15),Triple(15,15,13),Triple(15,14,13),Triple(14,14,13)),
            "Basculegion" to listOf(Triple(14,15,15),Triple(13,15,15),Triple(12,15,15),Triple(15,15,11))
        )
        for ((name, expected) in cases) {
            val family = PokemonFamilySuggester().familyMembersFor(context, null, name)
            assertEquals(name, listOf(98,96,93,91).zip(expected).toMap(), catalog.bestCombinations(context,family,name))
            expected.forEachIndexed { i, iv ->
                assertEquals(name, true, catalog.resolve(context,family,listOf(98,96,93,91)[i],iv.first,iv.second,iv.third,name).isBestMatch)
            }
        }
    }
    @Test fun sharedFamilyCannotAcceptAnotherFormsIvs() {
        val family = listOf("Mewtwo", "Mewtwo (Armadura)")
        assertEquals(false, catalog.resolve(context,family,96,14,15,14,"Mewtwo (Armadura)").isBestMatch)
        assertEquals(false, catalog.resolve(context,family,96,15,14,14,"Mewtwo").isBestMatch)
        assertEquals(Triple(15,14,14),catalog.bestCombinations(context,family,"Mewtwo (Armored)")[96])
        val rotom = listOf("Rotom","Rotom (Wash)")
        assertEquals(false,catalog.resolve(context,rotom,98,14,15,15,"Rotom (Wash)").isBestMatch)
        assertEquals(false,catalog.resolve(context,rotom,98,15,14,15,"Rotom").isBestMatch)
    }
}
