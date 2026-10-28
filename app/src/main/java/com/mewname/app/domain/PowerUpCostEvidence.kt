package com.mewname.app.domain

import com.mewname.app.domain.OcrTextGeometry.normalizeText
import com.mewname.app.ocr.OcrTextLine
import com.mewname.app.powerUpCostBetweenLevels
import kotlin.math.abs

/** Pokémon origin implied by the next power-up cost; each one scales Stardust and Candy differently. */
internal enum class PowerUpOrigin { NORMAL, LUCKY, PURIFIED, LUCKY_PURIFIED, SHADOW }

/**
 * Reads the Stardust and Candy beside the power-up button of a detail screen. Purification has no static
 * visual marker (its white glints are animated and also appear on white Pokémon and light backgrounds),
 * but it always charges 10% less, while shadows charge 20% more and lucky Pokémon half the Stardust.
 */
internal object PowerUpCostEvidence {
    data class Reading(val stardust: Int, val candy: Int?)

    private val buttonLabels = listOf("FORTALECER", "POWER UP", "MAS PODER")
    private val numberToken = Regex("^\\d{1,3}(?:[.,]\\d{3})*$")

    fun read(lines: List<OcrTextLine>): Reading? {
        val button = lines.firstOrNull { line ->
            line.boundingBox != null && normalizeText(line.text).trim().let { text -> buttonLabels.any { it == text } }
        } ?: return null
        val buttonBox = button.boundingBox!!
        val values = lines.asSequence()
            .filter { it !== button }
            .mapNotNull { line -> line.boundingBox?.let { box -> line to box } }
            .filter { (_, box) -> box.left >= buttonBox.right - buttonBox.height() &&
                abs(box.centerY() - buttonBox.centerY()) <= buttonBox.height() / 2 }
            .sortedBy { (_, box) -> box.left }
            .flatMap { (line, _) -> line.text.trim().split(Regex("\\s+")).asSequence() }
            .filter { numberToken.matches(it) }
            .map { it.replace(".", "").replace(",", "").toInt() }
            .toList()
        val stardustIndex = values.indexOfFirst { it >= 100 }
        if (stardustIndex < 0) return null
        val candy = values.drop(stardustIndex + 1).firstOrNull { it in 1..30 }
        return Reading(values[stardustIndex], candy)
    }

    /** Origins whose single power-up cost matches [reading] at [exactLevel], or at any level when it is unknown. */
    fun origins(reading: Reading, exactLevel: Double? = null): Set<PowerUpOrigin> {
        val levels = exactLevel?.let { listOf(it) } ?: generateSequence(1.0) { it + 0.5 }.takeWhile { it < 50.0 }.toList()
        return buildSet {
            for (level in levels) for (origin in PowerUpOrigin.values()) {
                val cost = powerUpCostBetweenLevels(
                    level, level + 0.5,
                    isLucky = origin == PowerUpOrigin.LUCKY || origin == PowerUpOrigin.LUCKY_PURIFIED,
                    isShadow = origin == PowerUpOrigin.SHADOW,
                    isPurified = origin == PowerUpOrigin.PURIFIED || origin == PowerUpOrigin.LUCKY_PURIFIED
                )
                val candy = if (cost.candyXl > 0) cost.candyXl else cost.candy
                if (cost.stardust == reading.stardust && (reading.candy == null || reading.candy == candy)) add(origin)
            }
        }
    }

    fun isPurified(origins: Set<PowerUpOrigin>): Boolean =
        origins.isNotEmpty() && origins.all { it == PowerUpOrigin.PURIFIED || it == PowerUpOrigin.LUCKY_PURIFIED }

    fun isShadow(origins: Set<PowerUpOrigin>): Boolean = origins == setOf(PowerUpOrigin.SHADOW)
}
