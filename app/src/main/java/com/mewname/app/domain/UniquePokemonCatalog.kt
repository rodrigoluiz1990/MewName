package com.mewname.app.domain

import java.util.Locale

data class UniquePokemonFormOption(
    val label: String,
    val code: String
)

data class UniquePokemonSpec(
    val assetFolder: String,
    val pokemonNames: Set<String>,
    val options: List<UniquePokemonFormOption>
)

object UniquePokemonCatalog {
    private val specs = listOf(
        UniquePokemonSpec(
            assetFolder = "furfrou",
            pokemonNames = setOf("FURFROU"),
            options = listOf(
                UniquePokemonFormOption("Cavalheiro", "DAN"),
                UniquePokemonFormOption("Debutante", "DEB"),
                UniquePokemonFormOption("Diamante", "DIA"),
                UniquePokemonFormOption("Coração", "HRT"),
                UniquePokemonFormOption("Kabuki", "KAB"),
                UniquePokemonFormOption("Aristocrático", "LR"),
                UniquePokemonFormOption("Madame", "MAT"),
                UniquePokemonFormOption("Faraó", "PHA"),
                UniquePokemonFormOption("Selvagem", "PB"),
                UniquePokemonFormOption("Estrela", "STR")
            )
        ),
        UniquePokemonSpec(
            assetFolder = "genesect",
            pokemonNames = setOf("GENESECT"),
            options = listOf(
                UniquePokemonFormOption("Normal", "NRM"),
                UniquePokemonFormOption("Incendiante", "BRN"),
                UniquePokemonFormOption("Congelante", "CHL"),
                UniquePokemonFormOption("Hídrico", "DSE"),
                UniquePokemonFormOption("Elétrico", "SHK")
            )
        ),
        UniquePokemonSpec(
            assetFolder = "rotom",
            pokemonNames = setOf("ROTOM"),
            options = listOf(
                UniquePokemonFormOption("Normal", "NRM"),
                UniquePokemonFormOption("Ventilador", "FAN"),
                UniquePokemonFormOption("Congelante", "FRS"),
                UniquePokemonFormOption("Calor", "HEA"),
                UniquePokemonFormOption("Corte", "MOW"),
                UniquePokemonFormOption("Lavagem", "WSH")
            )
        ),
        UniquePokemonSpec(
            assetFolder = "spinda",
            pokemonNames = setOf("SPINDA"),
            options = listOf(
                UniquePokemonFormOption("Spinda #1", "01"),
                UniquePokemonFormOption("Spinda #2", "02"),
                UniquePokemonFormOption("Spinda #3", "03"),
                UniquePokemonFormOption("Spinda #4", "04"),
                UniquePokemonFormOption("Spinda #5", "05"),
                UniquePokemonFormOption("Spinda #6", "06"),
                UniquePokemonFormOption("Spinda #7", "07"),
                UniquePokemonFormOption("Spinda #8", "08"),
                UniquePokemonFormOption("Spinda #9", "09")
            )
        ),
        UniquePokemonSpec(
            assetFolder = "unown",
            pokemonNames = setOf("UNOWN"),
            options = buildList {
                ('A'..'Z').forEach { letter ->
                    add(UniquePokemonFormOption(letter.toString(), letter.toString()))
                }
                add(UniquePokemonFormOption("!", "!"))
                add(UniquePokemonFormOption("?", "?"))
            }
        )
    )

    fun specFor(pokemonName: String?): UniquePokemonSpec? {
        val normalized = pokemonName?.trim()?.uppercase(Locale.US) ?: return null
        return specs.firstOrNull { normalized in it.pokemonNames }
    }

    fun optionsFor(pokemonName: String?): List<UniquePokemonFormOption> {
        return specFor(pokemonName)?.options.orEmpty()
    }

    fun compactCodeFor(pokemonName: String?, label: String?): String? {
        val safeLabel = label?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return optionsFor(pokemonName).firstOrNull { it.label.equals(safeLabel, ignoreCase = true) }?.code
            ?: safeLabel.uppercase(Locale.US).replace(" ", "").take(3)
    }

    fun allSpecs(): List<UniquePokemonSpec> = specs

    fun symbolKeyForAsset(assetFolder: String, code: String): String {
        return "UNIQUE_FORM_${assetFolder.uppercase(Locale.US)}_${code.uppercase(Locale.US)}"
    }

    fun symbolKeyFor(pokemonName: String?, label: String?): String? {
        val spec = specFor(pokemonName) ?: return null
        val option = spec.options.firstOrNull { it.label.equals(label?.trim(), ignoreCase = true) } ?: return null
        return symbolKeyForAsset(spec.assetFolder, option.code)
    }
}
