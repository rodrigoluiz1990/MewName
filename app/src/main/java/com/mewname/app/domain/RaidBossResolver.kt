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
        }.map { raidKey(it.text) }.filter { it.matches(Regex("[a-z ]{4,}")) }.distinct()
        if (fragments.isEmpty()) return null
        val candidates = catalog.filter {
            !it.tier.contains("MAX") && symbolCount(it.tier) == detectedLevel
        }.distinct()
        val matches = candidates.filter { choice ->
            val pokemon = meta.pokemon[choice.id] ?: return@filter false
            val species = raidKey(regionalBaseId(pokemon.id)).replace(" ", "")
            fragments.any { fragment ->
                // Join only fragments from the same OCR line in the boss-name band.
                // A truncated Mega prefix is evidence only for a current Mega candidate.
                val label = if (choice.tier.contains("MEGA"))
                    fragment.replace(Regex("^(mega|meg|me) +"), "") else fragment
                val part = label.replace(" ", "")
                (part.length >= 4 && part.length * 2 >= species.length && part in species) ||
                    closeOcrName(part, species, fragmented = ' ' in label)
            }
        }
        return matches.singleOrNull()
    }

    /** Exact boss CP is a fallback, never the capture CP or an arbitrary screen number.
     * Unsupported raid categories deliberately remain manual choices. */
    fun resolveBossCp(lines: List<BattleOcrLine>, meta: RaidMetadata,
        catalog: List<RaidChoice>, detectedLevel: Int?): RaidChoice? {
        fun log(message: String) {
            if (com.mewname.app.BuildConfig.DEBUG) android.util.Log.d("RaidReadTiming", "bossCp $message")
        }
        val band = lines.filter { (it.top + it.bottom) / 2f in .13f.. .28f }
        val readings = bossCpLines(band).mapNotNull { parseBossCp(it.text) }.distinct()
        log("level=$detectedLevel readings=$readings catalogSize=${catalog.size} band=$band")
        if (detectedLevel == null) { log("rejected=missing_level"); return null }
        val cp = readings.singleOrNull()
        if (cp == null) { log("rejected=missing_or_ambiguous_cp"); return null }
        val cpMatches = catalog.distinct().filter { choice ->
            meta.pokemon[choice.id]?.let { bossCp(it, choice.tier) } == cp
        }
        val matches = cpMatches.filter { symbolCount(it.tier) == detectedLevel }
        log("cpMatches=$cpMatches levelMatches=$matches result=${when {
            cpMatches.isEmpty() -> "no_current_cp_match"
            matches.isEmpty() -> "level_mismatch"
            matches.size > 1 -> "ambiguous"
            else -> "accepted"
        }}")
        return matches.singleOrNull()
    }

    /** Keep ambiguity explicit; absence of shadow text is not proof of a normal raid. */
    fun bossCpCandidates(lines: List<BattleOcrLine>, screenText: String, meta: RaidMetadata,
        catalog: List<RaidChoice>, detectedLevel: Int?, index: Map<Int, List<RaidChoice>>): List<RaidChoice> {
        if (detectedLevel == null) return emptyList()
        val cp = bossCpLines(lines).mapNotNull { parseBossCp(it.text) }.distinct().singleOrNull()
            ?: return emptyList()
        val text = raidKey(screenText)
        val shadow = Regex("\\b(?:shadow raid|raid sombros[ao]|reide sombros[ao]|incursion oscura|purified gems?|gemas? purificadas?)\\b").containsMatchIn(text)
        val normal = Regex("\\b(?:normal raid|regular raid|raid comum|reide comum|incursion normal)\\b").containsMatchIn(text)
        if (shadow && normal) return emptyList()
        val choices = (index[cp].orEmpty() + catalog).distinct().filter { choice ->
            symbolCount(choice.tier) == detectedLevel &&
                meta.pokemon[choice.id]?.let { bossCp(it, choice.tier) } == cp &&
                (!shadow || choice.tier.contains("SHADOW")) &&
                (!normal || !choice.tier.contains("SHADOW"))
        }
        val named = choices.filter { choice ->
            val label = raidKey(choice.id).replace(" shadow", "")
            lines.any { line -> (line.top + line.bottom) / 2f in .16f.. .39f &&
                line.left < .65f && line.right > .35f && raidKey(line.text) == label }
        }
        val evidence = named.ifEmpty { choices }
        val pool = evidence.filter { it in catalog }.ifEmpty { evidence }
        if (com.mewname.app.BuildConfig.DEBUG) android.util.Log.d("RaidReadTiming",
            "cpIndex cp=$cp level=$detectedLevel shadow=$shadow normal=$normal candidates=$pool")
        return pool.sortedWith(compareBy<RaidChoice> { meta.pokemon[it.id]?.dex ?: Int.MAX_VALUE }
            .thenBy { it.id }.thenBy { it.tier })
    }

    /** Join only adjacent numeric fragments with matching baselines and glyph heights. */
    internal fun bossCpLines(lines: List<BattleOcrLine>): List<BattleOcrLine> {
        val band = lines.filter { (it.top + it.bottom) / 2f in .13f.. .28f }
        val fragments = band.filter { it.text.trim().matches(Regex("[0-9]{1,5}")) }.sortedBy { it.left }
        val joined = fragments.zipWithNext().mapNotNull { (left, right) ->
            val height = minOf(left.bottom-left.top, right.bottom-right.top)
            val overlap = minOf(left.bottom,right.bottom)-maxOf(left.top,right.top)
            val gap = right.left-left.right
            if (height <= 0 || overlap < height * .8f || gap !in 0f.. .035f ||
                kotlin.math.abs((left.bottom-left.top)-(right.bottom-right.top)) > height*.25f) return@mapNotNull null
            val text = left.text.trim()+right.text.trim()
            if (parseBossCp(text) == null) return@mapNotNull null
            BattleOcrLine(text,left.left,minOf(left.top,right.top),right.right,maxOf(left.bottom,right.bottom))
        }
        return (band.filter { parseBossCp(it.text) != null } + joined).filter { it.left < .65f && it.right > .35f }
    }

    // OCR may attach a background stroke after the digits; never repair digits
    // or accept punctuation within a number (timer, decimal or range).
    internal fun parseBossCp(text: String): Int? =
        Regex("(?i)^(?:&\\s*)?(?:(?:pc|cp|po|p)\\s*)?([0-9]{4,6})\\s*[-–—.,]?$")
            .matchEntire(text.trim())?.groupValues?.get(1)?.toIntOrNull()

    internal fun bossCp(pokemon: RaidPokemon, tier: String): Int? {
        // Displayed boss CP uses base attack/defense + 15 IV and fixed raid HP.
        // These are not the encounter level 20/25 CP values.
        val hp = when (tier) {
            "RAID_LEVEL_1", "RAID_LEVEL_1_SHADOW" -> 600
            "RAID_LEVEL_3", "RAID_LEVEL_3_SHADOW" -> 3600
            "RAID_LEVEL_5", "RAID_LEVEL_5_SHADOW", "RAID_LEVEL_ULTRA_BEAST" -> 15000
            "RAID_LEVEL_MEGA" -> 9000
            else -> return null
        }
        if (pokemon.attack <= 0 || pokemon.defense <= 0) return null
        return kotlin.math.floor((pokemon.attack + 15) * kotlin.math.sqrt((pokemon.defense + 15).toDouble()) *
            kotlin.math.sqrt(hp.toDouble()) / 10).toInt()
    }

    private fun baseId(id: String) = id.replace(Regex("_SHADOW(?=_FORM|$)"), "")
        .replace(Regex("_MEGA(?:_[XY])?$|_PRIMAL$"), "").removeSuffix("_FORM")

    // Strip only regional qualifiers; other transformations retain their identity.
    private fun regionalBaseId(id: String) = baseId(id)
        .replace(Regex("_(ALOLAN|GALARIAN|HISUIAN|PALDEAN)$"), "")

    private fun closeOcrName(part: String, species: String, fragmented: Boolean = false): Boolean {
        if (part.length < 5 || species.length < 5 || part.first() != species.first()) return false
        val missing = species.length - part.length
        if (missing !in 0..2) return false
        // A heavily occluded reading needs an intact suffix (Vforb -> Voltorb).
        val anchoredFragments = fragmented && part.length >= 6 &&
            part.take(3) == species.take(3) && part.takeLast(2) == species.takeLast(2)
        val limit = if (part.takeLast(3) == species.takeLast(3) || anchoredFragments) missing + 1 else 1
        var previous = IntArray(species.length + 1) { it }
        for (i in part.indices) {
            val next = IntArray(species.length + 1)
            next[0] = i + 1
            for (j in species.indices) next[j + 1] = minOf(
                previous[j + 1] + 1, next[j] + 1,
                previous[j] + if (part[i] == species[j]) 0 else 1
            )
            previous = next
        }
        return previous.last() <= limit
    }

    // Preserve API category IDs while comparing their actual symbol count.
    private fun symbolCount(tier: String): Int? = when (tier) {
        "RAID_LEVEL_ULTRA_BEAST" -> 5
        "RAID_LEVEL_MEGA" -> 4
        else -> Regex("RAID_LEVEL_(?:MEGA_)?([1-6])(?:_|$)").find(tier)?.groupValues?.get(1)?.toIntOrNull()
    }

    fun resolve(name: String, screenText: String, meta: RaidMetadata, catalog: List<RaidChoice>, detectedLevel: Int? = null, fallbackCatalog: List<RaidChoice> = emptyList()): Result {
        if (detectedLevel != null) {
            val active = catalog.filterNot { it.tier.contains("MAX", ignoreCase = true) }.distinct()
            // Icon counting is noisy when the boss covers the emblems. Only an
            // unambiguous current-catalog identity may override that visual hint.
            // Do not use saved reports or inferred forms/tiers as proof.
            val identity = if (name.isNotBlank()) resolve(name, screenText, meta, active) else null
            val namedCurrent = identity?.selected?.takeIf { selected ->
                identity.alternatives.filter { it in active }.singleOrNull() == selected
            }
            val effectiveLevel = namedCurrent?.let { symbolCount(it.tier) } ?: detectedLevel
            val current = catalog.filter {
                !it.tier.contains("MAX", ignoreCase = true) && symbolCount(it.tier) == effectiveLevel
            }.distinct().sortedWith(
                compareBy<RaidChoice> { it.tier.contains("SHADOW") || form(it.id) == "shadow" }
                    .thenBy { meta.pokemon[it.id]?.dex?.takeIf { dex -> dex > 0 } ?: Int.MAX_VALUE }
                    .thenBy { it.id }.thenBy { it.tier }
            )
            // OCR can help choose within this list, but cannot add old raids or invent forms.
            val named = namedCurrent ?: if (name.isNotBlank() && current.isNotEmpty())
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
        val text = raidKey(screenText)
        // Raid-specific phrases avoid interpreting a gym name such as 'Shadow Park' as a form.
        val shadow = Regex("\\b(?:shadow (?:raid|gem)|(?:raid|reide|incursao|incursion) (?:sombros[ao]|oscur[ao])|(?:gema[s]? purificada[s]?|purified gems?))\\b").containsMatchIn(text)
        // A plain species label does not distinguish its regional forms in GO.
        // Only current records may supply that missing qualifier, and ambiguity
        // must remain a manual choice. Explicitly named regions stay exact.
        if (identified.id == regionalBaseId(identified.id) && identified.dex > 0 && !shadow &&
            !Regex("\\b(mega|primal|shadow)\\b").containsMatchIn(cleaned) &&
            !Regex("\\b(?:mega|primal) ${Regex.escape(raidKey(identified.id))}\\b").containsMatchIn(text)) {
            val regional = levelCatalog.filter { choice ->
                val candidate = meta.pokemon[choice.id]
                candidate?.dex == identified.dex &&
                    regionalBaseId(choice.id) == identified.id
            }.distinct()
            if (regional.any { baseId(it.id) != identified.id }) {
                return Result(regional.singleOrNull(), regional, true)
            }
        }
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
