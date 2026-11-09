package com.mewname.app.domain

import java.text.Normalizer
import java.util.Locale

/** A name or an isolated CP is not enough to classify a screen as a Pokemon detail. */
internal object PokemonDetailScreenDetector {
    private val marks = Regex("\\p{M}+")
    private val cp = Regex("\\b(?:CP|PC)\\s*\\d+")
    private val hp = Regex("(?:\\d+\\s*/\\s*\\d+\\s*(?:HP|PS)\\b|\\b(?:HP|PS)\\s*\\d+\\s*/\\s*\\d+)")
    private val action = Regex("\\b(?:FORTALECER|POWER UP|DAR MAS|EVOLUIR|EVOLVE|EVOLUCIONAR|PURIFICAR|PURIFY)\\b")
    private val candy = Regex("\\b(?:DOCE|DOCES|CANDY|CANDIES|CARAMELO|CARAMELOS)\\b")
    private val dust = Regex("\\b(?:POEIRA ESTELAR|STARDUST|POLVO ESTELAR)\\b")
    private val height = Regex("\\b(?:ALTURA|HEIGHT)\\b")
    private val weight = Regex("\\b(?:PESO|WEIGHT)\\b")

    fun matches(text: String): Boolean {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(marks, "").uppercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        val hasHp = hp.containsMatchIn(normalized)
        val hasCp = cp.containsMatchIn(normalized)
        val hasAction = action.containsMatchIn(normalized)
        val hasCandy = candy.containsMatchIn(normalized)
        val hasDust = dust.containsMatchIn(normalized)
        val hasMeasurements = height.containsMatchIn(normalized) && weight.containsMatchIn(normalized)
        // Also accept partially obscured CP when HP and the detail controls remain visible.
        return (hasHp && (hasAction || hasMeasurements || (hasCandy && hasDust))) ||
            (hasCp && hasAction && (hasCandy || hasDust || hasMeasurements)) ||
            (hasMeasurements && hasCandy && hasDust)
    }
}
