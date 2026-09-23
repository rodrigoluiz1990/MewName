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
        stamina: Int?,
        selectedPokemonName: String? = null
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

        val entries = entriesFor(context, familyMembers, selectedPokemonName)
        val entry = entries.firstOrNull { candidate ->
            candidate.combinations[ivPercent]?.let { expected ->
                expected.first == attack && expected.second == defense && expected.third == stamina
            } == true
        } ?: entries.firstOrNull { candidate ->
            ivPercent in candidate.combinations
        }
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

    fun bestCombinations(
        context: Context,
        familyMembers: List<String>,
        selectedPokemonName: String? = null
    ): Map<Int, Triple<Int, Int, Int>> {
        val signature = familySignature(familyMembers)
        if (signature.isBlank()) return emptyMap()
        val entries = entriesFor(context, familyMembers, selectedPokemonName)
        val combinations = entries.firstOrNull()?.combinations.orEmpty()
        return linkedMapOf<Int, Triple<Int, Int, Int>>().apply {
            listOf(98, 96, 93, 91).forEach { percent ->
                combinations[percent]?.let { put(percent, it) }
            }
        }
    }
    /**
     * Candy families contain both ordinary and regional evolution branches.
     * Scope Master rules to the selected branch before testing any IV combination.
     */
    private fun entriesFor(
        context: Context,
        familyMembers: List<String>,
        selectedPokemonName: String?
    ): List<FamilyEntry> {
        val catalog = loadFamilyMap(context)
        val members = familyMembers.map(::normalize).filter { it.isNotBlank() }.distinct()
        val selected = selectedPokemonName?.let(::normalize)?.takeIf { it.isNotBlank() }
            ?: members.singleOrNull()
        // A selected Lycanroc form must not inherit another form's combinations.
        // Rockruff can still consider both evolution rules.
        if (selected in setOf("LYCANROC", "LYCANROCNOTURNO")) {
            return listOfNotNull(catalog[selected])
        }
        val hasRegionalBranches = (members + listOfNotNull(selected)).any { region(it) != null }
        val scoped = if (hasRegionalBranches) {
            // Do not infer a regional form from an ambiguous candy family.
            if (selected == null || (selected !in members && catalog[selected] == null)) return emptyList()
            val selectedRegion = region(selected)
            (listOf(selected) + members).distinct().filter { region(it) == selectedRegion }
        } else members
        if (hasRegionalBranches) {
            // An explicit species rule wins over another evolution's rule.
            catalog[selected]?.let { return listOf(it) }
        }
        val signature = familySignature(scoped)
        return buildList {
            catalog[signature]?.let(::add)
            scoped.mapNotNull { catalog[it] }.forEach(::add)
            relatedFormEntries(catalog, signature, scoped).forEach(::add)
        }.distinctBy { it.signature }
    }

    internal fun regionalBranch(name: String): String? = region(normalize(name))

    private fun region(key: String): String? =
        REGIONAL_EVOLUTIONS[key] ?: REGIONS.firstOrNull { key.endsWith(it) }
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
                        val signature = familySignature(obj.optString("key").split("|"))
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

    private fun relatedFormEntries(
        familyMap: Map<String, FamilyEntry>,
        signature: String,
        normalizedMembers: List<String>
    ): List<FamilyEntry> {
        val hasDeoxys = signature == "DEOXYS" || "DEOXYS" in normalizedMembers
        // The family catalog groups Zero and Hero under Palafin. Preserve both
        // spreadsheet rules instead of collapsing them into one species entry.
        val hasPalafin = "PALAFIN" in normalizedMembers
        if (!hasDeoxys && !hasPalafin) return emptyList()
        return familyMap
            .filterKeys { key ->
                (hasDeoxys && key.startsWith("DEOXYS") && key != "DEOXYS") ||
                    (hasPalafin && key in setOf("PALAFINZERO", "PALAFINHERO"))
            }
            .values
            .toList()
    }

    private fun normalize(value: String): String {
        val rawKey = Normalizer.normalize(value.replace("♀", "F").replace("♂", "M"), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9]"), "")
            .trim()
        val prefix = REGIONAL_PREFIXES.entries.firstOrNull { rawKey.startsWith(it.key) }
        val key = if (prefix != null) rawKey.removePrefix(prefix.key) + prefix.value else rawKey
        // Display form names changed; the bundled Master table still uses the species keys.
        return when (key) {
            "LYCANROCDIURNO", "LYCANROCMIDDAY", "LYCANROCMIDDAYFORM", "LYCANROCFORMADIURNA",
            "LYCANROCCREPUSCULO", "LYCANROCDUSK", "LYCANROCDUSKFORM", "LYCANROCFORMACREPUSCULO" -> "LYCANROC"
            "LYCANROCNOTURNO", "LYCANROCMIDNIGHT", "LYCANROCMIDNIGHTFORM",
            "LYCANROCFORMANOTURNA" -> "LYCANROCNOTURNO"
            "ZACIANHERO", "ZACIANCOROADO", "ZACIANCROWNEDSWORD" -> "ZACIAN"
            "ZAMAZENTAHERO", "ZAMAZENTACOROADO", "ZAMAZENTACROWNEDSHIELD" -> "ZAMAZENTA"
            else -> key
        }
    }

    private companion object {
        val REGIONS = listOf("ALOLA", "GALAR", "HISUI", "PALDEA")
        val REGIONAL_PREFIXES = mapOf("ALOLAN" to "ALOLA", "GALARIAN" to "GALAR",
            "HISUIAN" to "HISUI", "PALDEAN" to "PALDEA")
        val REGIONAL_EVOLUTIONS = mapOf(
            "PERRSERKER" to "GALAR", "SIRFETCHD" to "GALAR", "CURSOLA" to "GALAR",
            "OBSTAGOON" to "GALAR", "RUNERIGUS" to "GALAR", "MRRIME" to "GALAR",
            "SNEASLER" to "HISUI", "OVERQWIL" to "HISUI", "CLODSIRE" to "PALDEA"
        )
        val SUPPORTED_IV_PERCENTS = setOf(67, 91, 93, 96, 98)
    }
}
