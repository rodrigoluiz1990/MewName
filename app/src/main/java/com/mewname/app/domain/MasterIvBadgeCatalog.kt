package com.mewname.app.domain

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

class MasterIvBadgeCatalog {
    data class MatchResult(
        val isBestMatch: Boolean? = null,
        val expectedAttack: Int? = null,
        val expectedDefense: Int? = null,
        val expectedStamina: Int? = null,
        val notes: String = ""
    )

    private data class FamilyEntry(
        val signature: String,
        val combinations: Map<Int, Triple<Int, Int, Int>>
    )

    @Volatile
    private var familyMap: Map<String, FamilyEntry>? = null

    fun resolve(
        context: Context,
        familyMembers: List<String>,
        ivPercent: Int?,
        attack: Int?,
        defense: Int?,
        stamina: Int?
    ): MatchResult {
        if (ivPercent !in SUPPORTED_IV_PERCENTS) {
            return MatchResult(notes = "iv_percent_fora_do_escopo")
        }
        if (attack == null || defense == null || stamina == null) {
            return MatchResult(notes = "ivs_incompletos")
        }

        val signature = familySignature(familyMembers)
        if (signature.isBlank()) {
            return MatchResult(notes = "familia_indisponivel")
        }

        val normalizedMembers = familyMembers
            .map(::normalize)
            .filter { it.isNotBlank() }
            .distinct()
        val familyMap = loadFamilyMap(context)
        val entry = familyMap[signature]
            ?: normalizedMembers.firstNotNullOfOrNull { member -> familyMap[member] }
            ?: return MatchResult(notes = "familia_sem_json")
        val expected = entry.combinations[ivPercent]
            ?: return MatchResult(notes = "percentual_sem_regra")

        val isBestMatch = expected.first == attack && expected.second == defense && expected.third == stamina
        return MatchResult(
            isBestMatch = isBestMatch,
            expectedAttack = expected.first,
            expectedDefense = expected.second,
            expectedStamina = expected.third,
            notes = "assinatura=${entry.signature}; consulta=$signature"
        )
    }

    private fun loadFamilyMap(context: Context): Map<String, FamilyEntry> {
        familyMap?.let { return it }
        synchronized(this) {
            familyMap?.let { return it }
            val loaded = runCatching {
                val json = context.assets.open(AssetPaths.PVP_MASTER_IV_TABLE).bufferedReader().use { it.readText() }
                val array = org.json.JSONArray(json)
                buildMap {
                    for (index in 0 until array.length()) {
                        val obj = array.optJSONObject(index) ?: continue
                        val signature = obj.optString("key")
                        if (signature.isBlank() || containsKey(signature)) continue
                        val combinations = mutableMapOf<Int, Triple<Int, Int, Int>>()
                        SUPPORTED_IV_PERCENTS.forEach { ivPercent ->
                            val combo = obj.optJSONArray(ivPercent.toString()) ?: return@forEach
                            if (combo.length() < 3) return@forEach
                            combinations[ivPercent] = Triple(
                                combo.optInt(0),
                                combo.optInt(1),
                                combo.optInt(2)
                            )
                        }
                        put(
                            signature,
                            FamilyEntry(
                                signature = signature,
                                combinations = combinations
                            )
                        )
                    }
                }
            }.getOrElse { emptyMap() }
            familyMap = loaded
            return loaded
        }
    }

    private fun familySignature(members: List<String>): String {
        return members.asSequence()
            .map(::normalize)
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .joinToString("|")
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9]"), "")
            .trim()
    }

    private companion object {
        val SUPPORTED_IV_PERCENTS = setOf(67, 91, 93, 96, 98)
    }
}
