package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.PvpRankCalculator
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
class PvpRankingOptimizationTest {
    @Test
    fun `binary search preserves exhaustive products CP levels and ties for every IV`() {
        val calculator = PvpRankCalculator()
        val cpmField = calculator.javaClass.getDeclaredField("cpmTable").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val cpms = cpmField.get(calculator) as List<Double>
        val optimized = calculator.javaClass.getDeclaredMethod("getBestStatProduct", JSONObject::class.java,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Double::class.javaPrimitiveType).apply { isAccessible = true }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stats = JSONObject(context.assets.open("pokemon/stats.json").bufferedReader().use { it.readText() })
        // Low stats, high stamina, high CP, regional forms and the reported evolution.
        val names = listOf("MAGIKARP", "SHUCKLE", "CHANSEY", "MEWTWO", "SLAKING", "PERSIAN", "ARCANINE", "ARCANINE (HISUI)")
        for (name in names) {
            val base = stats.getJSONObject(name)
            for (maxLevel in listOf(40.0, 41.0, 50.0, 51.0)) for (cap in listOf(500, 1500, 2500, Int.MAX_VALUE)) {
                for (a in 0..15) for (d in 0..15) for (s in 0..15) {
                    var expected: PvpRankCalculator.StatProduct? = null
                    val attack = base.getInt("attack") + a
                    val defense = base.getInt("defense") + d
                    val stamina = base.getInt("stamina") + s
                    // Reference: original exhaustive algorithm, including floor/minimum HP and CP.
                    for ((i, cpm) in cpms.withIndex()) {
                        val level = 1.0 + i * 0.5
                        if (level > maxLevel) break
                        val cp = floor(attack * sqrt(defense.toDouble()) * sqrt(stamina.toDouble()) * cpm.pow(2.0) / 10).toInt().coerceAtLeast(10)
                        if (cp <= cap) {
                            val hp = floor(stamina * cpm).toInt().coerceAtLeast(10)
                            val product = (attack.toDouble() * defense * hp) * cpm.pow(2.0)
                            if (expected == null || product > expected.product) {
                                expected = PvpRankCalculator.StatProduct(a, d, s, product, cp, level)
                            }
                        }
                    }
                    val actual = optimized.invoke(calculator, base, a, d, s, cap, maxLevel)
                    if (expected != actual) fail("$name $a/$d/$s cap=$cap level=$maxLevel: $expected != $actual")
                }
            }
        }
        val base = stats.getJSONObject("MEWTWO")
        assertNull(optimized.invoke(calculator, base, 15, 15, 15, 9, 51.0))
        assertNull(optimized.invoke(calculator, base, 15, 15, 15, 1500, 0.5))
    }
}
