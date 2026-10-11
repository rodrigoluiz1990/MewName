package com.mewname.app.domain

import com.mewname.app.domain.OcrTextGeometry.normalizeText
import com.mewname.app.domain.OcrTextGeometry.normalizedBitmapRect

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.mewname.app.ocr.OcrTextLine
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Reads appraisal bars and owns the geometry cache for one parser instance. */
internal class OcrIvBarReader {
    data class IvBarDetection(
        val attackRatio: Float?,
        val defenseRatio: Float?,
        val staminaRatio: Float?,
        val attack: Int?,
        val defense: Int?,
        val stamina: Int?,
        val attackDebug: String,
        val defenseDebug: String,
        val staminaDebug: String,
        val detectedBars: Int,
        val isReliable: Boolean,
        val appraisalPanelRect: Rect,
        val attackBarRect: Rect?,
        val defenseBarRect: Rect?,
        val staminaBarRect: Rect?
    )

    private data class DetectedBarBand(
        val top: Int,
        val bottom: Int,
        val ratio: Float?,
        val value: Int?,
        val likelyFullDark: Boolean = false,
        val rect: Rect,
        val debugSummary: String = ""
    )

    private data class BarMeasurement(
        val ratio: Float?,
        val value: Int? = null,
        val trackRect: Rect?,
        val debugSummary: String = ""
    )

    private data class LayoutBandCandidate(
        val measurement: BarMeasurement,
        val sourceRect: Rect,
        val score: Int
    )

    private data class BarColumnMetrics(
        val ratio: Float,
        val trackStart: Int,
        val trackEnd: Int
    )

    /** One bulk pixel read for the appraisal area, shared by all IV detectors. */
    private class IvPixels(val source: Bitmap) {
        val width = source.width
        val height = source.height
        private val top = (height * 0.60f).toInt()
        private val pixels = IntArray(width * (height - top)).also {
            source.getPixels(it, 0, width, 0, top, width, height - top)
        }
        fun getPixel(x: Int, y: Int): Int = if (y >= top && y < height && x in 0 until width)
            pixels[(y - top) * width + x] else source.getPixel(x, y)

        fun copyRegion(rect: Rect): IntArray = IntArray(rect.width() * rect.height()).also { result ->
            for (y in rect.top until rect.bottom) {
                val offset = (y - rect.top) * rect.width()
                if (y >= top && y < height && rect.left >= 0 && rect.right <= width) {
                    pixels.copyInto(result, offset, (y - top) * width + rect.left, (y - top) * width + rect.right)
                } else {
                    source.getPixels(result, offset, rect.width(), rect.left, y, rect.width(), 1)
                }
            }
        }
    }

    private data class IvGeometryKey(val width: Int, val height: Int, val region: Rect)
    private data class IvRowGeometry(val pixels: IntArray, val occupied: BooleanArray)
    private val ivRowGeometry = LinkedHashMap<IvGeometryKey, IvRowGeometry>(16, 0.75f, true)

    fun detectIvBars(source: Bitmap, lines: List<OcrTextLine>): IvBarDetection {
        val bitmap = IvPixels(source)
        val width = bitmap.width
        val height = bitmap.height

        val appraisalPanel = detectIvAppraisalPanel(bitmap) ?: Rect(
            (width * 0.05f).roundToInt(),
            (height * 0.70f).roundToInt(),
            (width * 0.48f).roundToInt(),
            (height * 0.91f).roundToInt()
        )

        val panelWidth = appraisalPanel.width().coerceAtLeast(1)
        val panelHeight = appraisalPanel.height().coerceAtLeast(1)
        val regionLeft = (appraisalPanel.left + panelWidth * 0.06f).roundToInt()
        val labelRects = detectIvLabelRects(lines, appraisalPanel)

        val expectedBarRects = buildExpectedIvBarRects(appraisalPanel, labelRects)
        val labelAnchoredBands = detectIvBarsFromLabels(
            bitmap = bitmap,
            appraisalPanel = appraisalPanel,
            labelRects = labelRects,
            expectedBarRects = expectedBarRects
        )
        val fallbackScanRect = Rect(
            regionLeft,
            (appraisalPanel.top + panelHeight * 0.48f).roundToInt(),
            expectedBarRects.maxOfOrNull { it.right } ?: appraisalPanel.right,
            (appraisalPanel.top + panelHeight * 0.96f).roundToInt().coerceAtMost(height)
        )

        // Preserve merge precedence: labels, layout, then broad scan. A later
        // source cannot replace an already compatible measurement in every slot.
        fun hasAllCompatibleBands(bands: List<DetectedBarBand>): Boolean =
            bands.size == 3 && bands.indices.all { index ->
                bands[index].ratio != null && isBandGeometryCompatible(bands[index], expectedBarRects[index])
            }
        val layoutBands = if (hasAllCompatibleBands(labelAnchoredBands)) emptyList() else
            detectIvBarsFromExpectedLayout(bitmap, appraisalPanel, expectedBarRects)
        val preferredBands = mergeDetectedBands(expectedBarRects, labelAnchoredBands, layoutBands)
        val scanBands = if (hasAllCompatibleBands(preferredBands)) emptyList() else
            detectIvBarBands(bitmap, fallbackScanRect)
        val bands = mergeDetectedBands(expectedBarRects, labelAnchoredBands, layoutBands, scanBands)
        val attackBand = bands.getOrNull(0)
        val defenseBand = bands.getOrNull(1)
        val staminaBand = bands.getOrNull(2)

        val attackRatio = attackBand?.ratio
        val defenseRatio = defenseBand?.ratio
        val staminaRatio = staminaBand?.ratio
        var attack = resolveDetectedBandValue(attackBand, IvBarType.ATTACK)
        var defense = resolveDetectedBandValue(defenseBand, IvBarType.DEFENSE)
        var stamina = resolveDetectedBandValue(staminaBand, IvBarType.STAMINA)
        val ratios = listOf(attackRatio, defenseRatio, staminaRatio)
        val detectedBars = ratios.count { it != null }
        val validSpread = ratios.filterNotNull().let { nonNull ->
            nonNull.size >= 2 && (nonNull.maxOrNull()!! - nonNull.minOrNull()!!) >= 0.025f
        }
        val hasEnoughFill = ratios.filterNotNull().average().toFloat() >= 0.18f
        val allZeroBars = attack == 0 && defense == 0 && stamina == 0
        val reliable = detectedBars == 3 && (validSpread || hasEnoughFill || allZeroBars)

        return IvBarDetection(
            attackRatio = attackRatio,
            defenseRatio = defenseRatio,
            staminaRatio = staminaRatio,
            attack = attack,
            defense = defense,
            stamina = stamina,
            attackDebug = attackBand?.debugSummary.orEmpty(),
            defenseDebug = defenseBand?.debugSummary.orEmpty(),
            staminaDebug = staminaBand?.debugSummary.orEmpty(),
            detectedBars = detectedBars,
            isReliable = reliable,
            appraisalPanelRect = appraisalPanel,
            attackBarRect = attackBand?.rect,
            defenseBarRect = defenseBand?.rect,
            staminaBarRect = staminaBand?.rect
        )
    }

