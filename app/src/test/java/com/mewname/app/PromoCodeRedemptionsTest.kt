package com.mewname.app

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PromoCodeRedemptionsTest {
    @Test fun confirmationsPersistByCodeAndCanBeUndoneIndependently() {
        val store = PromoCodeRedemptions(ApplicationProvider.getApplicationContext())
        store.preferences.edit().clear().commit()
        store.setRedeemed(" code123 ", true)
        store.setRedeemed("SECOND", true)
        val reopened = PromoCodeRedemptions(ApplicationProvider.getApplicationContext())
        assertTrue(reopened.isRedeemed("CODE123"))
        reopened.setRedeemed("code123", false)
        assertFalse(store.isRedeemed("CODE123"))
        assertTrue(store.isRedeemed("SECOND"))
        store.setRedeemed("  ", true)
        assertEquals(setOf("SECOND"), store.redeemedCodes())
        store.preferences.edit().clear().commit()
    }
}