package com.mewname.app.domain

import java.text.Normalizer
import java.util.Locale

internal enum class FilterScreen { FRIENDS, POKEMON }

internal object FilterScreenDetector {
    fun detect(text: String): FilterScreen? {
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
        return null
    }
}