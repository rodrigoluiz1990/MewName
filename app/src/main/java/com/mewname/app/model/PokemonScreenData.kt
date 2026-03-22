package com.mewname.app.model

data class PokemonScreenData(
    val pokemonName: String? = null,
    val candyFamilyName: String? = null,
    val candyDebugInfo: CandyDebugInfo? = null,
    val legacyDebugInfo: LegacyDebugInfo? = null,
    val adventureEffectDebugInfo: AdventureEffectDebugInfo? = null,
    val backgroundDebugInfo: BackgroundDebugInfo? = null,
    val vivillonPattern: VivillonPattern? = null,
    val pokedexNumber: Int? = null,
    val cp: Int? = null,
    val ivPercent: Int? = null,
    val attIv: Int? = null,
    val defIv: Int? = null,
    val staIv: Int? = null,
    val ivDebugInfo: IvDebugInfo? = null,
    val level: Double? = null,
    val gender: Gender = Gender.UNKNOWN,
    val type1: String? = null,
    val type2: String? = null,
    val isFavorite: Boolean = false,
    val isLucky: Boolean = false,
    val isShadow: Boolean = false,
    val isPurified: Boolean = false,
    val hasSpecialBackground: Boolean = false,
    val hasAdventureEffect: Boolean = false,
    val size: PokemonSize = PokemonSize.NORMAL,
    val pvpLeague: PvpLeague? = null,
    val pvpRank: Int? = null,
    val pvpLeagueRanks: List<PvpLeagueRankInfo> = emptyList(),
    val hasLegacyMove: Boolean = false,
    val evolutionFlags: Set<EvolutionFlag> = emptySet()
)

data class PvpLeagueRankInfo(
    val league: PvpLeague,
    val eligible: Boolean = false,
    val rank: Int? = null,
    val bestCp: Int? = null,
    val bestLevel: Double? = null,
    val stadiumUrl: String? = null,
    val description: String = ""
)

data class CandyDebugInfo(
    val regionLineCount: Int = 0,
    val regionLines: List<String> = emptyList(),
    val matchedLine: String? = null,
    val extractedFamilyRaw: String? = null,
    val resolvedFamilyName: String? = null,
    val notes: String = ""
)

data class IvDebugInfo(
    val attackRatio: Float? = null,
    val defenseRatio: Float? = null,
    val staminaRatio: Float? = null,
    val attackDetected: Int? = null,
    val defenseDetected: Int? = null,
    val staminaDetected: Int? = null,
    val attackMeasurementDebug: String = "",
    val defenseMeasurementDebug: String = "",
    val staminaMeasurementDebug: String = "",
    val detectedBars: Int = 0,
    val reliable: Boolean = false,
    val appraisalDetected: Boolean = false,
    val percentFromOcr: Int? = null,
    val percentFinal: Int? = null,
    val appraisalPanelRect: NormalizedDebugRect? = null,
    val attackBarRect: NormalizedDebugRect? = null,
    val defenseBarRect: NormalizedDebugRect? = null,
    val staminaBarRect: NormalizedDebugRect? = null,
    val notes: String = ""
)

data class NormalizedDebugRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class LegacyDebugInfo(
    val moveRegionLines: List<String> = emptyList(),
    val extractedMoves: List<String> = emptyList(),
    val matchedKeyword: String? = null,
    val matchedLegacyMove: String? = null,
    val matchedAgainstPokemon: String? = null,
    val notes: String = ""
)

data class AdventureEffectDebugInfo(
    val moveRegionLines: List<String> = emptyList(),
    val upperBadgeLines: List<String> = emptyList(),
    val extractedMoves: List<String> = emptyList(),
    val matchedKeyword: String? = null,
    val matchedPokemon: String? = null,
    val matchedMove: String? = null,
    val matchedEffectName: String? = null,
    val notes: String = ""
)

data class BackgroundDebugInfo(
    val textMatch: Boolean = false,
    val topRegionMatch: Boolean = false,
    val referenceDecision: Boolean? = null,
    val referenceName: String? = null,
    val referenceDistance: Double? = null,
    val colorFallbackMatch: Boolean = false,
    val topRegionLines: List<String> = emptyList(),
    val bottomRegionLines: List<String> = emptyList(),
    val notes: String = ""
)

enum class Gender {
    MALE, FEMALE, GENDERLESS, UNKNOWN
}

enum class PvpLeague {
    GREAT, ULTRA, LITTLE, MASTER
}

enum class EvolutionFlag {
    MEGA, GIGANTAMAX, DYNAMAX
}

enum class PokemonSize {
    XXS, NORMAL, XXL
}
