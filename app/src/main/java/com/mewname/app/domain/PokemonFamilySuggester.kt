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
        meowthFamily(candyFamilyName, currentName)?.let { return it }
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
        meowthFamily(candyFamilyName, currentName)?.let { return it }
        val mappedFamily = listOfNotNull(candyFamilyName, currentName)
            .map(::normalize)
            .firstNotNullOfOrNull { loadFamilyMap(context)[it] }
        return mappedFamily
            ?: listOfNotNull(currentName?.takeIf { it.isNotBlank() }, candyFamilyName?.takeIf { it.isNotBlank() })
                .distinct()
    }

    private fun meowthFamily(candyFamilyName: String?, currentName: String?): List<String>? {
        val isMeowthFamily = listOfNotNull(candyFamilyName, currentName)
            .map(::normalize)
            .any { normalized ->
                normalized == "MEOWTH" ||
                    normalized == "PERSIAN" ||
                    normalized == "ALOLAN MEOWTH" ||
                    normalized == "ALOLAN PERSIAN" ||
                    normalized == "GALARIAN MEOWTH" ||
                    normalized == "PERRSERKER" ||
                    normalized == "PERRSERKER GALARIAN"
            }
        if (!isMeowthFamily) return null
        return listOf(
            "Meowth",
            "Persian",
            "Alolan Meowth",
            "Alolan Persian",
            "Galarian Meowth",
            "Perrserker"
        )
    }

    private fun loadFamilyMap(context: Context): Map<String, List<String>> {
        familyMap?.let { return it }
        synchronized(this) {
            familyMap?.let { return it }
            val loaded = runCatching {
                val jsonString = context.assets.open(AssetPaths.POKEMON_FAMILIES).bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(jsonString)
                buildMap {
                    jsonObject.keys().forEach { key ->
                        val members = when (val rawValue = jsonObject.opt(key)) {
                            is JSONArray -> buildList(rawValue.length()) {
                                for (index in 0 until rawValue.length()) {
                                    add(rawValue.getString(index))
                                }
                            }
                            is String -> listOf(rawValue)
                            else -> emptyList()
                        }
                        if (members.isEmpty()) return@forEach

                        val normalizedMembers = members
                            .map(::normalize)
                            .filter { it.isNotBlank() }
                            .distinct()

                        put(normalize(key), members)
                        normalizedMembers.forEach { member ->
                            put(member, members)
                        }
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
                val jsonString = context.assets.open(AssetPaths.POKEMON_NAMES).bufferedReader().use { it.readText() }
                val array = JSONArray(jsonString)
                buildList(array.length()) {
                    for (index in 0 until array.length()) {
                        add(array.getJSONObject(index).getString("name"))
                    }
                }
            }.getOrElse { emptyList() }
            val loaded = fromJson.distinct()
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
