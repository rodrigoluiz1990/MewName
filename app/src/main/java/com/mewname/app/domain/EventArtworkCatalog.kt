package com.mewname.app.domain

import android.content.Context
import org.json.JSONObject
import java.net.URI

/** Use only exact source/image matches; dates or similar titles are not enough. */
internal class EventArtworkCatalog(context: Context) {
    private val rows by lazy {
        runCatching {
            val available = context.assets.list("events").orEmpty().toSet()
            val data = context.assets.open("events/sources.json").bufferedReader().use { JSONObject(it.readText()).getJSONArray("events") }
            (0 until data.length()).map { data.getJSONObject(it) }.filter { it.getString("file") in available }
        }.getOrDefault(emptyList())
    }
    fun resolve(image: String?, link: String?): String? {
        fun path(url: String?) = runCatching { URI(url.orEmpty()).path.trimEnd('/') }.getOrDefault("")
        val linkSlug = path(link).substringAfterLast('/')
        val match = rows.firstOrNull {
            image == it.optString("imageUrl") || (linkSlug.isNotBlank() && linkSlug == it.getString("id"))
        }
        return match?.let { "file:///android_asset/events/" + it.getString("file") } ?: image
    }
}
