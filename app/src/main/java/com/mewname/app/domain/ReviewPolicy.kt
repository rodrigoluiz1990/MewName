package com.mewname.app.domain

import com.mewname.app.model.Gender
import com.mewname.app.model.NamingBlockType
import com.mewname.app.model.NamingConfig
import com.mewname.app.model.NamingField
import com.mewname.app.model.PokemonScreenData
import com.mewname.app.model.effectiveBlocks

/** Shared rules that keep the activity and overlay review flows consistent. */
object ReviewPolicy {
    fun reviewableFields(configs: List<NamingConfig>): List<NamingField> {
        return configs.flatMap { config ->
            config.effectiveBlocks()
                .filter { block -> block.type == NamingBlockType.VARIABLE }
                .mapNotNull { block -> block.field }
        }.distinct()
    }

    fun shouldOpenReview(data: PokemonScreenData, fields: List<NamingField>): Boolean {
        return fields.any { field ->
            when (field) {
                NamingField.POKEMON_NAME -> data.pokemonName.isNullOrBlank()
                NamingField.UNOWN_LETTER -> false
                NamingField.UNIQUE_FORM -> UniquePokemonCatalog.optionsFor(data.pokemonName).isNotEmpty() && data.uniqueForm.isNullOrBlank()
                NamingField.VIVILLON_PATTERN -> isVivillonFamily(data.pokemonName) && data.vivillonPattern == null
                NamingField.CP -> data.cp == null
                NamingField.IV_PERCENT -> data.ivPercent == null
                NamingField.IV_COMBINATION -> data.attIv == null || data.defIv == null || data.staIv == null
                NamingField.LEVEL -> data.level == null
                NamingField.GENDER -> data.gender == Gender.UNKNOWN
                NamingField.SIZE,
                NamingField.MASTER_IV_BADGE,
                NamingField.EVOLVE_MARKER,
                NamingField.PURIFY_MARKER,
                NamingField.EVOLUTION_TYPE -> false
                NamingField.PVP_LEAGUE -> data.pvpLeague == null
                NamingField.PVP_RANK -> data.pvpRank == null
                else -> false
            }
        }
    }

    private fun isVivillonFamily(name: String?): Boolean {
        return name?.trim()?.uppercase() in setOf("SCATTERBUG", "SPEWPA", "VIVILLON")
    }
}