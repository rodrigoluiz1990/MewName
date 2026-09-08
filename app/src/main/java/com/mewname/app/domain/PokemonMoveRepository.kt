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
    private val cachedMoves = mutableMapOf<PokemonMoveDataSource, MutableMap<AppLanguage, MutableMap<String, PokemonMoveSet>>>()

    fun clearCache(language: AppLanguage? = null) {
        synchronized(this) {
            if (language == null) {
                cachedMoves.clear()
            } else {
                cachedMoves.values.forEach { it.remove(language) }
                cachedMoves.entries.removeIf { it.value.isEmpty() }
            }
        }
    }

    fun load(context: Context, pokemonName: String, language: AppLanguage): PokemonMoveSet {
        return load(pokemonName, language, AssetPokemonMoveDataSource(context))
    }

    fun load(
        pokemonName: String,
        language: AppLanguage,
        dataSource: PokemonMoveDataSource
    ): PokemonMoveSet {
        val lookupKeys = lookupKeysFor(pokemonName)
        if (lookupKeys.isEmpty()) return PokemonMoveSet(emptyList(), emptyList())
        synchronized(this) {
            val languageCache = cachedMoves
                .getOrPut(dataSource) { mutableMapOf() }
                .getOrPut(language) { mutableMapOf() }
            languageCache[lookupKeys.first()]?.let { return it }

            // Only parse and rank the requested Pokemon instead of building every move set in the 1.2 MB asset.
            val item = findMoveSetItem(dataSource.readCurrentMoves(), lookupKeys)
                ?: return PokemonMoveSet(emptyList(), emptyList())
            val speciesKey = normalizeMoveKey(item.getString("name"))
            val legacyMoves = dataSource.loadLegacyMoves()
                .filter { normalizeMoveKey(it.pokemon) == speciesKey }
                .flatMap { it.searchTerms }
                .map(::normalizeMoveKey)
                .toSet()
            val loaded = PokemonMoveSet(
                fastMoves = parseRawMoves(item.optJSONArray("fastMoves"), legacyMoves),
                chargedMoves = parseRawMoves(item.optJSONArray("chargedMoves"), legacyMoves)
            )
            // Opening the selector must never depend on loading/ranking the full stat catalog.
            val translations = dataSource.loadMoveTranslations(language).mapKeys { normalizeMoveKey(it.key) }
            fun translated(moves: List<PokemonMove>) = moves.map { move ->
                move.copy(localizedName = translations[normalizeMoveKey(move.name)] ?: move.name)
            }
            val localized = PokemonMoveSet(translated(loaded.fastMoves), translated(loaded.chargedMoves))
            languageCache[lookupKeys.first()] = localized
            return localized
        }
    }

    private fun findMoveSetItem(rawMoves: String, lookupKeys: List<String>): org.json.JSONObject? {
        val jsonArray = JSONArray(rawMoves)
        val entries = List(jsonArray.length()) { jsonArray.getJSONObject(it) }
        // Resolve every exact form/alias before falling back to a base species.
        for (lookupKey in lookupKeys) {
            entries.firstOrNull { item ->
                val aliases = item.optJSONArray("aliases")
                normalizeMoveKey(item.optString("name")) == lookupKey ||
                    (0 until (aliases?.length() ?: 0)).any { normalizeMoveKey(aliases!!.getString(it)) == lookupKey }
            }?.let { return it }
        }
        return entries.filter { item ->
            val key = normalizeMoveKey(item.optString("name"))
            lookupKeys.any { it.startsWith("$key ") }
        }.maxByOrNull { normalizeMoveKey(it.optString("name")).length }
    }
    /** Enriches visible moves after raw options are shown. */
    fun enrich(context: Context, moves: PokemonMoveSet, language: AppLanguage): PokemonMoveSet {
        return enrich(moves, language, AssetPokemonMoveDataSource(context))
    }

    internal fun enrich(moves: PokemonMoveSet, language: AppLanguage, dataSource: PokemonMoveDataSource): PokemonMoveSet {
        val entries = dataSource.loadMoveCatalog()
        val names = dataSource.loadMoveTranslations(language)
            .mapKeys { normalizeMoveKey(it.key) } +
            entries.associate { normalizeMoveKey(it.name) to it.localizedName(language) }
        val stats = entries.associateBy { normalizeMoveKey(it.name) }
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
        val array = JSONArray()
        moves.forEach { move -> array.put(org.json.JSONObject().put("name", move.name).put("legacy", move.legacy)) }
        return parseMoves(array, category, names, stats)
    }
    private fun parseMoves(
        array: JSONArray?,
        category: MoveCategory,
        localizedNames: Map<String, String>,
        moveStats: Map<String, MoveCatalogEntry>
    ): List<PokemonMove> {
        array ?: return emptyList()
        val moves = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                if (name.isBlank()) continue
                add(PokemonMove(name, localizedNames[normalizeMoveKey(name)] ?: name, item.optBoolean("legacy", false)))
            }
        }
        val ranked = moves.map { move -> move to score(moveStats[normalizeMoveKey(move.name)], category) }
            .sortedByDescending { it.second }
        val ratings = ranked.withIndex().associate { (index, entry) ->
            entry.first.name to PokemonMoveRating(index + 1, ranked.size.coerceAtLeast(1))
        }
        return moves.map { move -> move.copy(rating = ratings[move.name]) }
    }

    private fun lookupKeysFor(pokemonName: String): List<String> {
        val trimmed = pokemonName.trim()
        if (trimmed.isBlank()) return emptyList()
        val keys = linkedSetOf(normalizeMoveKey(trimmed))
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
        return Normalizer.normalize(text.trim().replace("♀", "F").replace("♂", "M"), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^A-Za-z0-9]+"), " ")
            .trim()
            .uppercase(Locale.US)
    }
}