    private fun detectIvBarsFromLabels(
        bitmap: IvPixels,
        appraisalPanel: Rect,
        labelRects: Map<String, Rect>,
        expectedBarRects: List<Rect>
    ): List<DetectedBarBand> {
        return listOf("ATTACK", "DEFENSE", "HP").mapIndexedNotNull { index, key ->
            val labelRect = labelRects[key] ?: return@mapIndexedNotNull null
            val expectedRect = expectedBarRects.getOrNull(index) ?: return@mapIndexedNotNull null
            val nextLabelRect = listOf("ATTACK", "DEFENSE", "HP")
                .getOrNull(index + 1)
                ?.let(labelRects::get)
            val searchTop = labelRect.bottom.coerceIn(appraisalPanel.top, appraisalPanel.bottom - 2)
            val fallbackBottom = (searchTop + expectedRect.height() + appraisalPanel.height() * 0.08f)
                .roundToInt()
                .coerceAtMost(appraisalPanel.bottom)
            val searchBottom = nextLabelRect
                ?.top
                ?.coerceAtMost(appraisalPanel.bottom)
                ?.takeIf { it > searchTop + 6 }
                ?: fallbackBottom
            val searchRect = Rect(
                expectedRect.left,
                searchTop,
                expectedRect.right,
                searchBottom
            )
            val localScan = detectIvBarBands(bitmap, searchRect)
                .filter { it.ratio != null }
                .filter { isBandGeometryCompatible(it, expectedRect) }
                .minByOrNull { abs(it.rect.centerY() - expectedRect.centerY()) }
            if (localScan != null) {
                return@mapIndexedNotNull localScan.copy(
                    value = localScan.value ?: nearestValueForLabel(key, localScan.ratio),
                    debugSummary = "label-scan ${localScan.debugSummary}"
                )
            }

            val anchoredTop = searchTop
                .coerceIn(appraisalPanel.top, appraisalPanel.bottom - expectedRect.height())
            val anchoredRect = Rect(
                expectedRect.left,
                anchoredTop,
                expectedRect.right,
                (anchoredTop + expectedRect.height()).coerceAtMost(appraisalPanel.bottom)
            )
            val measurement = findBestMeasurementNearExpectedRect(bitmap, appraisalPanel, anchoredRect)
            if (measurement != null) {
                val trackRect = measurement.measurement.trackRect ?: measurement.sourceRect
                return@mapIndexedNotNull DetectedBarBand(
                    top = trackRect.top,
                    bottom = trackRect.bottom,
                    ratio = measurement.measurement.ratio,
                    value = measurement.measurement.value ?: nearestValueForLabel(key, measurement.measurement.ratio),
                    likelyFullDark = measurement.measurement.ratio != null &&
                        isDarkFullBar(bitmap, measurement.sourceRect, measurement.measurement.ratio),
                    rect = trackRect,
                    debugSummary = "label-direct ${measurement.measurement.debugSummary}"
                )
            }

            DetectedBarBand(
                top = anchoredRect.top,
                bottom = anchoredRect.bottom,
                ratio = null,
                value = null,
                likelyFullDark = false,
                rect = anchoredRect,
                debugSummary = "label: sem medicao"
            )
        }
    }

    private fun detectIvLabelRects(
        lines: List<OcrTextLine>,
        appraisalPanel: Rect
    ): Map<String, Rect> {
        val relevantLines = lines
            .mapNotNull { line -> line.boundingBox?.let { rect -> line to rect } }
            .filter { (_, rect) -> rect.centerY() in appraisalPanel.top..appraisalPanel.bottom }

        val labelMap = linkedMapOf(
            "ATTACK" to null as Rect?,
            "DEFENSE" to null as Rect?,
            "HP" to null as Rect?
        ).toMutableMap()

        relevantLines.forEach { (line, rect) ->
            val normalized = normalizeText(line.text)
            when {
                normalized.contains("ATTACK") || normalized.contains("ATAQUE") -> labelMap["ATTACK"] = rect
                normalized.contains("DEFENSE") || normalized.contains("DEFESA") -> labelMap["DEFENSE"] = rect
                Regex("""(^|\s)(HP|PS)(\s|$)""").containsMatchIn(normalized) -> labelMap["HP"] = rect
            }
        }

        return labelMap.mapNotNull { (key, rect) -> rect?.let { key to it } }.toMap()
    }

