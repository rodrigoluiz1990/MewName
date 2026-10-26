package com.mewname.app.domain

import android.content.Context
import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale

interface PokemonMoveDataSource {
    fun loadMoveCatalog(): List<MoveCatalogEntry>
    fun readCurrentMoves(): String
    fun loadLegacyMoves(): List<LegacyMoveCatalogEntry> = emptyList()
    fun loadMoveTranslations(language: AppLanguage): Map<String, String> = emptyMap()
}

private class AssetPokemonMoveDataSource(context: Context) : PokemonMoveDataSource {
    private val appContext = context.applicationContext

    override fun loadMoveCatalog(): List<MoveCatalogEntry> {
        return GameInfoRepository.loadMoveCatalog(appContext)
    }

    override fun loadLegacyMoves(): List<LegacyMoveCatalogEntry> =
        GameCatalogRepository.loadLegacyMoveCatalog(appContext)

    override fun loadMoveTranslations(language: AppLanguage): Map<String, String> {
        val root = org.json.JSONObject(appContext.assets.open("catalogs/move_labels.json").bufferedReader().use { it.readText() })
        return buildMap {
            root.keys().forEach { name ->
                val value = root.getJSONObject(name).optString(language.name)
                put(name, value.takeUnless { it.isBlank() || it == "null" } ?: name)
            }
        }
    }
    override fun readCurrentMoves(): String {
        return appContext.assets.open(AssetPaths.POKEMON_CURRENT_MOVES)
            .bufferedReader()
            .use { it.readText() }
    }

    override fun equals(other: Any?): Boolean {
        return other is AssetPokemonMoveDataSource && other.appContext === appContext
    }

    override fun hashCode(): Int = System.identityHashCode(appContext)
}

data class PokemonMove(
    val name: String,
    val localizedName: String,
    val legacy: Boolean,
    val rating: PokemonMoveRating? = null
)

data class PokemonMoveSet(
    val fastMoves: List<PokemonMove>,
    val chargedMoves: List<PokemonMove>
)

data class PokemonMoveRating(
    val rank: Int,
    val total: Int
) {
    val label: String
        get() = "#$rank/$total"
}

/** Loads and caches a Pokemon's current moves independently from the review UI. */
object PokemonMoveRepository {
    private data class MoveIndex(
        val exact: Map<String, PokemonMoveSet>,
        val canonical: List<Pair<String, PokemonMoveSet>>
    ) {
        fun find(keys: List<String>): PokemonMoveSet {
            keys.forEach { key -> exact[key]?.let { return it } }
            return canonical.firstOrNull { (key, _) -> keys.any { it.startsWith("$key ") } }?.second
                ?: PokemonMoveSet(emptyList(), emptyList())
        }
    }

    private class LanguageCache(source: PokemonMoveDataSource, language: AppLanguage) {
        val translations by lazy { source.loadMoveTranslations(language).mapKeys { normalizeMoveKey(it.key) } }
        val moves = java.util.concurrent.ConcurrentHashMap<String, PokemonMoveSet>()
    }

    private class SourceCache(val source: PokemonMoveDataSource) {
        val index by lazy { loadIndex(source) }
        val stats by lazy { source.loadMoveCatalog().associateBy { normalizeMoveKey(it.name) } }
        val languages = java.util.concurrent.ConcurrentHashMap<AppLanguage, LanguageCache>()
        fun language(language: AppLanguage): LanguageCache = languages.computeIfAbsent(language) { LanguageCache(source, it) }
    }

    private val sources = mutableMapOf<PokemonMoveDataSource, SourceCache>()
    private fun sourceCache(source: PokemonMoveDataSource): SourceCache = synchronized(sources) {
        sources.getOrPut(source) { SourceCache(source) }
    }

    fun clearCache(language: AppLanguage? = null) {
        synchronized(sources) {
            if (language == null) sources.clear()
            else sources.values.forEach { it.languages.remove(language) }
        }
    }

    private fun loadIndex(source: PokemonMoveDataSource): MoveIndex {
        val array = JSONArray(source.readCurrentMoves())
        val legacy = source.loadLegacyMoves().groupBy { normalizeMoveKey(it.pokemon) }
            .mapValues { (_, entries) -> entries.flatMap { it.searchTerms }.map(::normalizeMoveKey).toSet() }
        val exact = linkedMapOf<String, PokemonMoveSet>()
        val canonical = mutableListOf<Pair<String, PokemonMoveSet>>()
        for (i in 0 until array.length()) {
            if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
            val item = array.getJSONObject(i)
            val name = normalizeMoveKey(item.getString("name"))
            val legacyMoves = legacy[name].orEmpty()
            val moves = PokemonMoveSet(parseRawMoves(item.optJSONArray("fastMoves"), legacyMoves),
                parseRawMoves(item.optJSONArray("chargedMoves"), legacyMoves))
            exact.putIfAbsent(name, moves)
            val aliases = item.optJSONArray("aliases")
            for (j in 0 until (aliases?.length() ?: 0)) exact.putIfAbsent(normalizeMoveKey(aliases!!.getString(j)), moves)
            canonical += name to moves
        }
        return MoveIndex(exact, canonical.sortedByDescending { it.first.length })
    }

