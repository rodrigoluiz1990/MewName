package com.mewname.app.domain

/** Bubble scans never refresh. Downloads belong exclusively to the normal app screens. */
internal fun shouldRefreshLeekCatalog(cachedAt: Long?, bubble: Boolean, manual: Boolean,
    now: Long = System.currentTimeMillis()): Boolean = !bubble &&
    (manual || cachedAt == null || now - cachedAt > 6 * 60 * 60 * 1000L)