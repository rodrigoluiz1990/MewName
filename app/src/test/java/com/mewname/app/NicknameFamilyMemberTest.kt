package com.mewname.app

import com.mewname.app.domain.PvpRankCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Purified Porygon-Z nicknamed "Porygon-" read on 2026-10-27: CP 3011 and 162 HP, resolved as Porygon. */
@RunWith(RobolectricTestRunner::class)
class NicknameFamilyMemberTest {
    private val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private val calculator = PvpRankCalculator()

    @Test fun onlyPorygonZFitsTheDetailScreenCpAndHp() {
        assertEquals(0, calculator.resolveIvEvidence(context, "Porygon", 3011, 162).matches)
        assertEquals(0, calculator.resolveIvEvidence(context, "Porygon2", 3011, 162).matches)
        assertTrue(calculator.resolveIvEvidence(context, "Porygon-Z", 3011, 162).matches > 0)
    }
}
