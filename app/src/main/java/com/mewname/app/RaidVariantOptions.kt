package com.mewname.app

import com.mewname.app.domain.RaidChoice

internal data class RaidVariantOption(val choice: RaidChoice, val label: String)

/** Keep distinct battle forms selectable, but never repeat an identical boss/tier pair. */
internal fun raidVariantOptions(
    choices: List<RaidChoice>,
    category: (RaidChoice) -> String,
    pokemonName: (String) -> String
): List<RaidVariantOption> {
    val unique = choices.distinct()
    return unique.map { choice ->
        val title = category(choice)
        val label = if (unique.size > 1) "${pokemonName(choice.id)} \u00b7 $title" else title
        RaidVariantOption(choice, label)
    }
}
