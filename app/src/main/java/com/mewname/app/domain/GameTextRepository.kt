package com.mewname.app.domain

import android.content.Context
import java.util.Locale

enum class AppLanguage {
    EN,
    PT_BR,
    ES
}

object GameTextRepository {
    private val textCache = mutableMapOf<AppLanguage, Map<String, String>>()
    private val moveCache = mutableMapOf<AppLanguage, Map<Int, String>>()
    private val pokemonCache = mutableMapOf<AppLanguage, Map<Int, String>>()

    fun resolveLanguage(): AppLanguage {
        val language = Locale.getDefault().language.lowercase(Locale.US)
        val country = Locale.getDefault().country.uppercase(Locale.US)
        return when {
            language == "pt" -> AppLanguage.PT_BR
            language == "es" -> AppLanguage.ES
            language == "en" -> AppLanguage.EN
            language == "pt" && country == "BR" -> AppLanguage.PT_BR
            else -> AppLanguage.EN
        }
    }

    fun moveTranslations(context: Context, language: AppLanguage): Map<Int, String> {
        moveCache[language]?.let { return it }
        val translations = loadTextTable(context, language)
            .mapNotNull { (resourceId, value) ->
                val moveId = resourceId.removePrefix("move_name_").toIntOrNull()
                moveId?.let { it to value }
            }
            .toMap()
        moveCache[language] = translations
        return translations
    }

    fun pokemonTranslations(context: Context, language: AppLanguage): Map<Int, String> {
        pokemonCache[language]?.let { return it }
        val translations = loadTextTable(context, language)
            .mapNotNull { (resourceId, value) ->
                val resource = resourceId.removePrefix("pokemon_name_")
                if (!Regex("""\d{4}""").matches(resource)) return@mapNotNull null
                resource.toIntOrNull()?.let { it to value }
            }
            .toMap()
        pokemonCache[language] = translations
        return translations
    }

    private fun loadTextTable(context: Context, language: AppLanguage): Map<String, String> {
        textCache[language]?.let { return it }
        val assetPath = when (language) {
            AppLanguage.EN -> AssetPaths.TEXT_EN
            AppLanguage.PT_BR -> AssetPaths.TEXT_PT_BR
            AppLanguage.ES -> AssetPaths.TEXT_ES
        }
        val raw = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        val regex = Regex("""RESOURCE ID:\s*([^\s]+)\s*TEXT:\s*(.*?)(?=\s*RESOURCE ID:|\z)""", setOf(RegexOption.DOT_MATCHES_ALL))
        val table = buildMap {
            regex.findAll(raw).forEach { match ->
                val key = match.groupValues[1].trim()
                val value = match.groupValues[2]
                    .replace(Regex("""\s+"""), " ")
                    .trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    put(key, value)
                }
            }
        }
        textCache[language] = table
        return table
    }
}
