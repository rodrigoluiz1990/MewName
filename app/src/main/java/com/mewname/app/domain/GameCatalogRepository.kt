package com.mewname.app.domain

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

data class LegacyMoveCatalogEntry(
    val pokemon: String,
    val moves: List<String>,
    val searchTerms: List<String>
)

data class AdventureEffectCatalogEntry(
    val pokemon: String,
    val aliases: List<String>,
    val move: String,
    val movePt: String,
    val moveAliases: List<String>,
    val effectName: String,
    val effectNamePt: String,
    val effectAliases: List<String>,
    val description: String
) {
    val normalizedPokemonAliases: List<String> = (aliases + pokemon)
        .map(::normalizeCatalogText)
        .distinct()
    val normalizedMoveAliases: List<String> = (moveAliases + move)
        .map(::normalizeCatalogText)
        .distinct()
    val normalizedEffectAliases: List<String> = (effectAliases + effectName)
        .map(::normalizeCatalogText)
        .distinct()
    val displayPokemonPt: String = formatAdventurePokemonDisplayName(pokemon)
}

object GameCatalogRepository {
    @Volatile
    private var legacyMoveCatalogCache: List<LegacyMoveCatalogEntry>? = null

    @Volatile
    private var adventureEffectCatalogCache: List<AdventureEffectCatalogEntry>? = null

    @Volatile
    private var gameCatalogRootCache: JSONObject? = null

    fun loadLegacyMoveCatalog(context: Context): List<LegacyMoveCatalogEntry> {
        legacyMoveCatalogCache?.let { return it }
        synchronized(this) {
            legacyMoveCatalogCache?.let { return it }
            val loaded = runCatching {
                val jsonObject = loadGameCatalogRoot(context).getJSONObject("legacyMoves")
                buildList {
                    jsonObject.keys().forEach { pokemon ->
                        val array = jsonObject.getJSONArray(pokemon)
                        val rawMoves = buildList(array.length()) {
                            for (index in 0 until array.length()) {
                                val value = repairMojibake(array.optString(index)).trim()
                                if (value.isNotEmpty()) add(value)
                            }
                        }
                        val groupedMoves = rawMoves.chunked(3)
                        val displayMoves = groupedMoves
                            .mapNotNull { chunk ->
                                chunk.getOrNull(1)?.takeIf { it.isNotBlank() }
                                    ?: chunk.firstOrNull { it.isNotBlank() }
                            }
                            .distinct()
                        val searchTerms = buildList {
                            add(normalizeCatalogText(pokemon))
                            groupedMoves.forEach { chunk ->
                                chunk.filter { it.isNotBlank() }
                                    .mapTo(this) { normalizeCatalogText(it) }
                            }
                        }.distinct()
                        add(
                            LegacyMoveCatalogEntry(
                                pokemon = repairMojibake(pokemon),
                                moves = displayMoves,
                                searchTerms = searchTerms
                            )
                        )
                    }
                }.sortedBy { it.pokemon }
            }.getOrElse { emptyList() }
            legacyMoveCatalogCache = loaded
            return loaded
        }
    }

    fun loadAdventureEffectCatalog(context: Context): List<AdventureEffectCatalogEntry> {
        adventureEffectCatalogCache?.let { return it }
        synchronized(this) {
            adventureEffectCatalogCache?.let { return it }
            val loaded = runCatching {
                val jsonArray = loadGameCatalogRoot(context).getJSONArray("adventureEffects")
                buildList {
                    for (index in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(index)
                        add(
                            AdventureEffectCatalogEntry(
                                pokemon = item.getString("pokemon"),
                                aliases = item.optJSONArrayStrings("aliases"),
                                move = item.getString("move"),
                                movePt = item.optString("movePt").ifBlank { item.getString("move") },
                                moveAliases = item.optJSONArrayStrings("moveAliases"),
                                effectName = item.optString("effectName").ifBlank { item.getString("move") },
                                effectNamePt = item.optString("effectNamePt").ifBlank { item.optString("movePt").ifBlank { item.getString("move") } },
                                effectAliases = item.optJSONArrayStrings("effectAliases"),
                                description = item.optString("description")
                            )
                        )
                    }
                }
            }.getOrElse { emptyList() }
            adventureEffectCatalogCache = loaded
            return loaded
        }
    }

    private fun loadGameCatalogRoot(context: Context): JSONObject {
        gameCatalogRootCache?.let { return it }
        synchronized(this) {
            gameCatalogRootCache?.let { return it }
            val loaded = JSONObject(
                context.assets.open(AssetPaths.GAME_CATALOGS).bufferedReader().use { it.readText() }
            )
            gameCatalogRootCache = loaded
            return loaded
        }
    }

    private fun JSONObject.optJSONArrayStrings(key: String): List<String> {
        val jsonArray = optJSONArray(key) ?: return emptyList()
        return buildList(jsonArray.length()) {
            for (index in 0 until jsonArray.length()) {
                val value = jsonArray.optString(index).trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    }
}

private fun repairMojibake(text: String): String {
    if (!text.any { it == '\u00C3' || it == '\u00C2' || it == '\u00E2' }) return text
    return runCatching {
        String(text.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
    }.getOrDefault(text)
}

private fun normalizeCatalogText(text: String): String {
    return Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .uppercase(Locale.US)
        .trim()
}

private fun formatAdventurePokemonDisplayName(pokemon: String): String {
    return when {
        pokemon.startsWith("Origin Forme ", ignoreCase = true) -> {
            pokemon.removePrefix("Origin Forme ").trim() + " (Origem)"
        }
        pokemon.startsWith("Black ", ignoreCase = true) -> {
            pokemon.removePrefix("Black ").trim() + " (Black)"
        }
        pokemon.startsWith("White ", ignoreCase = true) -> {
            pokemon.removePrefix("White ").trim() + " (White)"
        }
        pokemon.startsWith("Crowned Sword ", ignoreCase = true) -> {
            pokemon.removePrefix("Crowned Sword ").trim() + " (Coroado)"
        }
        pokemon.startsWith("Crowned Shield ", ignoreCase = true) -> {
            pokemon.removePrefix("Crowned Shield ").trim() + " (Coroado)"
        }
        pokemon.startsWith("Dusk Mane ", ignoreCase = true) -> {
            pokemon.removePrefix("Dusk Mane ").trim() + " (Dusk Mane)"
        }
        pokemon.startsWith("Dawn Wings ", ignoreCase = true) -> {
            pokemon.removePrefix("Dawn Wings ").trim() + " (Dawn Wings)"
        }
        else -> pokemon
    }
}
