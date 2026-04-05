package com.mewname.app.model

data class PokemonScreenData(
    val pokemonName: String? = null,
    val unownLetter: String? = null,
    val uniqueForm: String? = null,
    val candyFamilyName: String? = null,
    val candyDebugInfo: CandyDebugInfo? = null,
    val levelDebugInfo: LevelDebugInfo? = null,
    val genderDebugInfo: GenderDebugInfo? = null,
    val attributeDebugInfo: AttributeDebugInfo? = null,
    val legacyDebugInfo: LegacyDebugInfo? = null,
    val adventureEffectDebugInfo: AdventureEffectDebugInfo? = null,
    val backgroundDebugInfo: BackgroundDebugInfo? = null,
    val uniqueFormDebugInfo: UniqueFormDebugInfo? = null,
    val evolutionIconDebugInfo: EvolutionIconDebugInfo? = null,
    val vivillonDebugInfo: VivillonDebugInfo? = null,
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
    val isShiny: Boolean = false,
    val isShadow: Boolean = false,
    val isPurified: Boolean = false,
    val hasSpecialBackground: Boolean = false,
    val hasAdventureEffect: Boolean = false,
    val size: PokemonSize = PokemonSize.NORMAL,
    val pvpLeague: PvpLeague? = null,
    val pvpRank: Int? = null,
    val pvpLeagueRanks: List<PvpLeagueRankInfo> = emptyList(),
    val familyPvpRanks: List<PvpSpeciesRankInfo> = emptyList(),
    val masterIvBadgeMatch: Boolean? = null,
    val masterIvBadgeDebugInfo: MasterIvBadgeDebugInfo? = null,
    val hasLegacyMove: Boolean = false,
    val evolutionFlags: Set<EvolutionFlag> = emptySet()
)

data class PvpLeagueRankInfo(
    val league: PvpLeague,
    val pokemonName: String? = null,
    val eligible: Boolean = false,
    val rank: Int? = null,
    val bestCp: Int? = null,
    val bestLevel: Double? = null,
    val bestStatProduct: Double? = null,
    val stadiumUrl: String? = null,
    val description: String = ""
)

data class PvpSpeciesRankInfo(
    val pokemonName: String,
    val league: PvpLeague,
    val eligible: Boolean = false,
    val rank: Int? = null,
    val bestCp: Int? = null,
    val bestLevel: Double? = null,
    val bestStatProduct: Double? = null,
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

data class LevelDebugInfo(
    val source: String = "",
    val ocrLevel: Double? = null,
    val curveLevel: Double? = null,
    val finalLevel: Double? = null,
    val pokemonName: String? = null,
    val cp: Int? = null,
    val attackIv: Int? = null,
    val defenseIv: Int? = null,
    val staminaIv: Int? = null,
    val notes: String = ""
)

data class GenderDebugInfo(
    val detectedGender: Gender = Gender.UNKNOWN,
    val iconRect: NormalizedDebugRect? = null,
    val notes: String = ""
)

data class AttributeDebugInfo(
    val typeRegionLines: List<String> = emptyList(),
    val detectedTypes: List<String> = emptyList(),
    val favoriteFilledMatch: Boolean = false,
    val favoriteYellowRatio: Double? = null,
    val purifiedTextMatch: Boolean = false,
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
    val eventBadgeVisualMatch: Boolean = false,
    val luckyTextMatch: Boolean = false,
    val luckyVisualMatch: Boolean = false,
    val shinyParticleMatch: Boolean = false,
    val shadowTextMatch: Boolean = false,
    val shadowParticleMatch: Boolean = false,
    val shadowTextureMatch: Boolean = false,
    val referenceDecision: Boolean? = null,
    val referenceName: String? = null,
    val referenceDistance: Double? = null,
    val specialReferenceName: String? = null,
    val specialReferenceDistance: Double? = null,
    val colorFallbackMatch: Boolean = false,
    val topRegionLines: List<String> = emptyList(),
    val bottomRegionLines: List<String> = emptyList(),
    val notes: String = ""
)

data class UniqueFormDebugInfo(
    val category: String? = null,
    val bestReferenceName: String? = null,
    val bestLabel: String? = null,
    val bestDistance: Double? = null,
    val accepted: Boolean = false,
    val bestCandidateRect: NormalizedDebugRect? = null,
    val candidateRects: List<NormalizedDebugRect> = emptyList(),
    val notes: String = ""
)

data class VivillonDebugInfo(
    val detectedPattern: VivillonPattern? = null,
    val bestReferenceName: String? = null,
    val bestDistance: Double? = null,
    val secondReferenceName: String? = null,
    val secondDistance: Double? = null,
    val accepted: Boolean = false,
    val bestCandidateRect: NormalizedDebugRect? = null,
    val candidateRects: List<NormalizedDebugRect> = emptyList(),
    val notes: String = ""
)

data class EvolutionIconDebugInfo(
    val badgeLines: List<String> = emptyList(),
    val titleLines: List<String> = emptyList(),
    val centerLines: List<String> = emptyList(),
    val actionLines: List<String> = emptyList(),
    val megaKeyword: String? = null,
    val gigantamaxKeyword: String? = null,
    val dynamaxKeyword: String? = null,
    val detectedFlags: List<String> = emptyList(),
    val notes: String = ""
)

data class MasterIvBadgeDebugInfo(
    val supportedIvPercent: Boolean = false,
    val familyMembers: List<String> = emptyList(),
    val expectedAttack: Int? = null,
    val expectedDefense: Int? = null,
    val expectedStamina: Int? = null,
    val isBestMatch: Boolean? = null,
    val notes: String = ""
)

enum class Gender {
    MALE, FEMALE, GENDERLESS, UNKNOWN
}

enum class PvpLeague {
    GREAT, ULTRA, LITTLE, MASTER
}

enum class EvolutionFlag {
    BABY, STAGE1, STAGE2, MEGA, GIGANTAMAX, DYNAMAX, TERASTRAL
}

enum class PokemonSize {
    XXS, XS, NORMAL, XL, XXL
}
