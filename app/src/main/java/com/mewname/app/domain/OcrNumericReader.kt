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

    fun hasIvContext(normalizedRaw: String, lines: List<OcrTextLine>, referenceBounds: Rect?): Boolean {
        if (ivContextKeywords.any { normalizedRaw.contains(it) }) return true

        val lowerLines = normalizedLinesInRegion(lines, 0.0f, 1.0f, 0.55f, 0.95f, referenceBounds)
        val keywordHits = lowerLines.count { line ->
            ivContextKeywords.any { keyword -> line.contains(keyword) }
        }

        return keywordHits >= 2
    }

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
