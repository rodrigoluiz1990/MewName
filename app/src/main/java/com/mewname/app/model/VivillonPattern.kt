package com.mewname.app.model

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
            val normalized = name
                .substringBeforeLast('.')
                .trim()
                .uppercase()
                .replace('-', '_')
                .replace(' ', '_')
            val aliases = mapOf(
                "POKEBALL" to "POKE_BALL"
            )
            val resolved = aliases[normalized] ?: normalized
            return entries.firstOrNull { it.name == resolved }
        }
    }
}
