package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.PvpRankCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.floor
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
class StatsFormRegressionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    // Independent values from the pinned Game Master / PvPoke sources in the audit report.
    @Test fun normalAndAlternateFormsUseTheirOwnBaseStats() {
        val calculator = PvpRankCalculator()
        val cases = listOf(
            Triple("basculegion male", "Basculegion", intArrayOf(217,144,260)),
            Triple("basculegion female", "Basculegion (F\u00eamea)", intArrayOf(199,144,260)),
            Triple("Raichu", "Raichu", intArrayOf(193,151,155)),
            Triple("Dugtrio", "Dugtrio", intArrayOf(167,136,111)),
            Triple("Articuno", "Articuno", intArrayOf(192,236,207)),
            Triple("palafin zero", "Palafin", intArrayOf(143,144,225)),
            Triple("palafin hero", "Palafin (Hero)", intArrayOf(322,196,225)),
            Triple("Minior", "Minior", intArrayOf(116,194,155)),
            Triple("minior core", "Minior (Core)", intArrayOf(218,131,155)),
            Triple("Necrozma", "Necrozma", intArrayOf(251,195,219)),
            Triple("necrozma dusk mane", "Necrozma (Juba do Crep\u00fasculo)", intArrayOf(277,220,200)),
            Triple("necrozma dawn wings", "Necrozma (Asas da Alvorada)", intArrayOf(277,220,200)),
            Triple("ultra necrozma", "Necrozma (Ultra)", intArrayOf(337,196,200)),
            Triple("Zamazenta", "Zamazenta", intArrayOf(254,236,192)),
            Triple("zamazenta crowned shield", "Zamazenta (Coroado)", intArrayOf(250,292,192))
        )
        for ((alias, canonical, base) in cases) {
            assertEquals(alias, canonical, calculator.canonicalName(context, alias))
            val cp = floor((base[0]+1) * sqrt((base[1]+15).toDouble()) *
                sqrt((base[2]+12).toDouble()) * 0.79030001 * 0.79030001 / 10).toInt()
            assertEquals(alias, cp, calculator.estimateCpAtLevel(context, alias,1,15,12,40.0))
        }
    }
}
