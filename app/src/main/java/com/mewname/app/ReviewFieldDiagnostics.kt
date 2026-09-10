package com.mewname.app

import com.mewname.app.model.*

/** Values are kept separately from evidence: false or null alone never proves detection. */
internal data class ReviewFieldDiagnostic(
    val field: NamingField,
    val captured: String,
    val merged: String,
    val displayed: String,
    val evidence: String,
    val mergedEvidence: String = evidence,
    val displayedEvidence: String = evidence
)

internal object ReviewFieldDiagnostics {
    fun fields(selected: Set<NamingField>?): List<NamingField> =
        if (selected.isNullOrEmpty()) NamingField.entries else NamingField.entries.filter { it in selected }

    fun collect(raw: PokemonScreenData, merged: PokemonScreenData, current: PokemonScreenData,
                selected: Set<NamingField>?): List<ReviewFieldDiagnostic> = fields(selected).map { field ->
        ReviewFieldDiagnostic(field, value(raw, field), value(merged, field), value(current, field), evidence(raw, field), evidence(merged, field), evidence(current, field))
    }

    fun render(records: List<ReviewFieldDiagnostic>): String = buildString {
        records.forEach { item ->
            appendLine("[${item.field.name}]")
            appendLine("Valor: ${item.displayed}")
            if (item.captured != item.merged || item.merged != item.displayed) {
                appendLine("Historico: captura=${item.captured} -> combinado=${item.merged} -> revisao=${item.displayed}")
            }
            appendLine(item.evidence)
            if (item.mergedEvidence != item.evidence) appendLine("Evidencia apos combinacao: ${item.mergedEvidence}")
            if (item.displayedEvidence != item.mergedEvidence) appendLine("Evidencia da revisao: ${item.displayedEvidence}")
            appendLine()
        }
    }

    private fun value(d: PokemonScreenData, f: NamingField): String = (when (f) {
        NamingField.POKEMON_NAME -> "nome=${d.pokemonName}; familia=${d.candyFamilyName}"
        NamingField.UNOWN_LETTER -> d.unownLetter
        NamingField.UNIQUE_FORM -> d.uniqueForm
        NamingField.VIVILLON_PATTERN -> d.vivillonPattern
        NamingField.POKEDEX_NUMBER -> d.pokedexNumber
        NamingField.CP -> d.cp
        NamingField.IV_PERCENT -> d.ivPercent
        NamingField.IV_COMBINATION -> "${d.attIv}/${d.defIv}/${d.staIv}"
        NamingField.LEVEL -> d.level
        NamingField.GENDER -> d.gender
        NamingField.TYPE -> "${d.type1}/${d.type2}"
        NamingField.FAVORITE -> d.isFavorite
        NamingField.LUCKY -> d.isLucky
        NamingField.SHADOW -> d.isShadow
        NamingField.PURIFIED -> d.isPurified
        NamingField.SPECIAL_BACKGROUND -> "${d.hasSpecialBackground}; ${d.specialBackgroundType}"
        NamingField.ADVENTURE_EFFECT -> d.hasAdventureEffect
        NamingField.EVOLVE_MARKER -> d.shouldEvolve
        NamingField.PURIFY_MARKER -> d.shouldPurify
        NamingField.SIZE -> d.size
        NamingField.MASTER_IV_BADGE -> d.masterIvBadgeMatch
        NamingField.PVP_LEAGUE -> d.pvpLeague
        NamingField.PVP_RANK -> "${d.pvpPokemonName}; ${d.pvpRank}"
        NamingField.LEGACY_MOVE -> d.hasLegacyMove
        NamingField.LEGACY_MOVE_NAME -> "rapido=${d.selectedFastMove}; carregado=${d.selectedChargedMove}"
        NamingField.EVOLUTION_TYPE -> d.evolutionFlags
    })?.toString() ?: "nao informado"

    private fun evidence(d: PokemonScreenData, f: NamingField): String = (when (f) {
        NamingField.POKEMON_NAME -> d.candyDebugInfo
        NamingField.GENDER -> d.genderDebugInfo?.let { info ->
            buildString {
                appendLine("Origem: ${info.source}; especie=${info.pokemonName}; detectado=${info.detectedGender}")
                appendLine("OCR regional: ${info.candidateLines.joinToString(" | ").ifEmpty { "nenhum" }}")
                appendLine("Simbolos no OCR completo: masculino=${info.rawMaleSymbol}; feminino=${info.rawFemaleSymbol}")
                appendLine("Imagem disponivel=${info.bitmapAvailable}; comparacao visual do icone=nao implementada")
                appendLine("Regioes normalizadas (esquerda,topo,direita,baixo): " +
                    info.examinedRegions.joinToString("; ") { "(${it.left},${it.top},${it.right},${it.bottom})" })
                info.iconRect?.let { appendLine("Icone: $it") }
                append("Decisao: ${info.notes.substringBefore(" Comparacao visual do icone")}")
            }
        }
        NamingField.LEVEL, NamingField.CP -> d.levelDebugInfo
        NamingField.IV_PERCENT, NamingField.IV_COMBINATION -> d.ivDebugInfo
        NamingField.TYPE, NamingField.FAVORITE -> d.attributeDebugInfo
        NamingField.SHADOW, NamingField.PURIFIED, NamingField.LUCKY, NamingField.SPECIAL_BACKGROUND -> d.backgroundDebugInfo
        NamingField.LEGACY_MOVE, NamingField.LEGACY_MOVE_NAME -> d.legacyDebugInfo
        NamingField.SIZE -> d.sizeDebugInfo
        NamingField.MASTER_IV_BADGE -> d.masterIvBadgeDebugInfo
        NamingField.UNIQUE_FORM -> d.uniqueFormDebugInfo
        NamingField.VIVILLON_PATTERN -> d.vivillonDebugInfo
        NamingField.ADVENTURE_EFFECT -> d.adventureEffectDebugInfo
        NamingField.EVOLUTION_TYPE -> d.evolutionIconDebugInfo
        else -> null
    })?.toString() ?: "Sem evidencia especifica registrada; o valor isolado nao confirma reconhecimento."
}