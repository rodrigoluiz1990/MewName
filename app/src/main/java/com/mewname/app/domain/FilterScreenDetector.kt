package com.mewname.app.domain

import java.text.Normalizer
import java.util.Locale

internal enum class FilterScreen { FRIENDS, POKEMON }

internal object FilterScreenDetector {
    fun detect(text: String, lines: List<BattleOcrLine> = emptyList()): FilterScreen? {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").uppercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
        fun has(pattern: String) = Regex("\\b(?:$pattern)\\b").containsMatchIn(normalized)
        val search = has("BUSCAR|SEARCH|RECHERCHER")
        val friends = has("AMIGOS|FRIENDS") && has("SOCIAL") &&
            has("ADICIONAR AMIGOS|ADD FRIENDS?|ANADIR AMIGOS|AGREGAR AMIGOS") &&
            (search || has("CONVIDAR|INVITE|INVITAR"))
        if (friends) return FilterScreen.FRIENDS
        val storage = has("POKEMON") && has("TAGS|ETIQUETAS") && has("OVOS|EGGS|HUEVOS")
        val cpCount = Regex("\\b(?:PC|CP)\\s*\\d+").findAll(normalized).count()
        val capacity = Regex("\\b\\d+\\s*/\\s*\\d+\\b").containsMatchIn(normalized)
        if (storage && capacity && (cpCount >= 1 || (search && has("NENHUM POKEMON|NO POKEMON|NINGUN POKEMON")))) {
            return FilterScreen.POKEMON
        }
        // Open tag pages omit the storage tabs/capacity. Require a searchable CP grid,
        // excluding detail/appraisal/battle anchors; never route by the tag's custom name.
        val detail = has("DOCES|CANDY|CARAMELOS|FORTALECER|POWER UP|PURIFICAR|APPRAISAL|DEFESA|DEFENSE") ||
            Regex("\\d+\\s*/\\s*\\d+\\s*(?:PS|HP)").containsMatchIn(normalized)
        val cpBoxes = lines.filter { Regex("\\b(?:PC|CP)\\s*\\d+", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }
        val grid = cpBoxes.size >= 3 && cpBoxes.maxOf { it.left } - cpBoxes.minOf { it.left } > .2f &&
            cpBoxes.maxOf { it.top } - cpBoxes.minOf { it.top } > .08f
        if (((search && cpCount >= 2) || grid) && !detail) return FilterScreen.POKEMON
        return null
    }
}