    fun load(context: Context, pokemonName: String, language: AppLanguage): PokemonMoveSet =
        load(pokemonName, language, AssetPokemonMoveDataSource(context))

    fun load(pokemonName: String, language: AppLanguage, dataSource: PokemonMoveDataSource): PokemonMoveSet {
        val keys = lookupKeysFor(pokemonName)
        if (keys.isEmpty()) return PokemonMoveSet(emptyList(), emptyList())
        val source = sourceCache(dataSource)
        val localized = source.language(language)
        localized.moves[keys.first()]?.let { return it }
        val raw = source.index.find(keys)
        val translations = localized.translations
        fun translate(moves: List<PokemonMove>) = moves.map {
            it.copy(localizedName = translations[normalizeMoveKey(it.name)] ?: it.name)
        }
        val result = PokemonMoveSet(translate(raw.fastMoves), translate(raw.chargedMoves))
        return localized.moves.putIfAbsent(keys.first(), result) ?: result
    }

    /** Enriches visible moves after raw options are shown. */
    fun enrich(context: Context, moves: PokemonMoveSet, language: AppLanguage): PokemonMoveSet {
        return enrich(moves, language, AssetPokemonMoveDataSource(context))
    }

    internal fun enrich(moves: PokemonMoveSet, language: AppLanguage, dataSource: PokemonMoveDataSource): PokemonMoveSet {
        val source = sourceCache(dataSource)
        val stats = source.stats
        val names = source.language(language).translations +
            stats.values.associate { normalizeMoveKey(it.name) to it.localizedName(language) }
        return PokemonMoveSet(
            fastMoves = enrichMoves(moves.fastMoves, MoveCategory.FAST, names, stats),
            chargedMoves = enrichMoves(moves.chargedMoves, MoveCategory.CHARGED, names, stats)
        )
    }

    private fun parseRawMoves(array: JSONArray?, legacyMoves: Set<String>): List<PokemonMove> {
        array ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                if (name.isNotBlank()) add(PokemonMove(name, name, item.optBoolean("legacy", false) || normalizeMoveKey(name) in legacyMoves))
            }
        }
    }

    private fun enrichMoves(moves: List<PokemonMove>, category: MoveCategory, names: Map<String, String>, stats: Map<String, MoveCatalogEntry>): List<PokemonMove> {
        val localized = moves.map { move ->
            move.copy(localizedName = names[normalizeMoveKey(move.name)] ?: move.name)
        }
        val ranked = localized.map { move -> move to score(stats[normalizeMoveKey(move.name)], category) }
            .sortedByDescending { it.second }
        val ratings = ranked.withIndex().associate { (index, entry) ->
            entry.first.name to PokemonMoveRating(index + 1, ranked.size.coerceAtLeast(1))
        }
        return localized.map { move -> move.copy(rating = ratings[move.name]) }
    }

    private fun lookupKeysFor(pokemonName: String): List<String> {
        val trimmed = pokemonDisplayName(pokemonName).trim()
        if (trimmed.isBlank()) return emptyList()
        val keys = linkedSetOf(normalizeMoveKey(trimmed), normalizeMoveKey(pokemonName))
        if (Regex("""\((Alola|Galar|Hisui|Paldea)\)$""").containsMatchIn(trimmed)) keys += normalizeMoveKey(trimmed.substringBefore(" ("))
        listOf("Alolan ", "Galarian ", "Hisuian ", "Paldean ").forEach { prefix ->
            if (trimmed.startsWith(prefix, ignoreCase = true)) {
                keys += normalizeMoveKey(trimmed.substring(prefix.length).trim())
            }
        }
        return keys.filter { it.isNotBlank() }
    }

    private fun score(move: MoveCatalogEntry?, category: MoveCategory): Double {
        move ?: return Double.NEGATIVE_INFINITY
        return when (category) {
            MoveCategory.FAST -> {
                val power = move.pvpPower ?: move.power ?: 0
                val energyGain = kotlin.math.abs(move.pvpEnergyDelta ?: move.energyDelta ?: 0)
                val turns = (move.pvpTurnDuration ?: 1).coerceAtLeast(1)
                ((power * 1.15) + (energyGain * 1.8)) / turns
            }
            MoveCategory.CHARGED -> {
                val power = move.pvpPower ?: move.power ?: 0
                val cost = kotlin.math.abs(move.pvpEnergyDelta ?: move.energyDelta ?: 0).coerceAtLeast(1)
                power.toDouble() / cost
            }
        }
    }

    private fun normalizeMoveKey(text: String): String {
        return Normalizer.normalize(pokemonDisplayName(text).trim().replace("♀", "F").replace("♂", "M"), Normalizer.Form.NFD)
            .replace(COMBINING_DIACRITICS, "")
            .replace(NON_ALPHANUMERIC_RUNS, " ")
            .trim()
            .uppercase(Locale.US)
    }
}