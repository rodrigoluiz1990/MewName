package com.mewname.app.domain

import com.mewname.app.model.Gender
import com.mewname.app.model.PokemonScreenData
import com.mewname.app.model.PokemonSize
import java.text.Normalizer
import java.util.Locale

class PokemonReadSessionMerger {
    fun mergeIfSamePokemon(current: PokemonScreenData, previous: PokemonScreenData?): PokemonScreenData {
        if (previous == null || !isSamePokemon(current, previous)) return current

        return current.copy(
            pokemonName = current.pokemonName ?: previous.pokemonName,
            candyFamilyName = current.candyFamilyName ?: previous.candyFamilyName,
            candyDebugInfo = current.candyDebugInfo ?: previous.candyDebugInfo,
            legacyDebugInfo = current.legacyDebugInfo ?: previous.legacyDebugInfo,
            adventureEffectDebugInfo = current.adventureEffectDebugInfo ?: previous.adventureEffectDebugInfo,
            backgroundDebugInfo = current.backgroundDebugInfo ?: previous.backgroundDebugInfo,
            vivillonPattern = current.vivillonPattern ?: previous.vivillonPattern,
            pokedexNumber = current.pokedexNumber ?: previous.pokedexNumber,
            cp = current.cp ?: previous.cp,
            ivPercent = current.ivPercent ?: previous.ivPercent,
            attIv = current.attIv ?: previous.attIv,
            defIv = current.defIv ?: previous.defIv,
            staIv = current.staIv ?: previous.staIv,
            ivDebugInfo = current.ivDebugInfo ?: previous.ivDebugInfo,
            level = current.level ?: previous.level,
            gender = if (current.gender != Gender.UNKNOWN) current.gender else previous.gender,
            type1 = current.type1 ?: previous.type1,
            type2 = current.type2 ?: previous.type2,
            isFavorite = current.isFavorite || previous.isFavorite,
            isLucky = current.isLucky || previous.isLucky,
            isShadow = current.isShadow || previous.isShadow,
            isPurified = current.isPurified || previous.isPurified,
            hasSpecialBackground = current.hasSpecialBackground || previous.hasSpecialBackground,
            hasAdventureEffect = current.hasAdventureEffect || previous.hasAdventureEffect,
            size = if (current.size != PokemonSize.NORMAL) current.size else previous.size,
            pvpLeague = current.pvpLeague ?: previous.pvpLeague,
            pvpRank = current.pvpRank ?: previous.pvpRank,
            pvpLeagueRanks = if (current.pvpLeagueRanks.isNotEmpty()) current.pvpLeagueRanks else previous.pvpLeagueRanks,
            hasLegacyMove = current.hasLegacyMove || previous.hasLegacyMove,
            evolutionFlags = current.evolutionFlags + previous.evolutionFlags
        )
    }

    private fun isSamePokemon(current: PokemonScreenData, previous: PokemonScreenData): Boolean {
        val sameCp = current.cp != null && previous.cp != null && current.cp == previous.cp
        val currentName = normalize(current.pokemonName)
        val previousName = normalize(previous.pokemonName)
        val currentFamily = normalize(current.candyFamilyName)
        val previousFamily = normalize(previous.candyFamilyName)

        val sameResolvedName = currentName != null && previousName != null && currentName == previousName
        val sameFamily = currentFamily != null && previousFamily != null && currentFamily == previousFamily
        val familyMatchesName = sameCp && (
            (currentFamily != null && currentFamily == previousName) ||
                (previousFamily != null && previousFamily == currentName)
            )

        return sameResolvedName || sameFamily || (sameCp && (sameResolvedName || sameFamily || familyMatchesName))
    }

    private fun normalize(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9]"), "")
            .ifBlank { null }
    }
}
