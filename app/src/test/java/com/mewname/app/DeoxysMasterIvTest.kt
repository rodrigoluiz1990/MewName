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
class DeoxysMasterIvTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val catalog = MasterIvBadgeCatalog()
    private val forms = listOf("Deoxys", "Deoxys (Ataque)", "Deoxys (Defesa)", "Deoxys (Velocidade)")
    @Test fun allFourFormsRemainAvailableInSuggestedNames() {
        for (name in forms) assertEquals(forms, PokemonFamilySuggester().suggestionsFor(context,"Deoxys",name))
    }
    @Test fun selectedDefenseDoesNotUseOtherFormsAndViceVersa() {
        for (name in forms + listOf("Deoxys (Normal)", "Deoxys Attack", "Deoxys Speed", "Deoxys Defense")) {
            val defense = name.contains("Def")
            assertEquals(name, defense, catalog.resolve(context,forms,98,15,14,15,name).isBestMatch)
            assertEquals(name, !defense, catalog.resolve(context,forms,98,14,15,15,name).isBestMatch)
            assertEquals(name, if(defense) Triple(15,14,15) else Triple(14,15,15),catalog.bestCombinations(context,forms,name)[98])
            assertEquals(name,catalog.combinationFamilyName(forms,name))
        }
    }
    @Test fun ambiguousFamilyDoesNotAwardAnotherFormsBadge() {
        assertNull(catalog.resolve(context,forms,98,15,14,15).isBestMatch)
    }
}