    private fun mergeDetectedBands(
        expectedBarRects: List<Rect>,
        vararg bandSources: List<DetectedBarBand>
    ): List<DetectedBarBand> {
        val merged = mutableListOf<DetectedBarBand>()
        for (index in 0 until 3) {
            val candidates = bandSources.mapNotNull { it.getOrNull(index) }
            val expectedRect = expectedBarRects.getOrNull(index)
            val geometryMatched = expectedRect?.let { rect ->
                candidates.filter { candidate ->
                    candidate.ratio != null && isBandGeometryCompatible(candidate, rect)
                }
            }.orEmpty()
            val chosen = geometryMatched.firstOrNull()
                ?: expectedRect?.let { rect ->
                    candidates.firstOrNull { isBandGeometryCompatible(it, rect) }
                }
                ?: candidates.firstOrNull()
                ?: continue
            merged += chosen
        }
        return merged
    }

    private fun detectIvBarsFromExpectedLayout(
        bitmap: IvPixels,
        appraisalPanel: Rect,
        expectedBarRects: List<Rect>
    ): List<DetectedBarBand> {
        return expectedBarRects.mapIndexed { index, rect ->
            val labelKey = when (index) {
                0 -> "ATTACK"
                1 -> "DEFENSE"
                else -> "HP"
            }
            detectExpectedLayoutBand(
                bitmap = bitmap,
                appraisalPanel = appraisalPanel,
                expectedRect = rect,
                labelKey = labelKey
            )
        }
    }

    private fun buildExpectedIvBarRects(
        appraisalPanel: Rect,
        labelRects: Map<String, Rect> = emptyMap()
    ): List<Rect> {
        val commonBarStartRatio = 128f / 654f
        val commonBarWidthRatio = 375f / 654f
        val barHeightRatio = 51f / 842f
        val topRatios = listOf(
            "ATTACK" to 369f / 842f,
            "DEFENSE" to 472f / 842f,
            "HP" to 577f / 842f
        )

        val barLeft = appraisalPanel.left + (appraisalPanel.width() * commonBarStartRatio).roundToInt()
        val barRight = (barLeft + appraisalPanel.width() * commonBarWidthRatio)
            .roundToInt()
            .coerceAtMost(appraisalPanel.right)
        val barHeight = (appraisalPanel.height() * barHeightRatio).roundToInt().coerceAtLeast(18)
        val labelDrivenOffset = (appraisalPanel.height() * 0.012f).roundToInt().coerceAtLeast(10)

        return topRatios.map { (key, topRatio) ->
            val layoutTop = (appraisalPanel.top + appraisalPanel.height() * topRatio)
                .roundToInt()
                .coerceIn(appraisalPanel.top, appraisalPanel.bottom - 2)
            val labelAnchoredTop = labelRects[key]
                ?.let { rect -> rect.bottom + labelDrivenOffset }
                ?.coerceIn(appraisalPanel.top, appraisalPanel.bottom - 2)
            val barTop = labelAnchoredTop?.let { min(layoutTop, it) } ?: layoutTop
            val barBottom = (barTop + barHeight).coerceAtMost(appraisalPanel.bottom)
            Rect(barLeft, barTop, barRight, barBottom)
        }
    }

    private fun detectExpectedLayoutBand(
        bitmap: IvPixels,
        appraisalPanel: Rect,
        expectedRect: Rect,
        labelKey: String
    ): DetectedBarBand {
        val bestDirect = findBestMeasurementNearExpectedRect(bitmap, appraisalPanel, expectedRect)
        if (bestDirect != null) {
            val measurement = bestDirect.measurement
            return DetectedBarBand(
                top = measurement.trackRect?.top ?: bestDirect.sourceRect.top,
                bottom = measurement.trackRect?.bottom ?: bestDirect.sourceRect.bottom,
                ratio = measurement.ratio,
                value = measurement.value ?: nearestValueForLabel(labelKey, measurement.ratio),
                likelyFullDark = measurement.ratio != null && isDarkFullBar(bitmap, bestDirect.sourceRect, measurement.ratio),
                rect = measurement.trackRect ?: bestDirect.sourceRect,
                debugSummary = "layout-direct ${measurement.debugSummary}"
            )
        }

        val searchPadding = (appraisalPanel.height() * 0.05f).roundToInt().coerceAtLeast(18)
        val searchRect = Rect(
            expectedRect.left,
            (expectedRect.top - searchPadding).coerceAtLeast(appraisalPanel.top),
            expectedRect.right,
            (expectedRect.bottom + searchPadding).coerceAtMost(appraisalPanel.bottom)
        )

        val localScan = detectIvBarBands(bitmap, searchRect)
            .filter { it.ratio != null }
            .filter { isBandGeometryCompatible(it, expectedRect) }
            .minByOrNull { abs(it.rect.centerY() - expectedRect.centerY()) }
        if (localScan != null) {
            return localScan.copy(
                value = localScan.value ?: nearestValueForLabel(labelKey, localScan.ratio),
                debugSummary = "layout-scan ${localScan.debugSummary}"
            )
        }

        return DetectedBarBand(
            top = expectedRect.top,
            bottom = expectedRect.bottom,
            ratio = null,
            value = null,
            likelyFullDark = false,
            rect = expectedRect,
            debugSummary = "layout: sem medicao"
        )
    }

