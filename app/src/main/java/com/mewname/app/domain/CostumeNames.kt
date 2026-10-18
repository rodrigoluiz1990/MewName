package com.mewname.app.domain

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/** Exact sprite matching: the same event code can represent different outfits by species. */
internal class CostumeNames(context: Context) {
    private val catalog = JSONObject(context.assets.open("pokemon/costume_names.json").bufferedReader().use { it.readText() })
    private val names = catalog.getJSONObject("labels")
    private val excluded = catalog.getJSONArray("excludedFiles").let { array ->
        (0 until array.length()).map { array.getString(it).lowercase(Locale.ROOT) }.toSet()
    }

    private fun file(url: String) = url.substringBefore('?').substringAfterLast('/').lowercase(Locale.ROOT)

    fun isCostume(url: String): Boolean = file(url) !in excluded

    fun label(url: String, fallback: String, language: AppLanguage): String {
        val key = when (language) {
            AppLanguage.PT_BR -> "pt"
            AppLanguage.EN -> "en"
            AppLanguage.ES -> "es"
        }
        return names.optJSONObject(file(url))?.optString(key)?.takeIf { it.isNotBlank() }
            ?: fallback.replace(Regex("(?i)\\s*noevolve\\b"), "").trim()
    }
}
