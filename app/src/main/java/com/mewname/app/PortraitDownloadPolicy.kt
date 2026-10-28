package com.mewname.app

import android.content.Context
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.transform.Transformation

/**
 * Raid and Max portraits missing from the cache are downloaded once. A failed download is
 * remembered and stays blank, without retrying, until the screen's explicit refresh.
 * The bubble's missing raid suggestions follow the same rule under their own keys.
 */
internal object PortraitDownloadPolicy {
    private const val PREFS = "portrait_downloads"
    private const val FAILED_KEY = "failed"
    @Volatile private var failed: Set<String>? = null

    private fun failed(context: Context): Set<String> = failed ?: synchronized(this) {
        failed ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(FAILED_KEY, emptySet()).orEmpty().toSet().also { failed = it }
    }

    private fun update(context: Context, change: (Set<String>) -> Set<String>) = synchronized(this) {
        val next = change(failed(context))
        if (next == failed) return
        failed = next
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(FAILED_KEY, next).apply()
    }

    internal fun allowsNetwork(context: Context, url: String, refreshToken: Int): Boolean =
        refreshToken > 0 || url !in failed(context)

    internal fun recordResult(context: Context, url: String, downloaded: Boolean) =
        update(context) { if (downloaded) it - url else it + url }

    fun request(context: Context, url: String?, refreshToken: Int, transformation: Transformation): ImageRequest {
        val appContext = context.applicationContext
        val remote = url != null && url.startsWith("http")
        val network = remote && allowsNetwork(appContext, url!!, refreshToken)
        return ImageRequest.Builder(context).data(url)
            .transformations(transformation)
            .networkCachePolicy(if (network) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .listener(
                onSuccess = { _, _ -> if (remote) recordResult(appContext, url!!, downloaded = true) },
                // A cache-only miss is not a new attempt; only a real download failure is remembered.
                onError = { _, _ -> if (network) recordResult(appContext, url!!, downloaded = false) }
            )
            .build()
    }
}