    private fun findBestMeasurementNearExpectedRect(
        bitmap: IvPixels,
        appraisalPanel: Rect,
        expectedRect: Rect
    ): LayoutBandCandidate? {
        val offsetStep = max(2, expectedRect.height() / 8)
        val maxOffset = max(12, expectedRect.height() / 2)
        val candidates = mutableListOf<LayoutBandCandidate>()

        for (offset in -maxOffset..maxOffset step offsetStep) {
            val shiftedTop = (expectedRect.top + offset).coerceIn(appraisalPanel.top, appraisalPanel.bottom - 2)
            val shiftedBottom = (shiftedTop + expectedRect.height()).coerceAtMost(appraisalPanel.bottom)
            if (shiftedBottom - shiftedTop < 8) continue
            val shiftedRect = Rect(expectedRect.left, shiftedTop, expectedRect.right, shiftedBottom)
            val measurement = detectBarMeasurement(bitmap, shiftedRect) ?: continue
            val trackRect = measurement.trackRect ?: shiftedRect
            val widthDelta = abs(trackRect.width() - expectedRect.width())
            val centerDelta = abs(trackRect.centerY() - expectedRect.centerY())
            val heightPenalty = if (trackRect.height() < max(8, expectedRect.height() / 5)) 120 else 0
            val score = widthDelta + (centerDelta * 3) + heightPenalty
            candidates += LayoutBandCandidate(
                measurement = measurement,
                sourceRect = shiftedRect,
                score = score
            )
        }

        return candidates
            .filter { candidate ->
                val band = DetectedBarBand(
                    top = candidate.measurement.trackRect?.top ?: candidate.sourceRect.top,
                    bottom = candidate.measurement.trackRect?.bottom ?: candidate.sourceRect.bottom,
                    ratio = candidate.measurement.ratio,
                    value = candidate.measurement.value,
                    rect = candidate.measurement.trackRect ?: candidate.sourceRect
                )
                isBandGeometryCompatible(band, expectedRect)
            }
            .minByOrNull { it.score }
    }

    private fun isBandGeometryCompatible(
        band: DetectedBarBand,
        expectedRect: Rect
    ): Boolean {
        val candidateRect = band.rect
        val widthRatio = candidateRect.width().toFloat() / expectedRect.width().toFloat().coerceAtLeast(1f)
        val leftDelta = abs(candidateRect.left - expectedRect.left)
        val rightDelta = abs(candidateRect.right - expectedRect.right)
        val centerDelta = abs(candidateRect.centerY() - expectedRect.centerY())
        val minHeight = max(10, expectedRect.height() / 5)

        return widthRatio in 0.82f..1.18f &&
            leftDelta <= max(18, expectedRect.width() / 10) &&
            rightDelta <= max(18, expectedRect.width() / 10) &&
            centerDelta <= max(34, expectedRect.height()) &&
            candidateRect.height() >= minHeight
    }

    private fun detectIvBarBands(bitmap: IvPixels, scanRect: Rect): List<DetectedBarBand> {
        if (scanRect.width() <= 0 || scanRect.height() <= 0) return emptyList()
        // Cache only row geometry, never fill ratios/IVs. An unchanged row is
        // verified pixel-for-pixel; changed rows use the original detector.
        val key = IvGeometryKey(bitmap.width, bitmap.height, Rect(scanRect))
        val previous = synchronized(ivRowGeometry) { ivRowGeometry[key] }
        val pixels = bitmap.copyRegion(scanRect)
        val occupied = BooleanArray(scanRect.height())
        var reusedRows = 0
        for (row in occupied.indices) {
            val start = row * scanRect.width()
            val unchanged = previous != null && (start until start + scanRect.width()).all {
                pixels[it] == previous.pixels[it]
            }
            occupied[row] = if (unchanged) {
                reusedRows++
                previous!!.occupied[row]
            } else {
                detectBarFillRatioForRow(bitmap, scanRect, scanRect.top + row) != null
            }
        }
        synchronized(ivRowGeometry) {
            ivRowGeometry[key] = IvRowGeometry(pixels, occupied)
            // Bounded per parser/device session, including when resolution changes.
            while (ivRowGeometry.size > 12 || ivRowGeometry.values.sumOf { it.pixels.size } > 1_000_000) {
                ivRowGeometry.remove(ivRowGeometry.keys.first())
            }
        }
        android.util.Log.d("MewNameReadTiming", "IV geometry rows=$reusedRows/${occupied.size}")
        val candidateRows = occupied.indices.filter { occupied[it] }.map { scanRect.top + it }
        if (candidateRows.isEmpty()) return emptyList()

        val groups = mutableListOf<MutableList<Int>>()
        candidateRows.forEach { row ->
            val current = groups.lastOrNull()
            if (current == null || row - current.last() > 6) {
                groups += mutableListOf(row)
            } else {
                current += row
            }
        }

        return groups
            .filter { it.size >= 2 }
            .map { rows ->
                val top = rows.first().coerceAtLeast(scanRect.top)
                val bottom = (rows.last() + 1).coerceAtMost(scanRect.bottom)
                val rect = Rect(scanRect.left, top, scanRect.right, bottom)
                val measurement = detectBarMeasurement(bitmap, rect)
                val ratio = measurement?.ratio
                DetectedBarBand(
                    top = measurement?.trackRect?.top ?: top,
                    bottom = measurement?.trackRect?.bottom ?: bottom,
                    ratio = ratio,
                    value = measurement?.value,
                    likelyFullDark = ratio != null && isDarkFullBar(bitmap, rect, ratio),
                    rect = measurement?.trackRect ?: rect,
                    debugSummary = measurement?.debugSummary ?: "scan: sem medicao"
                )
            }
            .sortedBy { it.top }
            .take(3)
    }

