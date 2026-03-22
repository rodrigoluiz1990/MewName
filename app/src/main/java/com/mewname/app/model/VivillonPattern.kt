package com.mewname.app.model

enum class VivillonPattern(
    val label: String,
    val symbolKey: String
) {
    ARCHIPELAGO("Archipelago", "VIVILLON_ARCHIPELAGO"),
    CONTINENTAL("Continental", "VIVILLON_CONTINENTAL"),
    ELEGANT("Elegant", "VIVILLON_ELEGANT"),
    FANCY("Fancy", "VIVILLON_FANCY"),
    GARDEN("Garden", "VIVILLON_GARDEN"),
    HIGH_PLAINS("High Plains", "VIVILLON_HIGH_PLAINS"),
    ICY_SNOW("Icy Snow", "VIVILLON_ICY_SNOW"),
    JUNGLE("Jungle", "VIVILLON_JUNGLE"),
    MARINE("Marine", "VIVILLON_MARINE"),
    MEADOW("Meadow", "VIVILLON_MEADOW"),
    MODERN("Modern", "VIVILLON_MODERN"),
    MONSOON("Monsoon", "VIVILLON_MONSOON"),
    OCEAN("Ocean", "VIVILLON_OCEAN"),
    POKE_BALL("Poke Ball", "VIVILLON_POKE_BALL"),
    POLAR("Polar", "VIVILLON_POLAR"),
    RIVER("River", "VIVILLON_RIVER"),
    SANDSTORM("Sandstorm", "VIVILLON_SANDSTORM"),
    SAVANNA("Savanna", "VIVILLON_SAVANNA"),
    SUN("Sun", "VIVILLON_SUN"),
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
