package com.mewname.app.domain

/** Resolves battle forms separately from the species OCR. Never performs network requests. */
internal object RaidBossResolver {
    data class Result(val selected: RaidChoice?, val alternatives: List<RaidChoice>, val fromCatalog: Boolean)

    private fun baseId(id: String) = id.replace(Regex("_SHADOW(?=_FORM|$)"), "")
        .replace(Regex("_MEGA(?:_[XY])?$|_PRIMAL$"), "").removeSuffix("_FORM")

    fun resolve(name: String, screenText: String, meta: RaidMetadata, catalog: List<RaidChoice>, detectedLevel: Int? = null): Result {
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
        val alternatives = family.map { pokemon ->
            val detectedChoice = levelCatalog.singleOrNull { it.id == pokemon.id }
            detectedChoice ?: RaidChoice(pokemon.id, detectedLevel?.let { "RAID_LEVEL_$it" } ?: raidTier(pokemon.id, catalog))
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
        val active = levelCatalog.filter { choice -> eligible.any { it.id == choice.id } }.distinct()
        if (explicit != null) {
            // Keep X/Y when explicitly read; never choose arbitrarily between two Mega forms.
            val exact = eligible.singleOrNull { choice ->
                val words = raidKey(choice.id)
                Regex("\\b${Regex.escape(words)}\\b").containsMatchIn(text) ||
                    Regex("\\bmega ${Regex.escape(baseName)} ${choice.id.takeLast(1).lowercase()}\\b").containsMatchIn(text)
            }
            val namedForm = eligible.singleOrNull { it.id == identified.id && form(identified.id) != "normal" }
            val selected = exact ?: namedForm ?: eligible.singleOrNull() ?: active.singleOrNull()
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
