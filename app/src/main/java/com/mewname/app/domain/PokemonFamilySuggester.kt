package com.mewname.app.domain

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale

class PokemonFamilySuggester {
    @Volatile
    private var familyMap: Map<String, List<String>>? = null

    fun suggestionsFor(context: Context, candyFamilyName: String?, currentName: String?): List<String> =
        familyMembersFor(context, candyFamilyName, currentName)

    fun familyMembersFor(context: Context, candyFamilyName: String?, currentName: String?): List<String> {
        val mappedFamily = (if (normalize(currentName.orEmpty()).startsWith("NIDOR")) listOfNotNull(currentName, candyFamilyName) else listOfNotNull(candyFamilyName, currentName))
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
                val jsonString = context.assets.open(AssetPaths.POKEMON_FAMILIES).bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(jsonString)
                buildMap<String, List<String>> {
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
                            // Keep the first, most specific family registered for a form.
                            putIfAbsent(member, members)
                        }

                    }
                    // An OCR read without the gender symbol must stay within the two Nidoran branches.
                    val nidoranFamily = (get("NIDORAN♀").orEmpty() + get("NIDORAN♂").orEmpty()).distinct()
                    if (nidoranFamily.isNotEmpty()) putIfAbsent("NIDORAN", nidoranFamily)
                    // Explicit canonical keys take precedence over a generic family's aliases.
                    val names = JSONArray(context.assets.open(AssetPaths.POKEMON_NAMES).bufferedReader().use { it.readText() })
                    for (index in 0 until names.length()) {
                        val entry = names.getJSONObject(index)
                        val explicitFamily = jsonObject.opt(normalize(entry.getString("name"))) ?: continue
                        val members = when (explicitFamily) {
                            is JSONArray -> List(explicitFamily.length()) { explicitFamily.getString(it) }
                            is String -> listOf(explicitFamily)
                            else -> continue
                        }
                        val aliases = entry.optJSONArray("aliases") ?: continue
                        for (aliasIndex in 0 until aliases.length()) {
                            val alias = aliases.getString(aliasIndex)
                            if ('(' in alias || entry.getString("name").startsWith("Nidoran")) put(normalize(alias), members)
                        }
                    }
                }
            }.getOrElse { emptyMap() }
            familyMap = loaded
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
