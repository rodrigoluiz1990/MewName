package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RaidCatalogLoaderTest {
    @Test fun readsAssetsOffTheCallingThread() = runBlocking {
        val caller = Thread.currentThread()
        var worker: Thread? = null
        val result = readRaidCatalog { worker = Thread.currentThread(); emptyList() }
        assertTrue(result.isSuccess)
        assertNotSame(caller, worker)
    }

    @Test fun loadFailureCanBeRetried() = runBlocking {
        val error = java.io.IOException("asset unavailable")
        val failed = readRaidCatalog { throw error }
        assertTrue(failed.exceptionOrNull() is java.io.IOException)
        assertEquals(error.message, failed.exceptionOrNull()?.message)
        assertTrue(readRaidCatalog { emptyList() }.isSuccess)
    }

    @Test fun cancellationIsNotPresentedAsAReadingError() = runBlocking {
        try {
            readRaidCatalog { throw CancellationException("left screen") }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            // Leaving the screen cancels work without showing an error or updating its state.
        }
    }

    @Test fun actualCatalogKeepsAllCategoriesAndBosses() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expected = com.mewname.app.domain.GameInfoRepository.loadRaidHistory(context)
        val loaded = loadRaidCatalog(context).getOrThrow()
        assertTrue(loaded.isNotEmpty())
        assertEquals(expected.categories.map { it.id }, loaded.map { it.id })
        expected.categories.zip(loaded).forEach { (before, after) ->
            assertEquals(before.items.size, after.items.size)
            assertEquals(before.items.toSet(), after.items.toSet())
        }
    }
    @Test fun progressiveLoadPublishesOneBossAtATimeWithoutChangingEarlierSnapshots() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expected = loadRaidCatalog(context).getOrThrow()
        val total = expected.sumOf { it.items.size }
        val snapshots = mutableListOf<List<com.mewname.app.domain.RaidHistoryCategory>>()
        val result = loadRaidCatalogProgressively(context) { categories, count, reportedTotal ->
            assertEquals(total, reportedTotal)
            assertEquals(snapshots.size, count)
            assertEquals(count, categories.sumOf { it.items.size })
            assertEquals(expected.map { it.id }, categories.map { it.id })
            snapshots += categories
        }.getOrThrow()
        assertEquals(total + 1, snapshots.size)
        snapshots.forEachIndexed { count, categories ->
            assertEquals(count, categories.sumOf { it.items.size })
        }
        assertEquals(expected, result)
    }

    @Test fun leavingDuringProgressStopsFurtherUpdates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var updates = 0
        try {
            loadRaidCatalogProgressively(context) { _, count, _ ->
                updates++
                if (count == 1) throw CancellationException("left screen")
            }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(2, updates)
        }
    }
}