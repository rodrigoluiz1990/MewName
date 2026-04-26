package com.mewname.app.domain

import android.content.Context
import com.mewname.app.model.EvolutionFlag
import com.mewname.app.model.PokemonScreenData
import java.text.Normalizer
import java.util.Locale

enum class BattleMode {
    RAID,
    MAX
}

data class BattleAdvice(
    val mode: BattleMode,
    val bossName: String?,
    val bossTypes: List<String>,
    val weaknessTypes: List<String>,
    val suggestions: List<BattleSuggestionEntry>,
    val defenderSuggestions: List<BattleSuggestionEntry> = emptyList(),
    val copyText: String
)

object BattleAdvisor {
    fun adviceForRaw(
        context: Context,
        rawText: String
    ): BattleAdvice? {
        if (!looksLikeBattleScreen(rawText)) return null
        return buildAdvice(
            context = context,
            rawText = rawText,
            data = null
        )
    }

    fun adviceFor(
        context: Context,
        data: PokemonScreenData,
        rawText: String
    ): BattleAdvice? {
        if (!looksLikeBattleScreen(rawText, data)) return null
        return buildAdvice(
            context = context,
            rawText = rawText,
            data = data
        )
    }

    private fun buildAdvice(
        context: Context,
        rawText: String,
        data: PokemonScreenData?
    ): BattleAdvice {
        val mode = battleMode(rawText, data)
        val bossName = resolveBossName(context, data, rawText)
        val bossTypes = listOfNotNull(data?.type1, data?.type2)
            .ifEmpty { bossName?.let { findPokemonTypes(context, it) }.orEmpty() }
            .distinct()
            .take(2)
        val source = if (mode == BattleMode.MAX) {
            GameInfoRepository.loadMaxBattleRoster()
        } else {
            GameInfoRepository.loadRaidMetaSnapshot()
        }
        val matchup = GameInfoRepository.matchupAgainst(context, bossTypes)
        val matchupByType = matchup.associate { it.attackType to it.multiplier }
        val sourceOrder = source.mapIndexed { index, entry -> entry.name to index }.toMap()
        val weaknessTypes = matchup
            .filter { it.multiplier > 1.0 }
            .take(6)
            .map { it.attackType }
        val suggestions = if (weaknessTypes.isEmpty()) {
            source.take(10)
        } else {
            source.filter { entry -> entry.attackTypes.any { it in weaknessTypes } }
                .sortedWith(
                    compareByDescending<BattleSuggestionEntry> { entry ->
                        entry.attackTypes.maxOfOrNull { matchupByType[it] ?: 1.0 } ?: 1.0
                    }.thenBy { entry -> sourceOrder[entry.name] ?: Int.MAX_VALUE }
                )
                .ifEmpty { source.take(10) }
        }
        val defenderSuggestions = if (bossTypes.isEmpty()) {
            source.take(10)
        } else {
            val chart = GameInfoRepository.loadTypeEffectiveness(context)
            source.sortedWith(
                compareBy<BattleSuggestionEntry> { entry ->
                    val defenderTypes = entry.attackTypes.ifEmpty { listOf("Normal") }
                    bossTypes.flatMap { bossType ->
                        defenderTypes.map { defenderType ->
                            chart[bossType].orEmpty().getOrDefault(defenderType, 1.0)
                        }
                    }.average()
                }.thenBy { entry -> sourceOrder[entry.name] ?: Int.MAX_VALUE }
            ).take(10)
        }
        val copyText = buildFastCopyText(context, mode, suggestions)

        return BattleAdvice(
            mode = mode,
            bossName = bossName ?: data?.pokemonName,
            bossTypes = bossTypes,
            weaknessTypes = weaknessTypes,
            suggestions = suggestions,
            defenderSuggestions = defenderSuggestions,
            copyText = copyText
        )
    }

    fun findPokemonTypes(context: Context, pokemonName: String): List<String> {
        val normalizedName = normalizeName(stripBattlePrefixes(pokemonName))
        if (normalizedName.isBlank()) return emptyList()
        return GameInfoRepository.loadBattlePokemonIndex(context)
            .firstOrNull { entry -> normalizedName in entry.normalizedNames }
            ?.types
            .orEmpty()
    }

    fun copyTextFor(
        context: Context,
        mode: BattleMode,
        suggestions: List<BattleSuggestionEntry>
    ): String = buildCopyText(context, mode, suggestions)

