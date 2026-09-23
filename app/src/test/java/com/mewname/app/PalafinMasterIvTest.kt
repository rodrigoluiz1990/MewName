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

    @Test fun genericFamilyAcceptsBothSpreadsheetForms() {
        for (name in listOf("Finizen", "Palafin")) {
            val family = PokemonFamilySuggester().familyMembersFor(context, null, name)
            assertEquals(true, catalog.resolve(context, family, 98, 15, 15, 14).isBestMatch)
            assertEquals(true, catalog.resolve(context, family, 98, 14, 15, 15).isBestMatch)
            assertEquals(true, catalog.resolve(context, family, 91, 15, 15, 11).isBestMatch)
            assertEquals(true, catalog.resolve(context, family, 91, 13, 15, 13).isBestMatch)
            assertEquals(false, catalog.resolve(context, family, 98, 15, 14, 15).isBestMatch)
        }
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
