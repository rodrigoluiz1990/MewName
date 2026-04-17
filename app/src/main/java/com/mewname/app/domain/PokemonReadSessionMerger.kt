package com.mewname.app.domain

import com.mewname.app.model.Gender
import com.mewname.app.model.LevelDebugInfo
import com.mewname.app.model.PokemonScreenData
import com.mewname.app.model.PokemonSize
import java.text.Normalizer
import java.util.Locale

class PokemonReadSessionMerger {
    fun mergeIfSamePokemon(current: PokemonScreenData, previous: PokemonScreenData?): PokemonScreenData {
        if (previous == null || !isSamePokemon(current, previous)) return current

        return current.copy(
            pokemonName = current.pokemonName ?: previous.pokemonName,
            unownLetter = current.unownLetter ?: previous.unownLetter,
            uniqueForm = current.uniqueForm ?: previous.uniqueForm,
            candyFamilyName = current.candyFamilyName ?: previous.candyFamilyName,
            candyDebugInfo = current.candyDebugInfo ?: previous.candyDebugInfo,
            levelDebugInfo = mergeLevelDebugInfo(current.levelDebugInfo, previous.levelDebugInfo),
            genderDebugInfo = current.genderDebugInfo ?: previous.genderDebugInfo,
            attributeDebugInfo = current.attributeDebugInfo ?: previous.attributeDebugInfo,
            legacyDebugInfo = current.legacyDebugInfo ?: previous.legacyDebugInfo,
            adventureEffectDebugInfo = current.adventureEffectDebugInfo ?: previous.adventureEffectDebugInfo,
            backgroundDebugInfo = current.backgroundDebugInfo ?: previous.backgroundDebugInfo,
            uniqueFormDebugInfo = current.uniqueFormDebugInfo ?: previous.uniqueFormDebugInfo,
            evolutionIconDebugInfo = current.evolutionIconDebugInfo ?: previous.evolutionIconDebugInfo,
            vivillonDebugInfo = current.vivillonDebugInfo ?: previous.vivillonDebugInfo,
            vivillonPattern = current.vivillonPattern ?: previous.vivillonPattern,
            pokedexNumber = current.pokedexNumber ?: previous.pokedexNumber,
            cp = current.cp ?: previous.cp,
            ivPercent = current.ivPercent ?: previous.ivPercent,
            attIv = current.attIv ?: previous.attIv,
            defIv = current.defIv ?: previous.defIv,
            staIv = current.staIv ?: previous.staIv,
            ivDebugInfo = current.ivDebugInfo ?: previous.ivDebugInfo,
            level = current.level ?: previous.level,
            gender = when (current.gender) {
                Gender.UNKNOWN -> previous.gender
                else -> current.gender
            },
            type1 = current.type1 ?: previous.type1,
            type2 = current.type2 ?: previous.type2,
            isFavorite = current.isFavorite || previous.isFavorite,
            isLucky = current.isLucky || previous.isLucky,
            isShiny = current.isShiny || previous.isShiny,
            isShadow = current.isShadow || previous.isShadow,
            isPurified = current.isPurified || previous.isPurified,
            hasSpecialBackground = if (current.backgroundDebugInfo != null) current.hasSpecialBackground else current.hasSpecialBackground || previous.hasSpecialBackground,
            hasAdventureEffect = if (current.adventureEffectDebugInfo != null) current.hasAdventureEffect else current.hasAdventureEffect || previous.hasAdventureEffect,
            size = if (current.size != PokemonSize.NORMAL) current.size else previous.size,
            pvpLeague = current.pvpLeague ?: previous.pvpLeague,
            pvpRank = current.pvpRank ?: previous.pvpRank,
            pvpPokemonName = current.pvpPokemonName ?: previous.pvpPokemonName,
            pvpLeagueRanks = if (current.pvpLeagueRanks.isNotEmpty()) current.pvpLeagueRanks else previous.pvpLeagueRanks,
            familyPvpRanks = if (current.familyPvpRanks.isNotEmpty()) current.familyPvpRanks else previous.familyPvpRanks,
            masterIvBadgeMatch = current.masterIvBadgeMatch ?: previous.masterIvBadgeMatch,
            masterIvBadgeDebugInfo = current.masterIvBadgeDebugInfo ?: previous.masterIvBadgeDebugInfo,
            hasLegacyMove = if (current.legacyDebugInfo != null) current.hasLegacyMove else current.hasLegacyMove || previous.hasLegacyMove,
            evolutionFlags = if (current.evolutionFlags.isNotEmpty()) current.evolutionFlags else previous.evolutionFlags
        )
    }

    private fun isSamePokemon(current: PokemonScreenData, previous: PokemonScreenData): Boolean {
        val sameCp = current.cp != null && previous.cp != null && current.cp == previous.cp
        val sameIv = current.attIv != null && current.attIv == previous.attIv &&
            current.defIv != null && current.defIv == previous.defIv &&
            current.staIv != null && current.staIv == previous.staIv
        val currentName = normalize(current.pokemonName)
        val previousName = normalize(previous.pokemonName)
        val currentFamily = normalize(current.candyFamilyName)
        val previousFamily = normalize(previous.candyFamilyName)

        val sameResolvedName = currentName != null && previousName != null && currentName == previousName
        val sameFamily = currentFamily != null && previousFamily != null && currentFamily == previousFamily
        val oneCpMissing = current.cp == null || previous.cp == null
        val oneNameMissing = currentName == null || previousName == null
        val familyMatchesName = sameCp && (
            (currentFamily != null && currentFamily == previousName) ||
                (previousFamily != null && previousFamily == currentName)
            )
        val familyCompatibleWithoutCp = sameFamily && (sameIv || oneCpMissing || oneNameMissing)
        val sameNameWithPartialData = sameResolvedName && (sameIv || oneCpMissing || oneNameMissing)

        return when {
            sameCp -> sameResolvedName || sameFamily || familyMatchesName
            sameNameWithPartialData -> true
            familyCompatibleWithoutCp -> true
            sameResolvedName && sameIv -> true
            else -> false
        }
    }

    private fun normalize(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9]"), "")
            .ifBlank { null }
    }

    private fun mergeLevelDebugInfo(current: LevelDebugInfo?, previous: LevelDebugInfo?): LevelDebugInfo? {
        if (current == null) return previous
        if (previous == null) return current
        return LevelDebugInfo(
            source = current.source.ifBlank { previous.source },
            ocrLevel = current.ocrLevel ?: previous.ocrLevel,
            curveLevel = current.curveLevel ?: previous.curveLevel,
            finalLevel = current.finalLevel ?: previous.finalLevel,
            pokemonName = current.pokemonName ?: previous.pokemonName,
            cp = current.cp ?: previous.cp,
            attackIv = current.attackIv ?: previous.attackIv,
            defenseIv = current.defenseIv ?: previous.defenseIv,
            staminaIv = current.staminaIv ?: previous.staminaIv,
            notes = current.notes.ifBlank { previous.notes }
        )
    }
}
