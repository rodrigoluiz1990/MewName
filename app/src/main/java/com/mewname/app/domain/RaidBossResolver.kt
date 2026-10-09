package com.mewname.app.domain

/** Resolves battle forms separately from the species OCR. Never performs network requests. */
internal object RaidBossResolver {
    data class Result(val selected: RaidChoice?, val alternatives: List<RaidChoice>, val fromCatalog: Boolean)

    /** A model can cover the start/end of the boss label (Xerneas -> "erne").
     * Only use a substantial fragment from the calibrated name band, a detected
     * numeric tier and a unique current-catalog match. Never infer from gym text.
     */
    fun resolveOccludedName(lines: List<BattleOcrLine>, meta: RaidMetadata,
        catalog: List<RaidChoice>, detectedLevel: Int?): RaidChoice? {
        if (detectedLevel == null) return null
        val fragments = lines.filter {
            (it.top + it.bottom) / 2f in .16f.. .39f &&
                it.left < .65f && it.right > .35f
        }.map { raidKey(it.text) }.filter { it.matches(Regex("[a-z]{4,}")) }.distinct()
        if (fragments.isEmpty()) return null
        val candidates = catalog.filter {
            !it.tier.contains("MAX") && !it.tier.contains("MEGA") &&
                Regex("RAID_LEVEL_(\\d+)").find(it.tier)?.groupValues?.get(1)?.toIntOrNull() == detectedLevel
        }.distinct()
        val matches = candidates.filter { choice ->
            val pokemon = meta.pokemon[choice.id] ?: return@filter false
            val species = raidKey(baseId(pokemon.id)).replace(" ", "")
            fragments.any { part -> part.length >= 4 && part.length * 2 >= species.length && part in species }
        }
        return matches.singleOrNull()
    }

    private fun baseId(id: String) = id.replace(Regex("_SHADOW(?=_FORM|$)"), "")
        .replace(Regex("_MEGA(?:_[XY])?$|_PRIMAL$"), "").removeSuffix("_FORM")

    // Preserve API category IDs while comparing their actual symbol count.
    private fun symbolCount(tier: String): Int? = when (tier) {
        "RAID_LEVEL_ULTRA_BEAST" -> 5
        "RAID_LEVEL_MEGA" -> 4
        else -> Regex("RAID_LEVEL_(?:MEGA_)?([1-6])(?:_|$)").find(tier)?.groupValues?.get(1)?.toIntOrNull()
    }

