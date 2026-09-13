package com.mewname.app.domain

internal data class RocketCounter(val attackType: String, val examples: List<String>)

/** Uses weaknesses of the actual cached lineup, including dual types; never the grunt label alone. */
internal object RocketCounterAdvisor {
    private val examples = linkedMapOf(
        "Fighting" to listOf("Lucario", "Machamp", "Conkeldurr"),
        "Fire" to listOf("Reshiram", "Chandelure", "Blaziken"),
        "Water" to listOf("Kyogre", "Swampert", "Primarina"),
        "Grass" to listOf("Kartana", "Venusaur", "Sceptile"),
        "Electric" to listOf("Zekrom", "Xurkitree", "Electivire"),
        "Ice" to listOf("Mamoswine", "Glaceon", "Weavile"),
        "Ground" to listOf("Groudon", "Excadrill", "Garchomp"),
        "Rock" to listOf("Rhyperior", "Rampardos", "Tyranitar"),
        "Psychic" to listOf("Mewtwo", "Metagross", "Espeon"),
        "Dark" to listOf("Tyranitar", "Hydreigon", "Darkrai"),
        "Ghost" to listOf("Giratina", "Gengar", "Chandelure"),
        "Fairy" to listOf("Gardevoir", "Togekiss", "Primarina"),
        "Steel" to listOf("Metagross", "Excadrill", "Lucario"),
        "Flying" to listOf("Rayquaza", "Moltres", "Staraptor"),
        "Dragon" to listOf("Rayquaza", "Dragonite", "Garchomp"),
        "Poison" to listOf("Nihilego", "Roserade", "Gengar"),
        "Bug" to listOf("Volcarona", "Scizor", "Genesect")
    )
    fun suggest(entry: LeekEntry): List<RocketCounter> {
        val scores = examples.keys.associateWith { type ->
            entry.slots.sumOf { slot ->
                if (slot.pokemon.isEmpty()) 0.0 else slot.pokemon.sumOf { pokemon ->
                    val sections = pokemon.detail.split(';')
                    sections.maxOfOrNull { section ->
                        if (!Regex("\\b${Regex.escape(type)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(section)) 0.0
                        else if (section.contains("2×")) 2.56 else 1.6
                    } ?: 0.0
                } / slot.pokemon.size
            }
        }
        return scores.entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(3)
            .map { RocketCounter(it.key, examples.getValue(it.key)) }
    }
    fun filter(counters: List<RocketCounter>, typeLabel: (String) -> String): String =
        counters.map { typeLabel(it.attackType) }.distinct()
            .flatMap { type -> (1..3).map { "@$it$type" } }.joinToString(",")
}