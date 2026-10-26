package com.mewname.app.domain

internal data class EncounterReading(val name: String, val cp: Int)

internal object EncounterScreenReader {
    fun detect(lines: List<BattleOcrLine>, text: String): EncounterReading? {
        // Capture nameplates are central; raid CP and inventory CP are near the top.
        val normalized = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
        if (listOf("batalha", "battle", "fortalecer", "power up", "evoluir", "evolve", "poeira", "stardust", "doces", "candy").any { it in normalized }) return null
        val band = lines.filter { (it.top + it.bottom) / 2f in .24f.. .48f }
            .sortedWith(compareBy<BattleOcrLine> { it.top }.thenBy { it.left })
        val cpPattern = Regex("(?i)\\b(?:PC|CP)\\s*([0-9]{1,5})\\b")
        val cpLine = band.firstOrNull { cpPattern.containsMatchIn(it.text) } ?: return null
        val cp = cpPattern.find(cpLine.text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it >= 10 } ?: return null
        val near = band.filter { kotlin.math.abs((it.top + it.bottom - cpLine.top - cpLine.bottom) / 2) < .035f }
            .sortedBy { it.left }.joinToString(" ") { it.text }
        val name = cpPattern.replace(near, "").replace(Regex("[^\\p{L}\\p{M} .:'()\\-]"), " ").trim()
        if (name.length < 3) return null
        return EncounterReading(name, cp)
    }
}