    fun resolve(name: String, screenText: String, meta: RaidMetadata, catalog: List<RaidChoice>, detectedLevel: Int? = null, fallbackCatalog: List<RaidChoice> = emptyList()): Result {
        if (detectedLevel != null) {
            val current = catalog.filter {
                !it.tier.contains("MAX", ignoreCase = true) && symbolCount(it.tier) == detectedLevel
            }.distinct().sortedWith(
                compareBy<RaidChoice> { it.tier.contains("SHADOW") || form(it.id) == "shadow" }
                    .thenBy { meta.pokemon[it.id]?.dex?.takeIf { dex -> dex > 0 } ?: Int.MAX_VALUE }
                    .thenBy { it.id }.thenBy { it.tier }
            )
            // OCR can help choose within this list, but cannot add old raids or invent forms.
            val named = if (name.isNotBlank() && current.isNotEmpty())
                resolve(name, screenText, meta, current).selected?.takeIf { it in current }
            else null
            return Result(named ?: current.singleOrNull(), current, current.isNotEmpty())
        }
        val levelCatalog = catalog.filter { choice ->
            !choice.tier.contains("MAX", ignoreCase = true) &&
                (detectedLevel == null || Regex("RAID_LEVEL_(\\d+)").find(choice.tier)?.groupValues?.get(1)?.toIntOrNull() == detectedLevel)
        }
        val cleaned = raidKey(name).replace(Regex("\\b(sombroso|sombrosa|oscuro|oscura)\\b"), "shadow")
        val identified = meta.resolve(name) ?: meta.resolve(cleaned)
            ?: meta.resolve(cleaned.replace(Regex("\\b(shadow|mega|primal)\\b"), "").trim())
            ?: return Result(
                selected = null,
                alternatives = levelCatalog.distinct().sortedWith(
                    compareBy<RaidChoice> { meta.pokemon[it.id]?.dex?.takeIf { dex -> dex > 0 } ?: Int.MAX_VALUE }
                        .thenBy { it.id }
                ),
                fromCatalog = levelCatalog.isNotEmpty()
            )
        val family = meta.pokemon.values.filter { baseId(it.id) == baseId(identified.id) }
        // A recognized boss has authoritative category IDs (e.g. ULTRA_BEAST), not just OCR stars.
        val familyIds = family.map { it.id }.toSet()
        val known = catalog.filter { it.id in familyIds && !it.tier.contains("MAX") }
            .ifEmpty { fallbackCatalog.filter { it.id in familyIds && !it.tier.contains("MAX") } }
            .distinct()
        val matchingLevel = known.filter { choice ->
            detectedLevel == null || Regex("RAID_LEVEL_(\\d+)").find(choice.tier)?.groupValues?.get(1)?.toIntOrNull() == detectedLevel ||
                choice.tier == "RAID_LEVEL_ULTRA_BEAST" || choice.tier == "RAID_LEVEL_MEGA"
        }
        val preferred = matchingLevel.ifEmpty { known }
        val alternatives = family.flatMap { pokemon ->
            known.filter { it.id == pokemon.id }.ifEmpty {
                listOf(RaidChoice(pokemon.id, detectedLevel?.let { "RAID_LEVEL_$it" } ?: raidTier(pokemon.id, catalog)))
            }
        }
            .sortedWith(compareBy<RaidChoice> { form(it.id) }.thenBy { it.id })
        val text = raidKey(screenText)
        // Raid-specific phrases avoid interpreting a gym name such as 'Shadow Park' as a form.
        val shadow = Regex("\\b(?:shadow (?:raid|gem)|(?:raid|reide|incursao|incursion) (?:sombros[ao]|oscur[ao])|(?:gema[s]? purificada[s]?|purified gems?))\\b").containsMatchIn(text)
        val baseName = raidKey(baseId(identified.id))
        val mega = Regex("\\bmega[ -]+${Regex.escape(baseName)}(?: [xy])?\\b").containsMatchIn(text)
        val explicit = when {
            shadow -> "shadow"
            mega -> "mega"
            form(identified.id) != "normal" -> form(identified.id)
            else -> null
        }
        val eligible = alternatives.filter { explicit == null || form(it.id) == explicit }
        val active = preferred.filter { choice -> eligible.any { it == choice } }.distinct()
        if (explicit != null) {
            // Keep X/Y when explicitly read; never choose arbitrarily between two Mega forms.
            val exact = eligible.singleOrNull { choice ->
                val words = raidKey(choice.id)
                Regex("\\b${Regex.escape(words)}\\b").containsMatchIn(text) ||
                    Regex("\\bmega ${Regex.escape(baseName)} ${choice.id.takeLast(1).lowercase()}\\b").containsMatchIn(text)
            }
            val namedForm = eligible.singleOrNull { it.id == identified.id && form(identified.id) != "normal" }
            val selected = exact ?: namedForm ?: active.singleOrNull() ?: eligible.singleOrNull()
            return Result(selected, alternatives, false)
        }
        val selected = active.singleOrNull() ?: alternatives.singleOrNull { it.id == identified.id }
        return Result(selected, alternatives, active.singleOrNull() != null && selected?.id != identified.id)
    }

    fun form(id: String): String = when {
        "_SHADOW" in id -> "shadow"
        "_MEGA" in id -> "mega"
        id.endsWith("_PRIMAL") -> "primal"
        else -> "normal"
    }
}
