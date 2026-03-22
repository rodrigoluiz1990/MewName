package com.mewname.app.domain

import com.mewname.app.model.EvolutionFlag
import com.mewname.app.model.Gender
import com.mewname.app.model.NamingBlockType
import com.mewname.app.model.NamingConfig
import com.mewname.app.model.NamingField
import com.mewname.app.model.PokemonScreenData
import com.mewname.app.model.PvpLeague
import com.mewname.app.model.PokemonSize
import com.mewname.app.model.VivillonPattern
import com.mewname.app.model.effectiveBlocks

class NameGenerator {
    fun generate(data: PokemonScreenData, config: NamingConfig): String {
        val blocks = config.effectiveBlocks()
        val resolvedValues = blocks.map { block ->
            when (block.type) {
                NamingBlockType.FIXED_TEXT -> block.fixedText.takeIf { it.isNotBlank() }
                NamingBlockType.VARIABLE -> resolveField(block.field, data, config)
            }
        }

        val activeSegments = mutableListOf<ResolvedSegment>()

        blocks.forEachIndexed { index, block ->
            val value = resolvedValues[index]
            if (value.isNullOrEmpty()) return@forEachIndexed

            if (block.type == NamingBlockType.FIXED_TEXT && shouldSkipFixedText(index, block, blocks, resolvedValues)) {
                return@forEachIndexed
            }

            activeSegments += ResolvedSegment(
                field = block.field,
                type = block.type,
                value = value
            )
        }

        if (activeSegments.isEmpty()) return ""

        val maxLength = config.maxLength
        val initial = activeSegments.joinToString(separator = "") { it.value }.trim()
        if (initial.length <= maxLength) return initial

        val nameIndex = activeSegments.indexOfFirst { it.field == NamingField.POKEMON_NAME }
        if (nameIndex == -1) {
            return initial.take(maxLength)
        }

        val mutableSegments = activeSegments.toMutableList()
        val originalName = mutableSegments[nameIndex].value
        var allowedNameLength = originalName.length

        while (allowedNameLength >= 0) {
            mutableSegments[nameIndex] = mutableSegments[nameIndex].copy(
                value = originalName.take(allowedNameLength)
            )

            val candidate = mutableSegments
                .filter { it.value.isNotEmpty() }
                .joinToString(separator = "") { it.value }
                .trim()

            if (candidate.length <= maxLength) {
                return candidate
            }

            allowedNameLength--
        }

        return mutableSegments
            .filter { it.value.isNotEmpty() }
            .joinToString(separator = "") { it.value }
            .trim()
            .take(maxLength)
    }

    private fun resolveField(field: NamingField?, data: PokemonScreenData, config: NamingConfig): String? {
        return when (field) {
            NamingField.POKEMON_NAME -> data.pokemonName?.take(14)
            NamingField.VIVILLON_PATTERN -> vivillonPatternSymbol(data.vivillonPattern, config)
            NamingField.POKEDEX_NUMBER -> data.pokedexNumber?.let { "#$it" }
            NamingField.CP -> data.cp?.toString()
            NamingField.IV_PERCENT -> data.ivPercent?.let(::toSuperscript)
            NamingField.IV_COMBINATION -> if (data.attIv != null && data.defIv != null && data.staIv != null) {
                "${data.attIv}/${data.defIv}/${data.staIv}"
            } else null
            NamingField.LEVEL -> data.level?.let { "L${it.toInt()}" }
            NamingField.GENDER -> when (data.gender) {
                Gender.MALE -> config.symbols["MALE"]
                Gender.FEMALE -> config.symbols["FEMALE"]
                else -> null
            }
            NamingField.TYPE -> data.type1?.take(3)
            NamingField.FAVORITE -> if (data.isFavorite) config.symbols["FAVORITE"] else null
            NamingField.LUCKY -> if (data.isLucky) config.symbols["LUCKY"] else null
            NamingField.SHADOW -> if (data.isShadow) config.symbols["SHADOW"] else null
            NamingField.PURIFIED -> if (data.isPurified) config.symbols["PURIFIED"] else null
            NamingField.SPECIAL_BACKGROUND -> if (data.hasSpecialBackground) config.symbols["SPECIAL_BACKGROUND"] else null
            NamingField.ADVENTURE_EFFECT -> if (data.hasAdventureEffect) config.symbols["ADVENTURE_EFFECT"] else null
            NamingField.SIZE -> when (data.size) {
                PokemonSize.XXL -> config.symbols["XXL"]
                PokemonSize.XXS -> config.symbols["XXS"]
                else -> null
            }
            NamingField.PVP_LEAGUE -> when (data.pvpLeague) {
                PvpLeague.GREAT -> config.symbols["GREAT_LEAGUE"]
                PvpLeague.ULTRA -> config.symbols["ULTRA_LEAGUE"]
                PvpLeague.LITTLE -> config.symbols["LITTLE_LEAGUE"]
                else -> null
            }
            NamingField.PVP_RANK -> data.pvpRank?.toString()
            NamingField.LEGACY_MOVE -> if (data.hasLegacyMove) config.symbols["LEGACY"] else null
            NamingField.EVOLUTION_TYPE -> evolutionSymbols(data, config)
            null -> null
        }
    }

