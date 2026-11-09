package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.RaidCounterRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RaidSpriteCatalogTest {
    private val repository get() = RaidCounterRepository(ApplicationProvider.getApplicationContext<Context>())

    @Test fun megaUsesItsGameSpriteInsteadOfTheOrdinaryRaidFeedImage() {
        assertTrue(repository.image("CHANDELURE_MEGA")!!.endsWith("pm609.fMEGA.icon.png"))
        assertTrue(repository.image("CHARIZARD_MEGA_X")!!.endsWith("pm6.fMEGA_X.icon.png"))
        assertNotEquals(repository.image("CHANDELURE"), repository.image("CHANDELURE_MEGA"))
    }

    @Test fun preservesRegionalPrimalAndCrownedForms() {
        assertTrue(repository.image("RAICHU_ALOLA_FORM")!!.endsWith("pm26.fALOLA.icon.png"))
        assertTrue(repository.image("GROUDON_PRIMAL")!!.endsWith("pm383.fPRIMAL.icon.png"))
        assertTrue(repository.image("ZACIAN_CROWNED_SWORD_FORM")!!.endsWith("pm888.fCROWNED_SWORD.icon.png"))
    }

    @Test fun ordinaryAndShadowDoNotInheritACostume() {
        assertTrue(repository.image("BULBASAUR")!!.endsWith("pm1.icon.png"))
        assertEquals(repository.image("CHANDELURE"), repository.image("CHANDELURE_SHADOW_FORM"))
    }

    @Test fun localSpritesWorkWithoutNetworkAndUnknownIdsStayMissing() {
        assertEquals("file:///android_asset/pokemon/collection_sprites/pm774.fGREEN.icon.png", repository.image("MINIOR_GREEN_FORM"))
        assertNull(repository.image("UNKNOWN_TEST_SPECIES"))
    }
}
