package com.mewname.app.model

import java.text.Normalizer
import java.util.Locale

enum class VivillonPattern(
    val label: String,
    val symbolKey: String
) {
    ARCHIPELAGO("Arquipélago", "VIVILLON_ARCHIPELAGO"),
    CONTINENTAL("Continental", "VIVILLON_CONTINENTAL"),
    ELEGANT("Elegante", "VIVILLON_ELEGANT"),
    FANCY("Fancy", "VIVILLON_FANCY"),
    GARDEN("Jardim", "VIVILLON_GARDEN"),
    HIGH_PLAINS("Planalto", "VIVILLON_HIGH_PLAINS"),
    ICY_SNOW("Neve congelada", "VIVILLON_ICY_SNOW"),
    JUNGLE("Selva", "VIVILLON_JUNGLE"),
    MARINE("Marinho", "VIVILLON_MARINE"),
    MEADOW("Prado", "VIVILLON_MEADOW"),
    MODERN("Moderno", "VIVILLON_MODERN"),
    MONSOON("Monção", "VIVILLON_MONSOON"),
    OCEAN("Oceano", "VIVILLON_OCEAN"),
    POKE_BALL("Poke Ball", "VIVILLON_POKE_BALL"),
    POLAR("Polar", "VIVILLON_POLAR"),
    RIVER("Rio", "VIVILLON_RIVER"),
    SANDSTORM("Deserto", "VIVILLON_SANDSTORM"),
    SAVANNA("Savana", "VIVILLON_SAVANNA"),
    SUN("Solar", "VIVILLON_SUN"),
    TUNDRA("Tundra", "VIVILLON_TUNDRA");

    companion object {
        fun fromAssetName(name: String): VivillonPattern? {
            val normalized = Normalizer.normalize(name.substringBeforeLast('.'), Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                .substringBeforeLast('.')
                .trim()
                .uppercase(Locale.US)
                .replace(Regex("[^A-Z0-9]+"), "_")
                .trim('_')
                .replace(Regex("^\\d+_?"), "")
                .removePrefix("VIVILLON_")
            val aliases = mapOf(
                "ARQUIPELAGO" to "ARCHIPELAGO",
                "DESERTO" to "SANDSTORM",
                "ELEGANTE" to "ELEGANT",
                "JARDIM" to "GARDEN",
                "MARINHO" to "MARINE",
                "MONCAO" to "MONSOON",
                "MODERNO" to "MODERN",
                "NEVE_CONGELADA" to "ICY_SNOW",
                "OCEANO" to "OCEAN",
                "PLANALTO" to "HIGH_PLAINS",
                "PRADO" to "MEADOW",
                "RIO" to "RIVER",
                "SAVANA" to "SAVANNA",
                "SELVA" to "JUNGLE",
                "SOLAR" to "SUN",
                "POKEBALL" to "POKE_BALL",
                "POKE_BALL" to "POKE_BALL"
            )
            val resolved = aliases[normalized] ?: normalized
            return entries.firstOrNull { it.name == resolved }
        }
    }
}