    private fun evolutionSymbols(data: PokemonScreenData, config: NamingConfig): String? {
        val parts = buildList {
            if (EvolutionFlag.MEGA in data.evolutionFlags) add(config.symbols["MEGA"].orEmpty())
            if (EvolutionFlag.GIGANTAMAX in data.evolutionFlags) add(config.symbols["GIGANTAMAX"].orEmpty())
            if (EvolutionFlag.DYNAMAX in data.evolutionFlags) add(config.symbols["DYNAMAX"].orEmpty())
        }.filter { it.isNotBlank() }

        return parts.joinToString("")
            .ifBlank { null }
    }

    private fun vivillonPatternSymbol(pattern: VivillonPattern?, config: NamingConfig): String? {
        val vivillonPattern = pattern ?: return null
        return config.symbols[vivillonPattern.symbolKey]
            ?.takeIf { it.isNotBlank() }
            ?: vivillonPattern.label.take(3).uppercase()
    }

    private fun shouldSkipFixedText(
        index: Int,
        block: com.mewname.app.model.NamingBlock,
        blocks: List<com.mewname.app.model.NamingBlock>,
        resolvedValues: List<String?>
    ): Boolean {
        if (!isOptionalSeparator(block.fixedText)) return false

        val previousHasValue = resolvedValues
            .take(index)
            .indexOfLast { !it.isNullOrBlank() } != -1
        val nextHasValue = resolvedValues
            .drop(index + 1)
            .any { !it.isNullOrBlank() }

        val previousVariableMissing = blocks
            .take(index)
            .lastOrNull()?.type == NamingBlockType.VARIABLE &&
            resolvedValues.getOrNull(index - 1).isNullOrBlank()
        val nextVariableMissing = blocks
            .drop(index + 1)
            .firstOrNull()?.type == NamingBlockType.VARIABLE &&
            resolvedValues.getOrNull(index + 1).isNullOrBlank()

        if (!previousHasValue || !nextHasValue) return true
        if (previousVariableMissing || nextVariableMissing) return true
        return false
    }

    private fun isOptionalSeparator(fixedText: String): Boolean {
        val trimmed = fixedText.trim()
        if (trimmed.isEmpty()) return true
        return trimmed in setOf("-", "_", "/", "|", ".", ":", "·", "•", "(", ")", "[", "]")
    }

    private fun toSuperscript(value: Int): String {
        val digits = mapOf(
            '0' to '⁰',
            '1' to '¹',
            '2' to '²',
            '3' to '³',
            '4' to '⁴',
            '5' to '⁵',
            '6' to '⁶',
            '7' to '⁷',
            '8' to '⁸',
            '9' to '⁹'
        )
        return value.toString().map { digits[it] ?: it }.joinToString("")
    }

    private data class ResolvedSegment(
        val field: NamingField?,
        val type: NamingBlockType,
        val value: String
    )
}
