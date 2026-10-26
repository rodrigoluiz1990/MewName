package com.mewname.app.domain

import com.mewname.app.domain.OcrTextGeometry.normalizeText
import com.mewname.app.domain.OcrTextGeometry.normalizedLinesInRegion
import com.mewname.app.domain.OcrTextGeometry.rawLinesInRegion

import android.graphics.Rect
import com.mewname.app.ocr.OcrTextLine

/** Reads numeric fields only from their existing screen regions. */
internal object OcrNumericReader {
    val cpRegex = Regex("""\b(?:CP|PC|GP|OP|DP)\s*[:.-]?\s*(\d{1,5})\b""", RegexOption.IGNORE_CASE)
    private val looseCpRegex = Regex("""(?:^|[^A-Z])P\s*[:.-]?\s*(\d{2,5})\b""", RegexOption.IGNORE_CASE)
    private val hpRegex = Regex("""\b(\d{2,3})\s*/\s*(\d{2,3})\s*(?:HP|PS)\b""", RegexOption.IGNORE_CASE)
    private val ivPercentRegex = Regex("""(\d{1,3})\s*[%©]""")
    private val levelRegex = Regex("""^(?:L(?:V(?:L)?)?|NIVEL|LEVEL|NIV|LEV)\s*[:.]?\s*(\d{1,2}(?:[.,]5)?)$""", RegexOption.IGNORE_CASE)
    private val ivContextKeywords = setOf(
        "APPRAISAL", "VALORACAO", "VALORAR", "ATTACK", "DEFENSE", "DEFESA", "HP", "ATAQUE"
    )

    /** Only numeric fragments in the CP header, never dates, HP or candy amounts. */
    fun partialCpDigits(text: String): String? =
        Regex("""^(?:(?:PC|CP|PR|GP|OP|DP|P)\s*[:.-]?\s*)?(\d{2,5})$""", RegexOption.IGNORE_CASE)
            .matchEntire(text.trim())?.groupValues?.get(1)

    fun extractPartialCpDigits(lines: List<OcrTextLine>, bounds: Rect?): List<String> {
        if (bounds == null) return emptyList()
        return lines.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            val x = (box.centerX() - bounds.left).toFloat() / bounds.width()
            val y = (box.centerY() - bounds.top).toFloat() / bounds.height()
            if (x !in .25f.. .72f || y !in .035f.. .13f) return@mapNotNull null
            partialCpDigits(line.text)
        }.distinct()
    }

    fun hasIvContext(normalizedRaw: String, lines: List<OcrTextLine>, referenceBounds: Rect?): Boolean {
        if (ivContextKeywords.any { normalizedRaw.contains(it) }) return true

        val lowerLines = normalizedLinesInRegion(lines, 0.0f, 1.0f, 0.55f, 0.95f, referenceBounds)
        val keywordHits = lowerLines.count { line ->
            ivContextKeywords.any { keyword -> line.contains(keyword) }
        }

        return keywordHits >= 2
    }

    /** Positive detail-screen anchors; never bypass a visible lower appraisal panel. */
    fun isDetailWithoutAppraisal(raw: String, lines: List<OcrTextLine>, bounds: Rect?): Boolean {
        if (bounds == null || extractDisplayedMaxHp(lines, bounds) == null) return false
        val lower = normalizedLinesInRegion(lines, 0f, 1f, .55f, .98f, bounds)
        val labels = lower.count { appraisalLabel.containsMatchIn(it.trim()) }
        val appraisal = lower.any { text ->
            listOf("APPRAISAL", "AVALIACAO", "VALORACION", "FOI PEGO", "FOI CAPTURADO", "WAS CAUGHT", "FUE CAPTURADO").any { text.contains(it) }
        }
        if (labels >= 2 || appraisal) return false
        if (extractCp(raw, lines, bounds) != null) return true
        // Large artwork can hide the CP header. The appraisal panel covers the power-up and move buttons,
        // so either of them still confirms a detail screen; otherwise "NEW ATTACK" alone was read as appraisal.
        return lower.any { text -> detailOnlyButtons.any { button -> text.contains(button) } }
    }

    private val appraisalLabel = Regex("^(ATAQUE|ATTACK|DEFESA|DEFENSE|DEFENSA|PS|HP|SALUD)(?:$|[ :])")
    private val detailOnlyButtons = listOf("FORTALECER", "POWER UP", "MAS PODER", "NOVO ATAQUE", "NEW ATTACK", "NUEVO ATAQUE")

    fun extractCp(normalizedRaw: String, lines: List<OcrTextLine>, referenceBounds: Rect?): Int? {
        val hasBoundedLines = lines.any { it.boundingBox != null }
        val topLines = rawLinesInRegion(lines, 0.0f, 1.0f, 0.0f, 0.24f, referenceBounds)
            .map { it.text }
        val fromTop = topLines
            .asSequence()
            .mapNotNull { text -> extractCpFromText(text, allowLoosePrefix = true) }
            .firstOrNull()
        if (fromTop != null) return fromTop

        val upperLines = rawLinesInRegion(lines, 0.0f, 1.0f, 0.0f, 0.36f, referenceBounds)
            .map { it.text }
        val fromUpper = upperLines
            .asSequence()
            .mapNotNull { text -> extractCpFromText(text, allowLoosePrefix = false) }
            .firstOrNull()
        if (fromUpper != null) return fromUpper

        return if (hasBoundedLines) {
            null
        } else {
            extractCpFromText(normalizedRaw, allowLoosePrefix = true)
        }
    }

    private fun extractCpFromText(text: String, allowLoosePrefix: Boolean): Int? {
        val strict = cpRegex.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.takeIf { it in 10..10000 }
        if (strict != null) return strict

        if (!allowLoosePrefix) return null
        // On bright lucky backgrounds ML Kit can read the PC prefix as "sC".
        // Accept this only as a complete top-region line, never inside prose.
        val noisyPrefix = Regex("""^[S5]C\s*[:.-]?\s*(\d{2,5})$""", RegexOption.IGNORE_CASE)
            .matchEntire(text.trim())?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 10..10000 }
        if (noisyPrefix != null) return noisyPrefix
        return looseCpRegex.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.takeIf { it in 10..10000 }
    }

    fun extractIvPercent(lines: List<OcrTextLine>, referenceBounds: Rect?): Int? {
        val appraisalLines = normalizedLinesInRegion(lines, 0.0f, 0.55f, 0.58f, 0.95f, referenceBounds)
        val fromAppraisal = appraisalLines
            .asSequence()
            .mapNotNull { text ->
                ivPercentRegex.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
            .firstOrNull { it in 0..100 }

        return fromAppraisal
    }

    fun extractOcrLevel(lines: List<OcrTextLine>, referenceBounds: Rect?): Double? {
        val candidateLines = rawLinesInRegion(lines, 0.0f, 1.0f, 0.0f, 0.82f, referenceBounds)
            .map { normalizeText(it.text).trim() }

        return candidateLines.firstNotNullOfOrNull { line ->
            levelRegex.matchEntire(line)?.groupValues?.getOrNull(1)?.replace(",", ".")?.toDoubleOrNull()
        }
    }

    fun extractDisplayedMaxHp(lines: List<OcrTextLine>, referenceBounds: Rect?): Int? {
        val candidateLines = rawLinesInRegion(lines, 0.15f, 0.85f, 0.18f, 0.58f, referenceBounds)
            .map { normalizeText(it.text).trim() }

        return candidateLines.firstNotNullOfOrNull { line ->
            hpRegex.find(line)?.groupValues?.getOrNull(2)?.toIntOrNull()
        }
    }

}