    private fun detectIvAppraisalPanel(bitmap: IvPixels): Rect? {
        val searchRect = normalizedBitmapRect(
            bitmap.source,
            minX = 0.03f,
            maxX = 0.58f,
            minY = 0.62f,
            maxY = 0.95f
        )

        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var hitCount = 0

        for (y in searchRect.top until searchRect.bottom) {
            for (x in searchRect.left until searchRect.right) {
                if (isAppraisalPanelPixel(bitmap.getPixel(x, y))) {
                    hitCount++
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }

        if (hitCount < 350 || minX == Int.MAX_VALUE) return null

        val rect = Rect(
            (minX - bitmap.width * 0.01f).roundToInt().coerceAtLeast(0),
            (minY - bitmap.height * 0.01f).roundToInt().coerceAtLeast(0),
            (maxX + bitmap.width * 0.01f).roundToInt().coerceAtMost(bitmap.width),
            (maxY + bitmap.height * 0.01f).roundToInt().coerceAtMost(bitmap.height)
        )

        return if (rect.width() >= bitmap.width * 0.18f && rect.height() >= bitmap.height * 0.10f) rect else null
    }

    private fun detectBarFillRatio(bitmap: IvPixels, rect: Rect): Float? {
        return detectBarMeasurement(bitmap, rect)?.ratio
    }

    private fun detectBarMeasurement(bitmap: IvPixels, rect: Rect): BarMeasurement? {
        if (rect.width() <= 1 || rect.height() <= 1) return null
        val safeTop = rect.top.coerceIn(0, bitmap.height - 1)
        val safeBottom = rect.bottom.coerceIn(safeTop + 1, bitmap.height)
        val safeLeft = rect.left.coerceIn(0, bitmap.width - 1)
        val safeRight = rect.right.coerceIn(safeLeft + 1, bitmap.width)
        if (safeBottom <= safeTop || safeRight <= safeLeft) return null

        val safeRect = Rect(safeLeft, safeTop, safeRight, safeBottom)
        detectDiscreteIvMeasurement(bitmap, safeRect)?.let { discrete ->
            return discrete
        }

        val sampleRows = listOf(
            safeRect.top + safeRect.height() / 4,
            safeRect.top + safeRect.height() / 2 - safeRect.height() / 8,
            safeRect.top + safeRect.height() / 2,
            safeRect.top + safeRect.height() / 3,
            safeRect.top + (safeRect.height() * 2 / 3),
            safeRect.top + safeRect.height() * 3 / 4
        ).map { it.coerceIn(safeRect.top, safeRect.bottom - 1) }

        val aggregatedMetrics = detectBarColumnMetricsByColumns(bitmap, safeRect)
        val aggregatedRatio = aggregatedMetrics?.ratio
        val rowRatios = sampleRows.mapNotNull { y ->
            detectBarFillRatioForRow(bitmap, safeRect, y)
        }

        if (aggregatedRatio == null && rowRatios.isEmpty()) return null
        if (rowRatios.isEmpty()) {
            val tightenedTrackRect = aggregatedMetrics?.toTrackRect(safeRect)?.let { trackRect ->
                tightenBarTrackRect(bitmap, trackRect)
            } ?: safeRect
            return BarMeasurement(
                ratio = aggregatedRatio?.let(::calibrateObservedIvRatio),
                value = null,
                trackRect = tightenedTrackRect,
                debugSummary = "fallback aggRatio=${aggregatedRatio?.formatDebug() ?: "-"} trackW=${tightenedTrackRect.width()} trackH=${tightenedTrackRect.height()}"
            )
        }
        val sorted = rowRatios.sorted()
        val median = sorted[sorted.size / 2]
        val upperQuartile = sorted[(sorted.size * 3) / 4]
        val maxRatio = sorted.last()

        val observedRatio = when {
            aggregatedRatio != null -> {
                val clampedAggregate = min(aggregatedRatio, maxRatio * 1.02f)
                maxOf(
                    clampedAggregate,
                    median * 0.98f,
                    upperQuartile * 0.96f
                )
            }
            else -> maxOf(median, upperQuartile * 0.97f)
        }.coerceIn(0f, 1f)
        val tightenedTrackRect = aggregatedMetrics?.toTrackRect(safeRect)?.let { trackRect ->
            tightenBarTrackRect(bitmap, trackRect)
        } ?: safeRect
        return BarMeasurement(
            ratio = calibrateObservedIvRatio(observedRatio),
            value = null,
            trackRect = tightenedTrackRect,
            debugSummary = "fallback rows med=${median.formatDebug()} uq=${upperQuartile.formatDebug()} max=${maxRatio.formatDebug()} agg=${aggregatedRatio?.formatDebug() ?: "-"} obs=${observedRatio.formatDebug()} trackW=${tightenedTrackRect.width()} trackH=${tightenedTrackRect.height()}"
        )
    }

    private fun detectDiscreteIvMeasurement(bitmap: IvPixels, rect: Rect): BarMeasurement? {
        if (rect.width() < 30 || rect.height() < 6) return null

        val bandTop = (rect.top + rect.height() * 0.24f).roundToInt().coerceIn(rect.top, rect.bottom - 2)
        val bandBottom = (rect.bottom - rect.height() * 0.24f).roundToInt().coerceIn(bandTop + 2, rect.bottom)
        val measurementRect = Rect(rect.left, bandTop, rect.right, bandBottom)
        val minDarkHits = max(1, measurementRect.height() / 7)
        val darkColumns = BooleanArray(measurementRect.width())
        val softFillColumns = BooleanArray(measurementRect.width())
        val fillColumns = BooleanArray(measurementRect.width())
        val separatorColumns = BooleanArray(measurementRect.width())
        val trackColumns = BooleanArray(measurementRect.width())

        for (column in 0 until measurementRect.width()) {
            val x = measurementRect.left + column
            var darkHits = 0
            var warmHits = 0
            var separatorHits = 0
            var trackHits = 0
            for (y in measurementRect.top until measurementRect.bottom) {
                val color = bitmap.getPixel(x, y)
                if (isIvBarMeasuredFillPixel(color)) {
                    darkHits++
                    trackHits++
                } else if (isIvBarWarmTailPixel(color)) {
                    warmHits++
                    trackHits++
                } else if (isIvBarSeparatorPixel(color)) {
                    separatorHits++
                    trackHits++
                } else if (isIvBarTrackPixel(color)) {
                    trackHits++
                }
            }
            if (darkHits >= minDarkHits) {
                darkColumns[column] = true
            }
            if (warmHits >= max(1, measurementRect.height() / 5)) {
                softFillColumns[column] = true
            }
            if (darkColumns[column] || softFillColumns[column]) {
                fillColumns[column] = true
            }
            if (separatorHits >= 1) {
                separatorColumns[column] = true
            }
            if (trackHits >= max(1, measurementRect.height() / 3)) {
                trackColumns[column] = true
            }
        }

        if (darkColumns.count { it } < max(2, measurementRect.width() / 80)) {
            val sampleRows = listOf(
                measurementRect.top + measurementRect.height() / 4,
                measurementRect.top + measurementRect.height() / 2,
                measurementRect.top + measurementRect.height() * 3 / 4
            ).map { it.coerceIn(measurementRect.top, measurementRect.bottom - 1) }
            for (column in 0 until measurementRect.width()) {
                val x = measurementRect.left + column
                if (sampleRows.any { y ->
                        val color = bitmap.getPixel(x, y)
                        isIvBarMeasuredFillPixel(color) || isIvBarWarmTailPixel(color)
                    }) {
                    darkColumns[column] = true
                    fillColumns[column] = true
                }
            }
        }

        val darkCount = darkColumns.count { it }
        val softCount = softFillColumns.count { it }
        val fillCount = fillColumns.count { it }
        val trackCount = trackColumns.count { it }
        if (darkCount < max(2, measurementRect.width() / 80)) {
            return if (trackCount >= max(12, measurementRect.width() / 3) && softCount <= max(4, measurementRect.width() / 20)) {
                BarMeasurement(
                    ratio = 0f,
                    value = 0,
                    trackRect = measurementRect,
                    debugSummary = "discrete-zero trackWidthPx=${measurementRect.width()} trackColumns=$trackCount darkColumns=$darkCount softColumns=$softCount bandH=${measurementRect.height()}"
                )
            } else {
                null
            }
        }

        var lastDarkColumn = -1
        var lastFillColumn = -1
        var started = false
        var separatorBudget = max(4, measurementRect.width() / 60)
        var emptyBudget = max(3, measurementRect.width() / 45)

        for (column in 0 until measurementRect.width()) {
            if (fillColumns[column]) {
                if (darkColumns[column]) {
                    lastDarkColumn = column
                }
                lastFillColumn = column
                separatorBudget = max(4, measurementRect.width() / 60)
                emptyBudget = max(3, measurementRect.width() / 45)
                started = true
            } else if (darkColumns[column]) {
                lastDarkColumn = column
                lastFillColumn = column
                separatorBudget = max(4, measurementRect.width() / 60)
                emptyBudget = max(3, measurementRect.width() / 45)
                started = true
            } else if (started && softFillColumns[column]) {
                lastFillColumn = column
                separatorBudget = max(4, measurementRect.width() / 60)
                emptyBudget = max(3, measurementRect.width() / 45)
            } else if (started && separatorColumns[column] && separatorBudget > 0) {
                separatorBudget--
            } else if (started && emptyBudget > 0) {
                emptyBudget--
            } else if (started) {
                break
            }
        }

        if (lastFillColumn < 0) return null

        val fillWidthPx = (lastFillColumn + 1).coerceIn(0, measurementRect.width())
        val ratio = (fillWidthPx.toFloat() / measurementRect.width().toFloat()).coerceIn(0f, 1f)
        val stepWidth = measurementRect.width().toFloat() / 15f
        val rawIv = if (stepWidth > 0f) fillWidthPx.toFloat() / stepWidth else 0f
        if (measurementRect.height() <= 4 && rawIv <= 5.6f) {
            return BarMeasurement(
                ratio = 0f,
                value = 0,
                trackRect = measurementRect,
                debugSummary = "discrete-zero thinBand fillWidthPx=$fillWidthPx trackWidthPx=${measurementRect.width()} darkColumns=$darkCount softColumns=$softCount rawIv=${rawIv.formatDebug()} bandH=${measurementRect.height()}"
            )
        }
        val value = when {
            isDarkFullBar(bitmap, measurementRect, ratio) -> 15
            stepWidth <= 0f -> null
            // The bias compensates for separators, but must not turn a visibly
            // incomplete bar into 15 after a one-pixel JPEG edge shift.
            else -> (rawIv + 0.25f).roundToInt().coerceIn(0, if (rawIv < 14.5f) 14 else 15)
        }

        return BarMeasurement(
            ratio = ratio,
            value = value,
            trackRect = measurementRect,
            debugSummary = "discrete fillWidthPx=$fillWidthPx trackWidthPx=${measurementRect.width()} darkColumns=$darkCount softColumns=$softCount fillColumns=$fillCount lastDarkColumn=$lastDarkColumn lastFillColumn=$lastFillColumn rawIv=${rawIv.formatDebug()} iv=${value ?: "-"} bandH=${measurementRect.height()}"
        )
    }

    private fun detectBarColumnMetricsByColumns(bitmap: IvPixels, rect: Rect): BarColumnMetrics? {
        if (rect.width() <= 1 || rect.height() <= 1) return null

        val minFilledPixels = max(1, rect.height() / 6)
        val minTrackPixels = max(2, rect.height() / 4)
        val isFilledColumn = BooleanArray(rect.width())
        val isSeparatorColumn = BooleanArray(rect.width())
        val isTrackColumn = BooleanArray(rect.width())

        for (column in 0 until rect.width()) {
            val x = rect.left + column
            var filledHits = 0
            var separatorHits = 0
            var trackHits = 0

            for (scanY in rect.top until rect.bottom) {
                val color = bitmap.getPixel(x, scanY)
                when {
                    isIvBarCoreFillPixel(color) -> {
                        filledHits++
                        trackHits++
                    }
                    isIvBarSeparatorPixel(color) -> {
                        separatorHits++
                        trackHits++
                    }
                    isIvBarTrackPixel(color) || isIvBarWarmTailPixel(color) -> {
                        trackHits++
                    }
                }
            }

            if (filledHits >= minFilledPixels) {
                isFilledColumn[column] = true
            }
            if (separatorHits >= 1) {
                isSeparatorColumn[column] = true
            }
            if (trackHits >= minTrackPixels) {
                isTrackColumn[column] = true
            }
        }

        return analyzeBarColumns(rect.width(), isFilledColumn, isSeparatorColumn, isTrackColumn)
    }

    private fun nearestValueForLabel(labelKey: String, ratio: Float?): Int? {
        val barType = when (labelKey) {
            "ATTACK" -> IvBarType.ATTACK
            "DEFENSE" -> IvBarType.DEFENSE
            else -> IvBarType.STAMINA
        }
        return IvCombinationTable.nearestValueFromRatio(ratio, barType)
    }

    private fun resolveDetectedBandValue(band: DetectedBarBand?, barType: IvBarType): Int? {
        if (band == null) return null
        if (band.likelyFullDark && (band.ratio ?: 0f) >= 0.84f) {
            return 15
        }
        return band.value ?: IvCombinationTable.nearestValueFromRatio(band.ratio, barType)
    }

    private fun calibrateObservedIvRatio(observedRatio: Float): Float {
        val clamped = observedRatio.coerceIn(0f, 1f)
        return when {
            clamped >= 0.93f -> min(1f, clamped + 0.04f)
            clamped >= 0.60f -> (clamped * 0.97f).coerceIn(0f, 1f)
            clamped >= 0.18f -> (clamped * 0.94f - 0.005f).coerceIn(0f, 1f)
            else -> clamped
        }
    }

    private fun detectBarFillRatioForRow(bitmap: IvPixels, rect: Rect, y: Int): Float? {
        val isFilledColumn = BooleanArray(rect.width())
        val isSeparatorColumn = BooleanArray(rect.width())
        val isTrackColumn = BooleanArray(rect.width())

        for (column in 0 until rect.width()) {
            val x = rect.left + column
            val color = bitmap.getPixel(x, y)
            if (isIvBarCoreFillPixel(color)) {
                isFilledColumn[column] = true
            } else if (isIvBarSeparatorPixel(color)) {
                isSeparatorColumn[column] = true
                isTrackColumn[column] = true
            } else if (isIvBarTrackPixel(color) || isIvBarWarmTailPixel(color)) {
                isTrackColumn[column] = true
            }
        }

        return analyzeBarColumns(rect.width(), isFilledColumn, isSeparatorColumn, isTrackColumn)?.ratio
    }

    private fun analyzeBarColumns(
        rectWidth: Int,
        isFilledColumn: BooleanArray,
        isSeparatorColumn: BooleanArray,
        isTrackColumn: BooleanArray
    ): BarColumnMetrics? {
        val filledCount = isFilledColumn.count { it }
        if (filledCount < max(1, rectWidth / 24)) return null

        val trackGroups = mutableListOf<IntRange>()
        val maxGap = max(2, rectWidth / 42)
        var start = -1
        var lastSeen = -1
        var gap = 0

        for (column in 0 until isTrackColumn.size) {
            val isTrackLike = isTrackColumn[column] || isFilledColumn[column]
            when {
                isTrackLike && start == -1 -> {
                    start = column
                    lastSeen = column
                    gap = 0
                }
                isTrackLike -> {
                    lastSeen = column
                    gap = 0
                }
                start != -1 && gap < maxGap -> {
                    gap++
                }
                start != -1 -> {
                    trackGroups += (start..lastSeen)
                    start = -1
                    lastSeen = -1
                    gap = 0
                }
            }
        }
        if (start != -1 && lastSeen != -1) {
            trackGroups += (start..lastSeen)
        }
        if (trackGroups.isEmpty()) return null

        val selectedGroup = trackGroups.maxByOrNull { group ->
            val width = group.last - group.first + 1
            val filledInside = (group.first..group.last).count { isFilledColumn[it] }
            (filledInside * 3) + width
        } ?: return null

        val firstFilled = (selectedGroup.first..selectedGroup.last).firstOrNull { isFilledColumn[it] } ?: return null
        var lastContinuousFilled = firstFilled
        var separatorBudget = max(2, (selectedGroup.last - selectedGroup.first + 1) / 24)
        var emptyBudget = max(1, (selectedGroup.last - selectedGroup.first + 1) / 40)
        var started = false

        for (column in firstFilled..selectedGroup.last) {
            if (isFilledColumn[column]) {
                lastContinuousFilled = column
                separatorBudget = max(2, (selectedGroup.last - selectedGroup.first + 1) / 24)
                emptyBudget = max(1, (selectedGroup.last - selectedGroup.first + 1) / 40)
                started = true
            } else if (started && isSeparatorColumn[column] && separatorBudget > 0) {
                separatorBudget--
            } else if (started && emptyBudget > 0) {
                emptyBudget--
            } else if (started) {
                break
            }
        }

        if (lastContinuousFilled <= firstFilled) return null

        val detectedTrackWidth = (selectedGroup.last - selectedGroup.first + 1).toFloat().coerceAtLeast(1f)
        val effectiveWidth = (lastContinuousFilled - firstFilled + 1).toFloat()
        val ratio = (effectiveWidth / detectedTrackWidth).coerceIn(0f, 1f)
        if (ratio < 0.03f) return null

        return BarColumnMetrics(
            ratio = ratio,
            trackStart = selectedGroup.first,
            trackEnd = selectedGroup.last
        )
    }

    private fun BarColumnMetrics.toTrackRect(baseRect: Rect): Rect {
        val left = (baseRect.left + trackStart).coerceIn(baseRect.left, baseRect.right - 1)
        val right = (baseRect.left + trackEnd + 1).coerceIn(left + 1, baseRect.right)
        return Rect(left, baseRect.top, right, baseRect.bottom)
    }

    private fun tightenBarTrackRect(bitmap: IvPixels, rect: Rect): Rect {
        if (rect.width() <= 1 || rect.height() <= 1) return rect

        val minTrackPixels = max(4, rect.width() / 7)
        val candidateRows = mutableListOf<Int>()

        for (y in rect.top until rect.bottom) {
            var trackHits = 0
            for (x in rect.left until rect.right) {
                val color = bitmap.getPixel(x, y)
                if (
                    isIvBarCoreFillPixel(color) ||
                    isIvBarSeparatorPixel(color) ||
                    isIvBarTrackPixel(color) ||
                    isIvBarWarmTailPixel(color)
                ) {
                    trackHits++
                }
            }
            if (trackHits >= minTrackPixels) {
                candidateRows += y
            }
        }

        if (candidateRows.isEmpty()) return rect

        val groups = mutableListOf<MutableList<Int>>()
        candidateRows.forEach { row ->
            val current = groups.lastOrNull()
            if (current == null || row - current.last() > 1) {
                groups += mutableListOf(row)
            } else {
                current += row
            }
        }

        val selectedGroup = groups.maxByOrNull { group -> group.size } ?: return rect
        val top = selectedGroup.first().coerceIn(rect.top, rect.bottom - 1)
        val bottom = (selectedGroup.last() + 1).coerceIn(top + 1, rect.bottom)
        return Rect(rect.left, top, rect.right, bottom)
    }

    private fun isIvBarCoreFillPixel(colorInt: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(colorInt, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]

        val isOrangeRed = (hue in 0f..45f) || (hue in 340f..360f)
        return isOrangeRed && saturation >= 0.22f && value in 0.24f..0.88f
    }

    private fun isIvBarMeasuredFillPixel(colorInt: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(colorInt, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]

        val isOrangeRed = (hue in 0f..45f) || (hue in 340f..360f)
        return isOrangeRed && saturation >= 0.18f && value in 0.22f..0.88f
    }

    private fun isIvBarWarmTailPixel(colorInt: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(colorInt, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]

        val isOrangeRed = (hue in 0f..45f) || (hue in 340f..360f)
        return isOrangeRed && saturation >= 0.18f && value > 0.86f
    }

    private fun isIvBarSeparatorPixel(colorInt: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(colorInt, hsv)
        return hsv[1] <= 0.18f && hsv[2] >= 0.70f
    }

    private fun isIvBarTrackPixel(colorInt: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(colorInt, hsv)
        return hsv[1] <= 0.18f && hsv[2] in 0.45f..0.88f
    }

    private fun isDarkFullBar(bitmap: IvPixels, rect: Rect, ratio: Float): Boolean {
        if (ratio < 0.82f || rect.width() < 8 || rect.height() < 2) return false
        val startX = (rect.left + rect.width() * 0.72f).roundToInt().coerceIn(rect.left, rect.right - 1)
        val endX = (rect.left + rect.width() * 0.96f).roundToInt().coerceIn(startX + 1, rect.right)
        val sampleY = ((rect.top + rect.bottom) / 2).coerceIn(0, bitmap.height - 1)
        var darkHits = 0
        var totalHits = 0
        for (x in startX until endX) {
            val hsv = FloatArray(3)
            Color.colorToHSV(bitmap.getPixel(x, sampleY), hsv)
            val isBarRed = (hsv[0] in 340f..360f || hsv[0] in 0f..25f) && hsv[1] >= 0.22f
            if (!isBarRed) continue
            totalHits++
            if (hsv[2] <= 0.88f) {
                darkHits++
            }
        }
        return totalHits >= 4 && darkHits >= max(3, (totalHits * 0.55f).roundToInt())
    }

    private fun Float.formatDebug(): String = String.format(Locale.US, "%.3f", this)

    private fun isAppraisalPanelPixel(colorInt: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(colorInt, hsv)
        return hsv[1] <= 0.10f && hsv[2] >= 0.93f
    }

}
