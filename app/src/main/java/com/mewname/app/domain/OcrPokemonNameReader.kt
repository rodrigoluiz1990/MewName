package com.mewname.app.domain

import com.mewname.app.domain.OcrTextGeometry.normalizeText
import com.mewname.app.domain.OcrTextGeometry.rawLinesInRegion

import android.content.Context
import android.graphics.Rect
import com.mewname.app.model.CandyDebugInfo
import com.mewname.app.ocr.OcrTextLine
import org.json.JSONArray
import org.apache.commons.text.similarity.LevenshteinDistance
import kotlin.math.abs
import kotlin.math.max

/** Resolves titles, footer names and candy labels using one cached name catalog. */
internal class OcrPokemonNameReader(
    private val familySuggester: PokemonFamilySuggester = PokemonFamilySuggester()
) {
    data class PokemonNameEntry(
        val dex: Int,
        val name: String,
        val normalizedName: String,
        val normalizedAliases: List<String>
    )

    private val footerPokemonRegex = Regex("""(?i)O\s+POK[ÉE]MON\s+([\p{L}' .-]+?)\s+FOI\s+(?:PEGO|CAPTURADO)\s+EM""")
    private val footerCaughtRegex = Regex("""(?i)(?:THIS|THE)\s+POK[ÉE]MON\s+([\p{L}' .-]+?)\s+WAS\s+CAUGHT""")

    private val pokemonTypes = setOf(
        "NORMAL", "FIRE", "WATER", "GRASS", "ELECTRIC", "ICE", "FIGHTING", "POISON",
        "GROUND", "FLYING", "PSYCHIC", "BUG", "ROCK", "GHOST", "DRAGON", "STEEL", "FAIRY", "DARK", "DRAGAO"
    )
    private val generalBlacklist = setOf(
        "CP", "PC", "HP", "STARDUST", "WEIGHT", "HEIGHT", "ATAQUE", "DEFESA",
        "VALORAR", "APPRAISAL", "STATS", "POWER UP", "PURIFY", "EVOLVE", "NEW ATTACK"
    )

    private var pokemonNamesCache: List<PokemonNameEntry>? = null
    private val fuzzyMatcher = LevenshteinDistance()

    fun extractCandyFamilyName(
        context: Context,
        lines: List<OcrTextLine>,
        referenceBounds: Rect?,
        pokemonName: String?
    ): Pair<String?, CandyDebugInfo> {
        val regionLines = rawLinesInRegion(lines, 0.20f, 0.98f, 0.43f, 0.86f, referenceBounds)
            .sortedBy { it.boundingBox?.top ?: Int.MAX_VALUE }
        val candyIndex = regionLines.indexOfFirst { line ->
            val normalized = normalizeText(line.text)
            normalized.contains("CANDY") || normalized.contains("DOCES") || normalized.contains("CARAMELOS")
        }
        val candyEntry = regionLines.getOrNull(candyIndex)
        val candyLine = candyEntry?.text

        if (candyLine == null) {
            return null to CandyDebugInfo(
                regionLineCount = regionLines.size,
                regionLines = regionLines.map { it.text },
                notes = "nenhuma linha com DOCES DE/CANDY encontrada"
            )
        }

        val candyRect = candyEntry.boundingBox
        val familyLine = if (candyRect != null) {
            regionLines
                .drop(candyIndex + 1)
                .filter { candidate ->
                    val rect = candidate.boundingBox ?: return@filter false
                    val verticalGap = rect.top - candyRect.bottom
                    val centerDiff = abs(rect.exactCenterX() - candyRect.exactCenterX())
                    verticalGap >= 0 &&
                        verticalGap <= candyRect.height() * 4 &&
                        centerDiff <= candyRect.width() * 0.8f
                }
                .firstOrNull { candidate ->
                    val normalized = normalizeText(candidate.text)
                    normalized.isNotBlank() &&
                        !normalized.contains("DOCES DE") &&
                        !normalized.contains("CANDY") &&
                        !normalized.contains("DOCES GG") &&
                        !normalized.contains("POEIRA") &&
                        !normalized.matches(Regex("""[\d.,]+"""))
                }
                ?.text
        } else {
            null
        }

        // A complete candy label must not absorb an unrelated line below it.
        fun extractFamily(text: String): String? = (Regex("(?i)(?:doces(?: gg)?(?: de)?|caramelos(?: xl)?(?: de)?)\\s+([\\p{L}' .-]+)")
            .find(text)?.groupValues?.getOrNull(1)?.trim()
            ?: Regex("(?i)([\\p{L}' .-]+?)\\s+(?:candy)(?:\\s+xl)?(?:$|\\s)")
                .find(text)?.groupValues?.getOrNull(1)?.trim()
            ?: Regex("(?i)candy\\s+([\\p{L}' .-]+)").find(text)?.groupValues?.getOrNull(1)?.trim())?.takeIf {
                it.length >= 3 && normalizeText(it) !in setOf("DE", "GG", "XL", "CANDY", "DOCES", "CARAMELOS")
            }

        val precedingFamily = if (normalizeText(candyLine) in setOf("CANDY", "CANDY XL") && candyRect != null) {
            regionLines.take(candyIndex).lastOrNull { candidate ->
                val rect = candidate.boundingBox ?: return@lastOrNull false
                val gap = candyRect.top - rect.bottom
                gap in 0..(candyRect.height() * 4) &&
                    abs(rect.exactCenterX() - candyRect.exactCenterX()) <= max(rect.width(), candyRect.width()) * 0.8f &&
                    loadPokemonNames(context).any { normalizeText(candidate.text) in it.normalizedAliases }
            }?.text
        } else null
        val directFamily = extractFamily(candyLine)
        val combinedLine = when {
            directFamily != null -> candyLine
            precedingFamily != null -> "$precedingFamily $candyLine"
            else -> listOfNotNull(candyLine, familyLine).joinToString(" ")
        }.replace(Regex("\\s+"), " ").trim()
        val rawFamily = directFamily ?: extractFamily(combinedLine)
        if (rawFamily == null) {
            return null to CandyDebugInfo(
                regionLineCount = regionLines.size,
                regionLines = regionLines.map { it.text },
                matchedLine = combinedLine,
                notes = "linha encontrada, mas a familia nao foi extraida pela regex"
            )
        }

        val normalizedFamily = normalizeText(rawFamily)
        val pokemonNames = loadPokemonNames(context)
        val directResolved = pokemonNames.firstOrNull { entry ->
            entry.normalizedName == normalizedFamily || normalizedFamily in entry.normalizedAliases
        }?.name
        val fuzzyResolved = if (directResolved == null) pokemonNames
            .asSequence()
            .map { entry ->
                val score = entry.normalizedAliases.minOf { alias ->
                    when {
                        alias.startsWith(normalizedFamily) || normalizedFamily.startsWith(alias) -> 0
                        alias.contains(normalizedFamily) || normalizedFamily.contains(alias) -> 1
                        else -> fuzzyMatcher.apply(normalizedFamily, alias)
                    }
                }
                entry to score
            }
            .filter { (_, score) -> score <= fuzzyCandyFamilyThreshold(normalizedFamily.length) }
            .minByOrNull { (_, score) -> score }
            ?.first
            ?.name
        else null
        val resolved = directResolved ?: chooseCandyFamilyResolution(context, fuzzyResolved, pokemonName)
        val resolutionNotes = buildList {
            if (resolved == null) add("familia extraida, mas nao encontrada na base local")
            if (resolved != null && fuzzyResolved != null && directResolved == null && !resolved.equals(fuzzyResolved, ignoreCase = true)) {
                add("familia fuzzy trocada por familia do pokemon identificado")
            }
        }.joinToString("; ")

        return resolved to CandyDebugInfo(
            regionLineCount = regionLines.size,
            regionLines = regionLines.map { it.text },
            matchedLine = combinedLine,
            extractedFamilyRaw = rawFamily,
            resolvedFamilyName = resolved,
            notes = resolutionNotes
        )
    }

    private fun fuzzyCandyFamilyThreshold(length: Int): Int {
        return when {
            length <= 5 -> 1
            length <= 8 -> 2
            length <= 12 -> 3
            else -> 4
        }
    }

    private fun chooseCandyFamilyResolution(
        context: Context,
        resolvedFromCandy: String?,
        pokemonName: String?
    ): String? {
        val normalizedPokemon = pokemonName?.let(::normalizeText)
        if (normalizedPokemon.isNullOrBlank()) return resolvedFromCandy
        val pokemonNameIsKnown = loadPokemonNames(context).any { entry ->
            entry.normalizedName == normalizedPokemon || normalizedPokemon in entry.normalizedAliases
        }
        if (!pokemonNameIsKnown) return resolvedFromCandy

        val pokemonFamily = familySuggester.familyMembersFor(context, null, pokemonName)
            .takeIf { it.isNotEmpty() }
            ?: return resolvedFromCandy
        val normalizedPokemonFamily = pokemonFamily.map(::normalizeText).toSet()
        if (resolvedFromCandy != null && normalizeText(resolvedFromCandy) in normalizedPokemonFamily) {
            return resolvedFromCandy
        }
        return pokemonFamily.firstOrNull()
    }

    fun loadPokemonNames(context: Context): List<PokemonNameEntry> {
        if (pokemonNamesCache != null) return pokemonNamesCache!!
        return try {
            val list = mutableListOf<PokemonNameEntry>()

            val jsonString = context.assets.open(AssetPaths.POKEMON_NAMES).bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val aliasesJson = obj.getJSONArray("aliases")
                val aliases = mutableListOf<String>()
                for (j in 0 until aliasesJson.length()) {
                    aliases += normalizeText(aliasesJson.getString(j))
                }
                val canonicalName = obj.getString("name")
                list += PokemonNameEntry(
                    dex = obj.getInt("dex"),
                    name = canonicalName,
                    normalizedName = normalizeText(canonicalName),
                    normalizedAliases = (aliases + normalizeText(canonicalName)).distinct()
                )
            }

            val deduped = list.distinctBy { it.normalizedName }
            pokemonNamesCache = deduped
            deduped
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun inferDetailPokemonName(context: Context, lines: List<OcrTextLine>, bounds: Rect, candyFamily: String?): String? {
        val names = loadPokemonNames(context)
        val titleRegion = rawLinesInRegion(lines, 0.10f, 0.90f, 0.22f, 0.57f, bounds)
        val hp = titleRegion.firstOrNull { Regex("\\d+\\s*/\\s*\\d+.*(?:HP|PS)", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }?.boundingBox
        val titleLines = titleRegion.filter { line ->
            val rect = line.boundingBox ?: return@filter false
            (hp == null || (rect.bottom <= hp.top && hp.top - rect.bottom < bounds.height() * 0.14f)) &&
                !normalizeText(line.text).let { it.contains("DOCES") || it.contains("CANDY") || it.contains("CARAMELOS") }
        }
        val candidates = titleLines.mapNotNull { sanitizeNameCandidate(it.text)?.let(::normalizeText) }
        for (candidate in candidates) {
            names.firstOrNull { candidate == it.normalizedName || candidate in it.normalizedAliases }?.let { return it.name }
        }
        // Candy constrains a fuzzy nickname; it never identifies the evolution on its own.
        val family = candyFamily?.let { familySuggester.familyMembersFor(context, it, null).map(::normalizeText).toSet() }
        if (family == null) {
            // Without candy evidence only accept a unique, one-character OCR repair
            // of a sufficiently long title. Never select an arbitrary close name.
            val matches = names.filter { entry -> candidates.any { candidate ->
                candidate.length >= 6 && entry.normalizedAliases.any { alias ->
                    abs(alias.length - candidate.length) <= 1 && fuzzyMatcher.apply(candidate, alias) <= 1
                }
            } }
            return matches.singleOrNull()?.name
        }
        val decorated = names.filter { it.normalizedName in family }.filter { entry ->
            titleLines.any { line ->
                val candidate = sanitizeNameCandidate(line.text)?.let(::normalizeText).orEmpty()
                line.text.any { !it.isLetter() && !it.isWhitespace() } && entry.normalizedAliases.any { alias ->
                    candidate.endsWith(" $alias") && candidate.removeSuffix(alias).trim().let { prefix ->
                        prefix.length in 1..3 && prefix.all { it.isLetter() }
                    }
                }
            }
        }
        decorated.singleOrNull()?.let { return it.name }
        val matched = names.filter { it.normalizedName in family }.filter { entry ->
            candidates.any { candidate -> candidate.length >= 4 && entry.normalizedAliases.any { alias ->
                abs(alias.length - candidate.length) <= 1 && fuzzyMatcher.apply(candidate, alias) <= 1
            } }
        }
        return matched.singleOrNull()?.name
    }

    fun inferPokemonName(
        context: Context,
        lines: List<OcrTextLine>,
        extractedMoves: List<String>,
        cp: Int?,
        referenceBounds: Rect?
    ): String? {
        val footerName = inferPokemonNameFromFooter(context, lines, referenceBounds)
        if (footerName != null) return footerName

        val pokemonNames = loadPokemonNames(context)
        val candidateLines = prioritizeTopNameCandidates(lines, cp)

        for (line in candidateLines) {
            val candidate = sanitizeNameCandidate(line.text) ?: continue
            val normalizedCandidate = normalizeText(candidate)
            if (shouldSkipNameCandidate(normalizedCandidate, extractedMoves)) continue

            val exactMatch = pokemonNames.firstOrNull { entry ->
                normalizedCandidate == entry.normalizedName || normalizedCandidate in entry.normalizedAliases
            }
            if (exactMatch != null) return exactMatch.name
        }

        for (line in candidateLines) {
            val candidate = sanitizeNameCandidate(line.text) ?: continue
            val normalizedCandidate = normalizeText(candidate)
            if (shouldSkipNameCandidate(normalizedCandidate, extractedMoves)) continue

            val fuzzyMatch = pokemonNames
                .asSequence()
                .map { entry ->
                    val bestDistance = entry.normalizedAliases.minOf { alias ->
                        if (kotlin.math.abs(normalizedCandidate.length - alias.length) > 2) 3 else fuzzyMatcher.apply(normalizedCandidate, alias)
                    }
                    entry to bestDistance
                }
                .filter { (_, distance) -> distance <= 2 }
                .minByOrNull { (_, distance) -> distance }
                ?.first

            if (fuzzyMatch != null) return fuzzyMatch.name
        }

        return null
    }

    fun curveLevelCandidatesFor(
        context: Context,
        pokemonName: String?,
        familyMembers: List<String>
    ): List<String> {
        val currentName = pokemonName?.takeIf { it.isNotBlank() } ?: return emptyList()
        val currentEntry = findPokemonNameEntry(context, currentName) ?: return listOf(currentName)
        val currentDex = currentEntry.dex
        return buildList {
            add(currentEntry.name)
            familyMembers.forEach { member ->
                val entry = findPokemonNameEntry(context, member) ?: return@forEach
                if (entry.dex == currentDex) add(entry.name)
            }
        }.distinctBy(::normalizeText)
    }

    fun findPokemonNameEntry(context: Context, name: String): PokemonNameEntry? {
        val normalizedName = normalizeText(name)
        return loadPokemonNames(context).firstOrNull { entry ->
            entry.normalizedName == normalizedName || normalizedName in entry.normalizedAliases
        }
    }

    private fun inferPokemonNameFromFooter(
        context: Context,
        lines: List<OcrTextLine>,
        referenceBounds: Rect?
    ): String? {
        val footerLines = rawLinesInRegion(lines, 0.0f, 1.0f, 0.84f, 1.0f, referenceBounds)
            .map { it.text }
        val footerText = footerLines.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        val rawCandidate = AppraisalFooterReader.candidate(footerText)
            ?: AppraisalFooterReader.candidate(lines.joinToString("\n") { it.text })
            ?: footerPokemonRegex.find(footerText)?.groupValues?.getOrNull(1)
            ?: footerCaughtRegex.find(footerText)?.groupValues?.getOrNull(1)
            ?: return null

        val candidate = sanitizeNameCandidate(rawCandidate) ?: return null
        val normalizedCandidate = normalizeText(candidate)
        val pokemonNames = loadPokemonNames(context)

        return pokemonNames.firstOrNull { entry ->
            normalizedCandidate == entry.normalizedName || normalizedCandidate in entry.normalizedAliases
        }?.name ?: pokemonNames
            .asSequence()
            .map { entry ->
                val bestDistance = entry.normalizedAliases.minOf { alias ->
                    if (kotlin.math.abs(normalizedCandidate.length - alias.length) > 2) 3 else fuzzyMatcher.apply(normalizedCandidate, alias)
                }
                entry to bestDistance
            }
            .filter { (_, distance) -> distance <= 2 }
            .minByOrNull { (_, distance) -> distance }
            ?.first
            ?.name
    }

    private fun prioritizeTopNameCandidates(lines: List<OcrTextLine>, cp: Int?): List<OcrTextLine> {
        val cpLineTop = lines.firstOrNull { cp != null && OcrNumericReader.cpRegex.containsMatchIn(it.text) }?.boundingBox?.top
        return lines
            .sortedWith(
                compareBy<OcrTextLine>(
                    { line ->
                        when {
                            cpLineTop != null && line.boundingBox != null -> abs(line.boundingBox.top - cpLineTop)
                            else -> Int.MAX_VALUE
                        }
                    },
                    { it.boundingBox?.top ?: Int.MAX_VALUE },
                    { it.boundingBox?.left ?: Int.MAX_VALUE }
                )
            )
            .take(12)
    }

    private fun sanitizeNameCandidate(text: String): String? {
        val cleaned = text
            .replace(Regex("[^\\p{L}' -]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.length !in 3..18) return null
        return cleaned
    }

    private fun shouldSkipNameCandidate(normalizedCandidate: String, extractedMoves: List<String>): Boolean {
        return generalBlacklist.any { normalizedCandidate == it || normalizedCandidate.contains(it) } ||
            pokemonTypes.contains(normalizedCandidate) ||
            extractedMoves.any { it == normalizedCandidate }
    }
}
