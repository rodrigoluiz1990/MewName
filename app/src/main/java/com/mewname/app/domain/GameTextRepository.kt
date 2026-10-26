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
    private val moveCache = java.util.concurrent.ConcurrentHashMap<AppLanguage, Map<Int, String>>()
    private val pokemonCache = java.util.concurrent.ConcurrentHashMap<AppLanguage, Map<Int, String>>()
    private val rocketQuoteCache = java.util.concurrent.ConcurrentHashMap<AppLanguage, List<RocketQuote>>()
    private val pokemonResourceId = Regex("""\d{4}""")

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
            rocketQuoteCache.clear()
        } else {
            textCache.remove(language)
            researchTemplateCache.remove(language)
            researchIndexes.remove(language)
            moveCache.remove(language)
            pokemonCache.remove(language)
            // Quotes pair EN with each language, so an EN reload invalidates all of them.
            if (language == AppLanguage.EN) rocketQuoteCache.clear() else rocketQuoteCache.remove(language)
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
                if (!pokemonResourceId.matches(resource)) return@mapNotNull null
                resource.toIntOrNull()?.let { it to value }
            }
            .toMap()
        pokemonCache[language] = translations
        return translations
    }

    // Screen routing checks these on every Pokemon read. They are a few hundred phrases out of ~12 MB of
    // text tables, so they are also stored per app version and later starts skip parsing the tables.
    fun rocketQuotes(context: Context, language: AppLanguage): List<RocketQuote> = rocketQuoteCache.getOrPut(language) {
        readStoredRocketQuotes(context, language) ?: run {
            val english = loadTextTable(context, AppLanguage.EN)
            val localized = loadTextTable(context, language)
            english.filterKeys { it.startsWith("combat_") && it.contains("quote") }.mapNotNull { (id, text) ->
                localized[id]?.takeIf { it.isNotBlank() }?.let { RocketQuote(text, it, when {
                    id.contains("__female_speaker") -> "Female"
                    id.contains("__male_speaker") -> "Male"
                    else -> null
                }) }
            }.also { storeRocketQuotes(context, language, it) }
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
        // Each table is ~4 MB: a caller arriving during a load waits for it instead of parsing it again.
        synchronized(textLoadLocks.getValue(language)) {
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

    private val textLoadLocks = AppLanguage.entries.associateWith { Any() }

    private fun storedRocketQuotesFile(context: Context, language: AppLanguage): java.io.File =
        java.io.File(context.filesDir, "$ROCKET_QUOTE_STORE_DIR/${com.mewname.app.BuildConfig.VERSION_CODE}/${language.name}.json")

    private fun readStoredRocketQuotes(context: Context, language: AppLanguage): List<RocketQuote>? = runCatching {
        val file = storedRocketQuotesFile(context, language)
        if (!file.isFile) return null
        val array = org.json.JSONArray(file.readText())
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            RocketQuote(item.getString("english"), item.getString("localized"),
                item.optString("speaker").takeIf { item.has("speaker") })
        }
    }.getOrNull()

    private fun storeRocketQuotes(context: Context, language: AppLanguage, quotes: List<RocketQuote>) {
        if (quotes.isEmpty()) return
        runCatching {
            val file = storedRocketQuotesFile(context, language)
            file.parentFile?.parentFile?.listFiles()?.filter { it != file.parentFile }?.forEach { it.deleteRecursively() }
            file.parentFile?.mkdirs()
            val array = org.json.JSONArray()
            quotes.forEach { quote ->
                array.put(org.json.JSONObject().put("english", quote.english).put("localized", quote.localized)
                    .apply { quote.speaker?.let { put("speaker", it) } })
            }
            val temp = java.io.File(file.path + ".tmp")
            temp.writeText(array.toString())
            check(temp.renameTo(file))
        }
    }

    private const val ROCKET_QUOTE_STORE_DIR = "rocket-quotes"
}
