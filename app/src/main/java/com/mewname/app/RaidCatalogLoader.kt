package com.mewname.app

import android.content.Context
import android.util.Log
import com.mewname.app.domain.GameInfoRepository
import com.mewname.app.domain.RaidHistoryCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun readRaidCatalog(
    load: () -> List<RaidHistoryCategory>
): Result<List<RaidHistoryCategory>> = try {
    Result.success(withContext(Dispatchers.IO) { load() })
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Log.e("RaidPlanner", "Failed to load raid catalog", error)
    Result.failure(error)
}

internal suspend fun loadRaidCatalog(context: Context): Result<List<RaidHistoryCategory>> = readRaidCatalog {
    val catalog = GameInfoRepository.loadRaidHistory(context.applicationContext)
    val order = GameInfoRepository.loadPokemonDexOrder(context.applicationContext)
    catalog.categories.map { category ->
        category.copy(items = category.items.sortedWith(compareBy(
            { item -> order[normalizeRaidHistoryName(item.name)] ?: Int.MAX_VALUE },
            { item -> normalizeRaidSearchName(item.name) }
        )))
    }
}

private fun normalizeRaidSearchName(text: String): String =
    java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase(java.util.Locale.US).trim()

private fun normalizeRaidHistoryName(text: String): String =
    normalizeRaidSearchName(text)
        .replace(Regex("\\([^)]*\\)"), "")
        .replace(Regex("\\b(mega|primal|shadow|dynamax|gigantamax)\\b"), "")
        .replace(Regex("\\s+"), " ").trim()
/** Publish immutable snapshots so the first bosses are usable before other categories finish. */
internal suspend fun loadRaidCatalogProgressively(
    context: Context,
    onProgress: suspend (List<RaidHistoryCategory>, Int, Int) -> Unit
): Result<List<RaidHistoryCategory>> = try {
    val catalog = withContext(Dispatchers.IO) {
        GameInfoRepository.loadRaidHistory(context.applicationContext).categories
    }
    val total = catalog.sumOf { it.items.size }
    var snapshot = catalog.map { it.copy(items = emptyList()) }
    onProgress(snapshot, 0, total)
    val order = withContext(Dispatchers.IO) {
        GameInfoRepository.loadPokemonDexOrder(context.applicationContext)
    }
    var count = 0
    catalog.forEachIndexed { index, category ->
        // Normalize each name once, rather than on every comparator invocation.
        val sorted = withContext(Dispatchers.Default) {
            category.items.map { item ->
                Triple(item, order[normalizeRaidHistoryName(item.name)] ?: Int.MAX_VALUE,
                    normalizeRaidSearchName(item.name))
            }.sortedWith(compareBy({ it.second }, { it.third })).map { it.first }
        }
        for (boss in sorted) {
            kotlinx.coroutines.yield()
            snapshot = snapshot.mapIndexed { categoryIndex, current ->
                if (categoryIndex == index) current.copy(items = current.items + boss) else current
            }
            onProgress(snapshot, ++count, total)
        }
    }
    Result.success(snapshot)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Log.e("RaidPlanner", "Failed to progressively load raid catalog", error)
    Result.failure(error)
}