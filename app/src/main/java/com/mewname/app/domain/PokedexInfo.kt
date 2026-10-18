package com.mewname.app.domain

import android.content.Context
import org.json.JSONObject

internal data class PokemonSizeBounds(val label: String, val minimum: Double, val maximum: Double)

internal fun pokemonSizeBounds(info: JSONObject?): List<PokemonSizeBounds> {
    val sizes = info?.optJSONObject("sizes") ?: return emptyList()
    val keys = listOf("xxsLowerBound", "xsLowerBound", "mLowerBound", "mUpperBound", "xlUpperBound", "xxlUpperBound")
    val bounds = keys.map { sizes.optDouble(it, Double.NaN) }
    if (bounds.any { !it.isFinite() || it <= 0 } || bounds.zipWithNext().any { it.first > it.second }) return emptyList()
    return listOf("XXS", "XS", "M", "XL", "XXL").mapIndexed { i, label -> PokemonSizeBounds(label, bounds[i], bounds[i + 1]) }
}

internal object PokedexInfoCatalog {
    @Volatile private var catalog: JSONObject? = null
    @Volatile private var releases: JSONObject? = null
    fun load(context: Context, entry: PokedexCatalogEntry): JSONObject? {
        val data = catalog ?: synchronized(this) {
            catalog ?: JSONObject(context.assets.open("pokemon/pokedex_info.json").bufferedReader().use { it.readText() })
                .getJSONObject("entries").also { catalog = it }
        }
        val releaseData = releases ?: synchronized(this) {
            releases ?: JSONObject(context.assets.open("pokemon/release_status.json").bufferedReader().use { it.readText() })
                .getJSONObject("entries").also { releases = it }
        }
        val info = match(data, entry)?.let { JSONObject(it.toString()) } ?: return null
        match(releaseData, entry)?.let { status ->
            listOf("released", "shinyReleased").forEach { field ->
                if (status.has(field)) info.put(field, status.getBoolean(field))
            }
        }
        return info
    }

    internal fun match(data: JSONObject, entry: PokedexCatalogEntry): JSONObject? {
        fun identity(value: String) = pokedexIdentity(value.replace('_', ' ').replace("Hisuian", "Hisui", true)
            .replace("Galarian", "Galar", true).replace("Alolan", "Alola", true).replace("Paldean", "Paldea", true))
        val name = identity(entry.name)
        val keys = data.keys().asSequence().toList()
        // A species entry prefers its explicitly configured normal form. Unknown variants never inherit base data.
        keys.firstOrNull { identity(it) == name + "normal" }?.let { return data.getJSONObject(it) }
        keys.firstOrNull { identity(it) == name }?.let { return data.getJSONObject(it) }
        val aliases = entry.aliases.map(::identity).toSet()
        val matches = keys.filter { identity(it) in aliases }
        return matches.singleOrNull()?.let { data.getJSONObject(it) }
    }
}
