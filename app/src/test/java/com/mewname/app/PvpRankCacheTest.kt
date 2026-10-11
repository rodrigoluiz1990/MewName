package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.PvpRankCalculator
import com.mewname.app.model.PvpLeague
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class PvpRankCacheTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val species = listOf("Bulbasaur", "Ivysaur", "Venusaur", "Charmander",
        "Charmeleon", "Charizard", "Squirtle", "Wartortle", "Blastoise", "Caterpie",
        "Metapod", "Butterfree", "Weedle", "Kakuna", "Beedrill", "Pidgey", "Pidgeotto", "Pidgeot", "Rattata", "Raticate", "Spearow", "Fearow", "Ekans", "Arbok", "Pikachu", "Raichu", "Sandshrew", "Sandslash", "Nidoran Female", "Nidorina", "Nidoqueen", "Nidoran Male", "Nidorino", "Nidoking", "Clefairy", "Clefable", "Vulpix", "Ninetales")

    private fun rank(calculator: PvpRankCalculator, name: String) =
        calculator.calculateLeagueRankInfo(context, name, 0, 15, 15, PvpLeague.GREAT)
            .also { assertNotNull(name, it) }

    @Suppress("UNCHECKED_CAST")
    private fun cache(calculator: PvpRankCalculator): Map<String, Any> {
        val field = calculator.javaClass.getDeclaredField("rankTableCache").apply { isAccessible = true }
        val entries = field.get(calculator) as Map<String, Any>
        return synchronized(entries) { LinkedHashMap(entries) }
    }

    @Test fun binaryRankPreservesFirstPlaceForTiedProducts() {
        val products = listOf(100.0, 100.0, 90.0, 80.0, 80.0)
        for (value in products + listOf(110.0, 95.0, 70.0)) {
            assertEquals(products.indexOf(value) + 1,
                com.mewname.app.domain.rankInDescendingProducts(products, value))
        }
        assertEquals(0, com.mewname.app.domain.rankInDescendingProducts(emptyList(), 1.0))
    }
    @Test fun leastRecentlyUsedTableIsEvictedWhileFrequentlyUsedTableIsRetained() {
        val calculator = PvpRankCalculator()
        species.take(36).forEach { rank(calculator, it) }
        val before = cache(calculator)
        assertEquals(36, before.size)
        val firstKey = before.keys.first()
        val secondKey = before.keys.elementAt(1)
        rank(calculator, species.first())
        rank(calculator, species[36])
        val after = cache(calculator)
        assertEquals(36, after.size)
        assertSame(before[firstKey], after[firstKey])
        assertFalse(after.containsKey(secondKey))
    }

    @Test fun evictionAndRecalculationPreserveAllRankFieldsAndIvProducts() {
        val calculator = PvpRankCalculator()
        val expected = rank(calculator, species.first())
        val before = cache(calculator)
        val key = before.keys.single()
        val original = before.getValue(key)
        species.drop(1).forEach { rank(calculator, it) }
        assertFalse(cache(calculator).containsKey(key))
        assertEquals(expected, rank(calculator, species.first()))
        val rebuilt = cache(calculator).getValue(key)
        assertNotSame(original, rebuilt)
        // RankTable is a data class: this checks all 4,096 spreads and the sorted products,
        // not just the rank selected for the visible Pokemon.
        assertEquals(original, rebuilt)
        assertEquals(36, cache(calculator).size)
    }

    @Test fun concurrentReadsAndEvictionsStayBoundedAndReturnTheSameRankings() {
        val calculator = PvpRankCalculator()
        val reference = PvpRankCalculator()
        val expected = species.associateWith { rank(reference, it) }
        val executor = Executors.newFixedThreadPool(4)
        try {
            val jobs = (species + species.reversed()).map { name -> Callable {
                assertEquals(expected[name], rank(calculator, name))
                assertTrue(cache(calculator).size <= 36)
            } }
            executor.invokeAll(jobs, 30, TimeUnit.SECONDS).forEach { it.get() }
            assertEquals(36, cache(calculator).size)
        } finally {
            executor.shutdownNow()
        }
    }
}
