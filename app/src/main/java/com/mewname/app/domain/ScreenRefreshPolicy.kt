package com.mewname.app.domain

internal const val RAID_CATALOG_MAX_AGE = 60 * 60 * 1000L
internal const val BATTLE_DATA_MAX_AGE = 24 * 60 * 60 * 1000L

/** Missing, undated or clock-skewed caches must be refreshed, without discarding them. */
internal fun screenCacheExpired(fetchedAt: Long?, maxAge: Long, now: Long = System.currentTimeMillis()): Boolean =
    fetchedAt == null || fetchedAt <= 0 || fetchedAt > now || now - fetchedAt >= maxAge
