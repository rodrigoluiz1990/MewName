package com.mewname.app.model

import java.util.UUID

data class NamingConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Padrao",
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
    POKEMON_NAME("Coloque o Nome"),
    VIVILLON_PATTERN("Padrao Vivillon"),
    POKEDEX_NUMBER("N Pokedex"),
    CP("CP"),
    IV_PERCENT("IV Medio"),
    IV_COMBINATION("IV A/D/S"),
    LEVEL("Nivel"),
    GENDER("Genero"),
    TYPE("Tipo"),
    FAVORITE("Favorito"),
    LUCKY("Sortudo"),
    SHADOW("Sombrio"),
    PURIFIED("Purificado"),
    SPECIAL_BACKGROUND("Fundo Especial"),
    ADVENTURE_EFFECT("Efeito Aventura"),
    SIZE("Tamanho"),
    PVP_LEAGUE("Liga PVP"),
    PVP_RANK("Ranking PVP"),
    LEGACY_MOVE("Antigo"),
    EVOLUTION_TYPE("Evolucao")
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
    "FAVORITE" to "*",
    "LUCKY" to "+",
    "SHADOW" to "SH",
    "PURIFIED" to "PU",
    "SPECIAL_BACKGROUND" to "\u2605FE",
    "ADVENTURE_EFFECT" to "\u2605AV",
    "XXL" to "\u2605XXL",
    "XXS" to "\u2605XXS",
    "GREAT_LEAGUE" to "GL",
    "ULTRA_LEAGUE" to "UL",
    "LITTLE_LEAGUE" to "CP",
    "LEGACY" to "\u24C1",
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