    fun fastCopyTextFor(
        context: Context,
        mode: BattleMode,
        suggestions: List<BattleSuggestionEntry>
    ): String = buildFastCopyText(context, mode, suggestions)

    private fun buildFastCopyText(
        context: Context,
        mode: BattleMode,
        suggestions: List<BattleSuggestionEntry>
    ): String {
        val suggestionTerms = suggestions
            .flatMap { it.searchTerms }
            .distinct()
        return if (mode == BattleMode.MAX) {
            val allowedMaxTerms = maxAllowedTerms(selectedLanguage(context))
            val maxSuggestionTerms = (suggestionTerms + listOf("zacian", "zamazenta")).distinct()
            "${allowedMaxTerms.joinToString(",")}&${maxSuggestionTerms.joinToString(",")}"
        } else {
            suggestionTerms.joinToString(",")
        }
    }

    private fun buildCopyText(
        context: Context,
        mode: BattleMode,
        suggestions: List<BattleSuggestionEntry>
    ): String {
        val language = selectedLanguage(context)
        val suggestionTerms = suggestions
            .flatMap { it.searchTerms }
            .map { localizedSearchTerm(context, it, language) }
            .distinct()

        return if (mode == BattleMode.MAX) {
            val allowedMaxTerms = maxAllowedTerms(language)
            val maxSuggestionTerms = (suggestionTerms + listOf("zacian", "zamazenta").map {
                localizedSearchTerm(context, it, language)
            }).distinct()
            if (maxSuggestionTerms.isEmpty()) {
                allowedMaxTerms.joinToString(",")
            } else {
                "${allowedMaxTerms.joinToString(",")}&${maxSuggestionTerms.joinToString(",")}"
            }
        } else {
            suggestionTerms.joinToString(",")
        }
    }

    private fun selectedLanguage(context: Context): AppLanguage {
        return context.getSharedPreferences("mewname_prefs", Context.MODE_PRIVATE)
            .getString("app_language", null)
            ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
            ?: GameTextRepository.resolveLanguage()
    }

    private fun localizedSearchTerm(
        context: Context,
        term: String,
        language: AppLanguage
    ): String {
        GameInfoRepository.localizedPokemonSearchTerm(context, term, language)?.let { return it }
        return localizedBattleTag(term, language)
    }

    private fun localizedBattleTag(term: String, language: AppLanguage): String {
        val normalized = term.lowercase(Locale.US).trim()
        return when (normalized) {
            "dmax" -> when (language) {
                AppLanguage.PT_BR -> "dinamax"
                AppLanguage.ES -> "dinamax"
                AppLanguage.EN -> "dynamax"
            }
            "gmax" -> when (language) {
                AppLanguage.PT_BR -> "gigamax"
                AppLanguage.ES -> "gigamax"
                AppLanguage.EN -> "gigantamax"
            }
            "shadow" -> when (language) {
                AppLanguage.PT_BR -> "sombroso"
                AppLanguage.ES -> "oscuro"
                AppLanguage.EN -> "shadow"
            }
            "primal" -> when (language) {
                AppLanguage.PT_BR -> "primordial"
                AppLanguage.ES -> "primigenio"
                AppLanguage.EN -> "primal"
            }
            else -> normalized
        }
    }

    private fun maxAllowedTerms(language: AppLanguage): List<String> {
        return when (language) {
            AppLanguage.PT_BR -> listOf("gigamax", "dinamax", "zacian", "zamazenta")
            AppLanguage.ES -> listOf("gigamax", "dinamax", "zacian", "zamazenta")
            AppLanguage.EN -> listOf("gigantamax", "dynamax", "zacian", "zamazenta")
        }
    }

