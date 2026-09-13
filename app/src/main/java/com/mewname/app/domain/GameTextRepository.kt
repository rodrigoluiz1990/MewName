package com.mewname.app.domain

import android.content.Context
import java.util.Locale

enum class AppLanguage {
    EN,
    PT_BR,
    ES
}

object GameTextRepository {
    private val textCache = java.util.concurrent.ConcurrentHashMap<AppLanguage, Map<String, String>>()
    private val researchTemplateCache = java.util.concurrent.ConcurrentHashMap<AppLanguage, List<ResearchTemplate>>()
    private val moveCache = mutableMapOf<AppLanguage, Map<Int, String>>()
    private val pokemonCache = mutableMapOf<AppLanguage, Map<Int, String>>()

    private val researchIndexes = java.util.concurrent.ConcurrentHashMap<AppLanguage, ResearchTranslationIndex>()
    @Synchronized
    internal fun researchIndex(context: Context, language: AppLanguage): ResearchTranslationIndex =
        researchIndexes.getOrPut(language) { ResearchTranslationIndex(researchTemplates(context, language)) }

    fun clearCache(language: AppLanguage? = null) {
        if (language == null) {
            textCache.clear()
            researchTemplateCache.clear()
            researchIndexes.clear()
            moveCache.clear()
            pokemonCache.clear()
        } else {
            textCache.remove(language)
            researchTemplateCache.remove(language)
            researchIndexes.remove(language)
            moveCache.remove(language)
            pokemonCache.remove(language)
        }
    }

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

    fun rocketQuotes(context: Context, language: AppLanguage): List<RocketQuote> {
        val english = loadTextTable(context, AppLanguage.EN)
        val localized = loadTextTable(context, language)
        return english.filterKeys { it.startsWith("combat_") && it.contains("quote") }.mapNotNull { (id, text) ->
            localized[id]?.takeIf { it.isNotBlank() }?.let { RocketQuote(text, it, when {
                id.contains("__female_speaker") -> "Female"
                id.contains("__male_speaker") -> "Male"
                else -> null
            }) }
        }
    }

    fun researchTemplates(context: Context, language: AppLanguage): List<ResearchTemplate> {
        researchTemplateCache[language]?.let { return it }
        val english = loadTextTable(context, AppLanguage.EN)
        val localized = loadTextTable(context, language)
        val terms = english.filterKeys { it.startsWith("pokemon_name_") || it.startsWith("pokemon_type_") }
            .mapNotNull { (id, text) -> localized[id]?.let { catalogText(text) to it } }.toMap()
        val bundled = english.filter { (id, text) -> id.startsWith("quest_") && text.length < 180 &&
            !id.contains("dialogue") && !id.contains("title") }.mapNotNull { (id, text) ->
            localized[id]?.let { ResearchTemplate(text, it, terms) }
        }.distinctBy { it.english to it.localized }
        // Local translations for current source phrasing absent from bundled quest resources.
        fun tr(pt: String, en: String, es: String) = when (language) {
            AppLanguage.PT_BR -> pt; AppLanguage.EN -> en; AppLanguage.ES -> es
        }
        val templates = bundled + listOf(
            ResearchTemplate("Catch {0} {1}", tr("Capturar {0} {1}.", "Catch {0} {1}", "Captura {0} {1}"), terms),
            ResearchTemplate("Catch {0} {1}- or {2}-type Pokémon",
                tr("Capturar {0} Pokémon de tipo {1} ou {2}.", "Catch {0} {1}- or {2}-type Pokémon",
                    "Captura {0} Pokémon de tipo {1} o {2}"), terms),
            ResearchTemplate("Win a Max Battle", tr("Vencer 1 Batalha Max.", "Win a Max Battle", "Gana un Combate Max"), terms)
        )
        researchTemplateCache[language] = templates
        return templates
    }
    fun researchDisplayTerms(context: Context, language: AppLanguage): Map<String, String> {
        val english = loadTextTable(context, AppLanguage.EN)
        val localized = loadTextTable(context, language)
        return english.filterKeys { (it.startsWith("item_") && it.endsWith("_name")) ||
            it.startsWith("pokemon_name_") || it == "mega_energy" || it == "pokemon_info_stardust_label" }
            .mapNotNull { (id, value) -> localized[id]?.let { catalogText(value) to it } }.toMap()
    }
    private fun loadTextTable(context: Context, language: AppLanguage): Map<String, String> {
        textCache[language]?.let { return it }
        val assetPath = when (language) {
            AppLanguage.EN -> AssetPaths.TEXT_EN
            AppLanguage.PT_BR -> AssetPaths.TEXT_PT_BR
            AppLanguage.ES -> AssetPaths.TEXT_ES
        }
        val table = context.assets.open(assetPath).bufferedReader().use { GameTextFileReader.read(it) }
        textCache[language] = table
        return table
    }
}
