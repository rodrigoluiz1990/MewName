package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.MasterIvBadgeCatalog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MasterIvRenamedSpeciesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val catalog = MasterIvBadgeCatalog()

    @Test fun renamedFormsKeepTheMasterTableRule() {
        for (name in listOf("Zacian (Hero)", "Zacian Hero", "Zacian (Coroado)", "Zacian Crowned Sword",
            "Zamazenta (Hero)", "Zamazenta (Coroado)", "Zamazenta Crowned Shield")) {
            assertEquals(name, true, catalog.resolve(context, listOf(name), 98, 14, 15, 15).isBestMatch)
            assertEquals(name, false, catalog.resolve(context, listOf(name), 98, 15, 15, 14).isBestMatch)
        }
    }

    @Test fun unknownNicknameDoesNotInventAMasterRule() {
        assertNull(catalog.resolve(context, listOf("Meu parceiro"), 98, 14, 15, 15).isBestMatch)
        assertNull(catalog.resolve(context, listOf("Zacian (Hero)"), 98, null, 15, 15).isBestMatch)
    }
}