    private fun looksLikeBattleScreen(
        rawText: String,
        data: PokemonScreenData? = null
    ): Boolean {
        val normalized = normalizeName(rawText)
        val hasBattleAction = Regex("""\bBATALHA\b""").containsMatchIn(normalized) ||
            Regex("""\bBATTLE\b""").containsMatchIn(normalized)
        val hasRaidSignal = listOf(
            "RAID",
            "REIDE",
            "PASSE DE REIDE",
            "RAID PASS",
            "REMOTE RAID"
        ).any { it in normalized }
        val hasLobbySignal = listOf(
            "GRUPO PRIVADO",
            "PRIVATE GROUP",
            "GROUP CODE"
        ).any { it in normalized }
        val hasMaxSignal = listOf(
            "DINAMAX",
            "DYNAMAX",
            "GIGAMAX",
            "GIGANTAMAX",
            "MAX BATTLE",
            "BATALHA MAX"
        ).any { it in normalized }
        val hasPokemonDetailSignal = listOf(
            "PESO",
            "ALTURA",
            "DOCE",
            "DOCES",
            "FORTALECER",
            "EVOLUIR",
            "POEIRA ESTELAR",
            "GINASIOS E REIDES",
            "BATALHAS DE TREINADOR",
            "WEIGHT",
            "HEIGHT",
            "CANDY",
            "CANDIES",
            "POWER UP",
            "EVOLVE",
            "GYMS AND RAIDS",
            "TRAINER BATTLES"
        ).any { it in normalized }
        val hasRealRaidStartSignal = hasBattleAction ||
            hasLobbySignal ||
            listOf("PASSE DE REIDE", "RAID PASS", "REMOTE RAID").any { it in normalized }
        val hasBattleEvolutionFlag = data?.evolutionFlags.orEmpty().any {
            it == EvolutionFlag.DYNAMAX || it == EvolutionFlag.GIGANTAMAX || it == EvolutionFlag.MEGA
        }
        val hasTimerLikeText = Regex("""\b\d{1,2}\s*:\s*\d{2}\s*:\s*\d{2}\b""").containsMatchIn(rawText) ||
            Regex("""\b\d{1,2}\s*:\s*\d{2}\b""").containsMatchIn(rawText)
        if (hasPokemonDetailSignal && !hasMaxSignal && !hasRealRaidStartSignal) {
            return false
        }
        return hasMaxSignal ||
            (hasRaidSignal && hasRealRaidStartSignal) ||
            (hasLobbySignal && (hasBattleAction || hasTimerLikeText)) ||
            (hasBattleEvolutionFlag && (hasBattleAction || hasTimerLikeText))
    }

    private fun battleMode(rawText: String, data: PokemonScreenData? = null): BattleMode {
        val normalized = normalizeName(rawText)
        return if (
            data?.evolutionFlags.orEmpty().any { it == EvolutionFlag.DYNAMAX || it == EvolutionFlag.GIGANTAMAX } ||
            listOf("DINAMAX", "DYNAMAX", "GIGAMAX", "GIGANTAMAX", "MAX BATTLE", "BATALHA MAX").any { it in normalized }
        ) {
            BattleMode.MAX
        } else {
            BattleMode.RAID
        }
    }

    private fun resolveBossName(
        context: Context,
        data: PokemonScreenData?,
        rawText: String
    ): String? {
        data?.pokemonName?.let { return stripBattlePrefixes(it).takeIf(String::isNotBlank) }
        val normalizedRaw = normalizeName(rawText).replace('0', 'O')
        val compactRaw = normalizedRaw.replace(" ", "")
        val rawTokens = normalizedRaw.split(" ").filter { it.length >= 5 }
        return GameInfoRepository.loadBattlePokemonIndex(context)
            .asSequence()
            .flatMap { entry -> entry.normalizedNames.asSequence().map { alias -> entry.name to alias } }
            .filter { (_, alias) ->
                alias.length >= 4 && (
                    alias in normalizedRaw ||
                        alias.replace(" ", "") in compactRaw ||
                        rawTokens.any { token -> isOneCharacterOcrMiss(token, alias) }
                    )
            }
            .maxByOrNull { (_, alias) -> alias.length }
            ?.first
    }

    private fun isOneCharacterOcrMiss(text: String, expected: String): Boolean {
        if (expected.length < 5 || text.length < 5) return false
        if (kotlin.math.abs(text.length - expected.length) > 1) return false
        var textIndex = 0
        var expectedIndex = 0
        var misses = 0
        while (textIndex < text.length && expectedIndex < expected.length) {
            if (text[textIndex] == expected[expectedIndex]) {
                textIndex++
                expectedIndex++
            } else {
                misses++
                if (misses > 1) return false
                when {
                    text.length > expected.length -> textIndex++
                    expected.length > text.length -> expectedIndex++
                    else -> {
                        textIndex++
                        expectedIndex++
                    }
                }
            }
        }
        misses += (text.length - textIndex) + (expected.length - expectedIndex)
        return misses <= 1
    }

    private fun stripBattlePrefixes(text: String): String {
        return text
            .replace(Regex("""(?i)\b(Mega|Primal|Shadow|Dynamax|Dinamax|Gigamax|Gigantamax)\b"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalizeName(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
