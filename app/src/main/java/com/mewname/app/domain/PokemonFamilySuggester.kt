package com.mewname.app.domain

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale

class PokemonFamilySuggester {
    @Volatile
    private var orderedNames: List<String>? = null
    @Volatile
    private var familyMap: Map<String, List<String>>? = null

    fun suggestionsFor(context: Context, candyFamilyName: String?, currentName: String?): List<String> {
        val mappedFamily = listOfNotNull(candyFamilyName, currentName)
            .map(::normalize)
            .firstNotNullOfOrNull { loadFamilyMap(context)[it] }
        if (mappedFamily != null) return mappedFamily

        val familyName = candyFamilyName?.trim().takeUnless { it.isNullOrBlank() } ?: return listOfNotNull(currentName)
        val names = loadOrderedNames(context)
        val familyIndex = names.indexOfFirst { normalize(it) == normalize(familyName) }
        if (familyIndex == -1) return listOfNotNull(currentName).distinct()

        val suggestions = mutableListOf<String>()
        for (offset in -2..2) {
            val candidate = names.getOrNull(familyIndex + offset) ?: continue
            suggestions += candidate
        }

        currentName?.let { current ->
            if (suggestions.none { normalize(it) == normalize(current) }) {
                suggestions += current
            }
        }
        return suggestions
            .distinct()
            .filter { candidate ->
                candidate == familyName || currentName != null && normalize(candidate) == normalize(currentName) || suggestions.indexOf(candidate) in 0..4
            }
    }

    fun familyMembersFor(context: Context, candyFamilyName: String?, currentName: String?): List<String> {
        val mappedFamily = listOfNotNull(candyFamilyName, currentName)
            .map(::normalize)
            .firstNotNullOfOrNull { loadFamilyMap(context)[it] }
        return mappedFamily
            ?: listOfNotNull(currentName?.takeIf { it.isNotBlank() }, candyFamilyName?.takeIf { it.isNotBlank() })
                .distinct()
    }

    private fun loadFamilyMap(context: Context): Map<String, List<String>> {
        familyMap?.let { return it }
        synchronized(this) {
            familyMap?.let { return it }
            val loaded = runCatching {
                val jsonString = context.assets.open("pokemon_families.json").bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(jsonString)
                buildMap {
                    jsonObject.keys().forEach { key ->
                        val array = jsonObject.getJSONArray(key)
                        put(
                            normalize(key),
                            buildList(array.length()) {
                                for (index in 0 until array.length()) {
                                    add(array.getString(index))
                                }
                            }
                        )
                    }
                }
            }.getOrElse { emptyMap() }
            familyMap = loaded
            return loaded
        }
    }

    private fun loadOrderedNames(context: Context): List<String> {
        orderedNames?.let { return it }
        synchronized(this) {
            orderedNames?.let { return it }

            val fromJson = runCatching {
                val jsonString = context.assets.open("pokemon_names.json").bufferedReader().use { it.readText() }
                val array = JSONArray(jsonString)
                buildList(array.length()) {
                    for (index in 0 until array.length()) {
                        add(array.getJSONObject(index).getString("name"))
                    }
                }
            }.getOrElse { emptyList() }

            val fromTxt = runCatching {
                context.assets.open("pokemon_names_full.txt").bufferedReader().useLines { lines ->
                    lines.map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toList()
                }
            }.getOrElse { emptyList() }

            val loaded = (fromJson + fromTxt).distinct()
            orderedNames = loaded
            return loaded
        }
    }

    private fun normalize(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .uppercase(Locale.US)
            .trim()
    }
}
