package com.mewname.app.model

import java.util.UUID

data class NamingConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Padrão",
    val maxLength: Int = 12,
    val blocks: List<NamingBlock> = defaultPatternBlocks(),
    val fields: List<NamingField> = emptyList(),
    val symbols: Map<String, String> = defaultSymbols(),
    val customSeparator: String = ""
)

data class NamingBlock(
    val id: String = UUID.randomUUID().toString(),
    val type: NamingBlockType,
    val field: NamingField? = null,
    val fixedText: String = ""
) {
    fun label(): String = when (type) {
        NamingBlockType.VARIABLE -> field?.label ?: "Campo"
        NamingBlockType.FIXED_TEXT -> if (fixedText.isBlank()) "Texto fixo" else "\"$fixedText\""
    }
}

enum class NamingBlockType {
    VARIABLE,
    FIXED_TEXT
}

enum class NamingField(val label: String) {
    POKEMON_NAME("Nome"),
    UNOWN_LETTER("Letra Unown"),
    UNIQUE_FORM("Forma Única"),
    VIVILLON_PATTERN("Padrão Vivillon"),
    POKEDEX_NUMBER("Nº Pokédex"),
    CP("CP"),
    IV_PERCENT("IV Médio"),
    IV_COMBINATION("IV A/D/S"),
    LEVEL("Nível"),
    GENDER("Gênero"),
    TYPE("Tipo"),
    FAVORITE("Favorito"),
    LUCKY("Sortudo"),
    SHADOW("Sombrio"),
    PURIFIED("Purificado"),
    SPECIAL_BACKGROUND("Fundo Especial"),
    ADVENTURE_EFFECT("Efeito Aventura"),
    SIZE("Tamanho"),
    MASTER_IV_BADGE("IV Master"),
    PVP_LEAGUE("Liga PvP"),
    PVP_RANK("Ranking PvP"),
    LEGACY_MOVE("Ataque Legado"),
    LEGACY_MOVE_NAME("Nome Ataque Legado"),
    EVOLUTION_TYPE("Evolução")
}

fun defaultPatternBlocks() = listOf(
    NamingBlock(type = NamingBlockType.VARIABLE, field = NamingField.SPECIAL_BACKGROUND),
    NamingBlock(type = NamingBlockType.VARIABLE, field = NamingField.ADVENTURE_EFFECT),
    NamingBlock(type = NamingBlockType.VARIABLE, field = NamingField.SIZE),
    NamingBlock(type = NamingBlockType.VARIABLE, field = NamingField.IV_PERCENT),
    NamingBlock(type = NamingBlockType.VARIABLE, field = NamingField.POKEMON_NAME),
    NamingBlock(type = NamingBlockType.VARIABLE, field = NamingField.EVOLUTION_TYPE),
    NamingBlock(type = NamingBlockType.VARIABLE, field = NamingField.LEGACY_MOVE)
)

