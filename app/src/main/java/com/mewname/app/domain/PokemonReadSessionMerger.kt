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
        val trustCurrentIv = hasTrustedIvRead(current)
        val mergedLegacyMove = current.hasLegacyMove || previous.hasLegacyMove
        val mergedMasterIvBadgeMatch = mergePositiveBadgeMatch(
            current = current.masterIvBadgeMatch,
            previous = previous.masterIvBadgeMatch
        )

        return current.copy(
            pokemonName = mergePokemonName(current, previous),
            unownLetter = current.unownLetter ?: previous.unownLetter,
            uniqueForm = current.uniqueForm ?: previous.uniqueForm,
            candyFamilyName = mergeCandyFamilyName(current, previous),
            candyDebugInfo = current.candyDebugInfo ?: previous.candyDebugInfo,
            levelDebugInfo = mergeLevelDebugInfo(current.levelDebugInfo, previous.levelDebugInfo),
            genderDebugInfo = current.genderDebugInfo ?: previous.genderDebugInfo,
            attributeDebugInfo = current.attributeDebugInfo ?: previous.attributeDebugInfo,
            legacyDebugInfo = mergeLegacyDebugInfo(current, previous, mergedLegacyMove),
            adventureEffectDebugInfo = current.adventureEffectDebugInfo ?: previous.adventureEffectDebugInfo,
            backgroundDebugInfo = current.backgroundDebugInfo ?: previous.backgroundDebugInfo,
            uniqueFormDebugInfo = current.uniqueFormDebugInfo ?: previous.uniqueFormDebugInfo,
            evolutionIconDebugInfo = current.evolutionIconDebugInfo ?: previous.evolutionIconDebugInfo,
            vivillonDebugInfo = current.vivillonDebugInfo ?: previous.vivillonDebugInfo,
            vivillonPattern = current.vivillonPattern ?: previous.vivillonPattern,
            pokedexNumber = current.pokedexNumber ?: previous.pokedexNumber,
            cp = current.cp ?: previous.cp,
            ivPercent = if (trustCurrentIv) current.ivPercent ?: previous.ivPercent else previous.ivPercent,
            attIv = if (trustCurrentIv) current.attIv ?: previous.attIv else previous.attIv,
            defIv = if (trustCurrentIv) current.defIv ?: previous.defIv else previous.defIv,
            staIv = if (trustCurrentIv) current.staIv ?: previous.staIv else previous.staIv,
            ivDebugInfo = if (trustCurrentIv) current.ivDebugInfo ?: previous.ivDebugInfo else previous.ivDebugInfo,
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
            sizeDebugInfo = current.sizeDebugInfo ?: previous.sizeDebugInfo,
            pvpLeague = current.pvpLeague ?: previous.pvpLeague,
            pvpRank = current.pvpRank ?: previous.pvpRank,
            pvpPokemonName = current.pvpPokemonName ?: previous.pvpPokemonName,
            pvpLeagueRanks = if (current.pvpLeagueRanks.isNotEmpty()) current.pvpLeagueRanks else previous.pvpLeagueRanks,
            familyPvpRanks = if (current.familyPvpRanks.isNotEmpty()) current.familyPvpRanks else previous.familyPvpRanks,
            masterIvBadgeMatch = mergedMasterIvBadgeMatch,
            masterIvBadgeDebugInfo = mergeMasterIvBadgeDebugInfo(current, previous, mergedMasterIvBadgeMatch),
            hasLegacyMove = mergedLegacyMove,
            evolutionFlags = if (current.evolutionFlags.isNotEmpty()) current.evolutionFlags else previous.evolutionFlags
        )
    }

    private fun mergePositiveBadgeMatch(current: Boolean?, previous: Boolean?): Boolean? {
        return when {
            current == true || previous == true -> true
            current != null -> current
            else -> previous
        }
    }

    private fun mergeMasterIvBadgeDebugInfo(
        current: PokemonScreenData,
        previous: PokemonScreenData,
        mergedMatch: Boolean?
    ) = when {
        mergedMatch == true && current.masterIvBadgeMatch == true -> current.masterIvBadgeDebugInfo ?: previous.masterIvBadgeDebugInfo
        mergedMatch == true && previous.masterIvBadgeMatch == true -> previous.masterIvBadgeDebugInfo ?: current.masterIvBadgeDebugInfo
        else -> current.masterIvBadgeDebugInfo ?: previous.masterIvBadgeDebugInfo
    }

    private fun mergeLegacyDebugInfo(
        current: PokemonScreenData,
        previous: PokemonScreenData,
        mergedLegacyMove: Boolean
    ) = when {
        mergedLegacyMove && current.hasLegacyMove -> current.legacyDebugInfo ?: previous.legacyDebugInfo
        mergedLegacyMove && previous.hasLegacyMove -> previous.legacyDebugInfo ?: current.legacyDebugInfo
        else -> current.legacyDebugInfo ?: previous.legacyDebugInfo
    }

    private fun hasTrustedIvRead(data: PokemonScreenData): Boolean {
        val hasIvValues = data.ivPercent != null || data.attIv != null || data.defIv != null || data.staIv != null
        if (!hasIvValues) return false
        val debug = data.ivDebugInfo ?: return true
        return debug.appraisalDetected
    }

    private fun mergePokemonName(current: PokemonScreenData, previous: PokemonScreenData): String? {
        val currentName = normalize(current.pokemonName)
        val previousName = normalize(previous.pokemonName)
        val currentFamily = normalize(current.candyFamilyName)
        val previousFamily = normalize(previous.candyFamilyName)
        val sameCp = current.cp != null && previous.cp != null && current.cp == previous.cp

        if (currentName == null) return previous.pokemonName
        if (previousName == null || currentName == previousName) return current.pokemonName
        if (sameCp && currentFamily != null && previousFamily != null && currentFamily == previousFamily) {
            return previous.pokemonName
        }
        return current.pokemonName
    }

    private fun mergeCandyFamilyName(current: PokemonScreenData, previous: PokemonScreenData): String? {
        val currentFamily = normalize(current.candyFamilyName)
        val previousFamily = normalize(previous.candyFamilyName)
        val previousName = normalize(previous.pokemonName)
        val sameCp = current.cp != null && previous.cp != null && current.cp == previous.cp

        if (currentFamily == null) return previous.candyFamilyName
        if (previousFamily == null || currentFamily == previousFamily) return current.candyFamilyName
        if (sameCp && currentFamily == previousName) return previous.candyFamilyName
        return current.candyFamilyName
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
        val currentLooksLikeIncompleteReadOfPrevious = sameCp &&
            currentName == null &&
            previousName != null &&
            currentFamily != null &&
            (currentFamily == previousFamily || currentFamily == previousName)
        val familyCompatibleWithoutCp = sameFamily && (sameIv || oneCpMissing || oneNameMissing)
        val sameNameWithPartialData = sameResolvedName && (sameIv || oneCpMissing || oneNameMissing)

        return when {
            sameCp -> sameResolvedName || sameFamily || familyMatchesName || currentLooksLikeIncompleteReadOfPrevious
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