fun defaultSymbols() = mapOf(
    "MALE" to "\u2642",
    "FEMALE" to "\u2640",
    "TYPE_NORMAL" to "NOR",
    "TYPE_FIRE" to "FOG",
    "TYPE_WATER" to "AGU",
    "TYPE_GRASS" to "PLA",
    "TYPE_ELECTRIC" to "ELE",
    "TYPE_ICE" to "GEL",
    "TYPE_FIGHTING" to "LUT",
    "TYPE_POISON" to "VEN",
    "TYPE_GROUND" to "TER",
    "TYPE_FLYING" to "VOA",
    "TYPE_PSYCHIC" to "PSI",
    "TYPE_BUG" to "INS",
    "TYPE_ROCK" to "ROC",
    "TYPE_GHOST" to "FAN",
    "TYPE_DRAGON" to "DRA",
    "TYPE_DARK" to "SOM",
    "TYPE_STEEL" to "ACO",
    "TYPE_FAIRY" to "FAD",
    "FAVORITE" to "*",
    "LUCKY" to "+",
    "SHADOW" to "SH",
    "PURIFIED" to "PU",
    "SPECIAL_BACKGROUND" to "\u2605FE",
    "ADVENTURE_EFFECT" to "\u2605AV",
    "XXL" to "\u2605XXL",
    "XL" to "\u2605XL",
    "XS" to "\u2605XS",
    "XXS" to "\u2605XXS",
    "MASTER_IV_MATCH" to "tm",
    "MASTER_IV_OTHER" to "●",
    "GREAT_LEAGUE" to "GL",
    "ULTRA_LEAGUE" to "UL",
    "LITTLE_LEAGUE" to "CP",
    "MASTER_LEAGUE" to "ML",
    "LEGACY" to "\u24C1",
    "BABY" to "BY",
    "STAGE1" to "¹",
    "STAGE2" to "²",
    "MEGA" to "\u24C2",
    "GIGANTAMAX" to "\u24BC",
    "DYNAMAX" to "\u24B9",
    "VIVILLON_ARCHIPELAGO" to "ARC",
    "VIVILLON_CONTINENTAL" to "CON",
    "VIVILLON_ELEGANT" to "ELE",
    "VIVILLON_FANCY" to "FAN",
    "VIVILLON_GARDEN" to "GAR",
    "VIVILLON_HIGH_PLAINS" to "HPL",
    "VIVILLON_ICY_SNOW" to "ISN",
    "VIVILLON_JUNGLE" to "JUN",
    "VIVILLON_MARINE" to "MAR",
    "VIVILLON_MEADOW" to "MEA",
    "VIVILLON_MODERN" to "MOD",
    "VIVILLON_MONSOON" to "MON",
    "VIVILLON_OCEAN" to "OCE",
    "VIVILLON_POKE_BALL" to "PB",
    "VIVILLON_POLAR" to "POL",
    "VIVILLON_RIVER" to "RIV",
    "VIVILLON_SANDSTORM" to "SAN",
    "VIVILLON_SAVANNA" to "SAV",
    "VIVILLON_SUN" to "SUN",
    "VIVILLON_TUNDRA" to "TUN"
)

fun NamingConfig.effectiveBlocks(): List<NamingBlock> {
    if (blocks.isNotEmpty()) return blocks
    if (fields.isNotEmpty()) {
        return fields.map { field ->
            NamingBlock(type = NamingBlockType.VARIABLE, field = field)
        }
    }
    return emptyList()
}

fun NamingConfig.hasVisibleSizeSymbol(size: PokemonSize): Boolean {
    val key = when (size) {
        PokemonSize.XXL -> "XXL"
        PokemonSize.XL -> "XL"
        PokemonSize.XS -> "XS"
        PokemonSize.XXS -> "XXS"
        PokemonSize.NORMAL -> return false
    }
    return !symbols[key].isNullOrBlank()
}

fun NamingConfig.hasVisibleEvolutionSymbol(flag: EvolutionFlag): Boolean {
    val key = when (flag) {
        EvolutionFlag.BABY -> "BABY"
        EvolutionFlag.STAGE1 -> "STAGE1"
        EvolutionFlag.STAGE2 -> "STAGE2"
        EvolutionFlag.MEGA -> "MEGA"
        EvolutionFlag.GIGANTAMAX -> "GIGANTAMAX"
        EvolutionFlag.DYNAMAX -> "DYNAMAX"
        EvolutionFlag.TERASTRAL -> "TERASTAL"
    }
    return !symbols[key].isNullOrBlank()
}

fun List<NamingConfig>.hasVisibleSizeSymbol(size: PokemonSize): Boolean =
    any { it.hasVisibleSizeSymbol(size) }

fun List<NamingConfig>.visibleEvolutionFlags(): Set<EvolutionFlag> =
    EvolutionFlag.entries.filterTo(linkedSetOf()) { flag ->
        any { it.hasVisibleEvolutionSymbol(flag) }
    }
