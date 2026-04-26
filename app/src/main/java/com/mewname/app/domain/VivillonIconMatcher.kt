package com.mewname.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import com.mewname.app.model.NormalizedDebugRect
import com.mewname.app.model.VivillonPattern
import com.mewname.app.model.VivillonDebugInfo
import kotlin.math.abs
import kotlin.math.roundToInt

class VivillonIconMatcher {
    private data class ReferenceSignature(
        val pattern: VivillonPattern,
        val fileName: String,
        val signature: IntArray
    )

    private data class CandidateCrop(
        val bitmap: Bitmap,
        val rect: Rect
    )

    data class MatchResult(
        val pattern: VivillonPattern?,
        val debugInfo: VivillonDebugInfo
    )

    @Volatile
    private var cachedReferences: List<ReferenceSignature>? = null

    fun detectPattern(context: Context, bitmap: Bitmap): MatchResult {
        return detectPattern(context, bitmap, null)
    }

    fun detectPattern(
        context: Context,
        bitmap: Bitmap,
        pokemonName: String?
    ): MatchResult {
        val references = loadReferences(context)
        if (references.isEmpty()) {
            return MatchResult(
                pattern = null,
                debugInfo = VivillonDebugInfo(
                    notes = "nenhuma referência Vivillon carregada"
                )
            )
        }

        val candidateCrops = cropVivillonIconCandidates(bitmap, pokemonName)
        val candidateProfile = candidateProfileFor(pokemonName)
        val candidateSignatures = candidateCrops.map { crop ->
            createSignature(crop.bitmap) to crop.rect
        }
        val ranked = references.map { reference ->
            val bestCandidate = candidateSignatures.minByOrNull { candidate ->
                candidateMatchScore(bitmap, candidateProfile, candidate.first, reference.signature, candidate.second)
            }
            val distance = bestCandidate?.let { candidate ->
                candidateMatchScore(bitmap, candidateProfile, candidate.first, reference.signature, candidate.second)
            } ?: Double.MAX_VALUE
            Triple(reference, distance, bestCandidate?.second)
        }.sortedBy { it.second }

        val best = ranked.firstOrNull()
        if (best == null) {
            return MatchResult(
                pattern = null,
                debugInfo = VivillonDebugInfo(
                    candidateRects = candidateCrops.take(DEBUG_CANDIDATE_LIMIT).map { normalizeRect(bitmap, it.rect) },
                    notes = "nenhuma referência candidata calculada"
                )
            )
        }
        val second = ranked.getOrNull(1)
        val bestDistance = best.second
        val clearlyBetter = second == null || bestDistance <= second.second * DISTINCT_FACTOR
        val accepted = bestDistance <= MATCH_THRESHOLD || clearlyBetter
        val detectedPattern = if (accepted) best.first.pattern else null
        return MatchResult(
            pattern = detectedPattern,
            debugInfo = VivillonDebugInfo(
                detectedPattern = detectedPattern,
                bestReferenceName = best.first.fileName,
                bestDistance = bestDistance,
                secondReferenceName = second?.first?.fileName,
                secondDistance = second?.second,
                accepted = accepted,
                bestCandidateRect = best.third?.let { normalizeRect(bitmap, it) },
                candidateRects = candidateCrops.take(DEBUG_CANDIDATE_LIMIT).map { normalizeRect(bitmap, it.rect) },
                notes = buildString {
                    append("refs=")
                    append(references.size)
                    append("; threshold=")
                    append(MATCH_THRESHOLD)
                    append("; distinctlyBetter=")
                    append(clearlyBetter)
                    append("; profile=")
                    append(candidateProfile)
                    append("; candidates=")
                    append(candidateCrops.size)
                }
            )
        )
    }

    private fun loadReferences(context: Context): List<ReferenceSignature> {
        cachedReferences?.let { return it }
        synchronized(this) {
            cachedReferences?.let { return it }

            val loaded = runCatching {
                context.assets.list(REFS_PATH)
                    ?.filter { fileName ->
                        fileName.endsWith(".png", true) ||
                            fileName.endsWith(".jpg", true) ||
                            fileName.endsWith(".jpeg", true) ||
                            fileName.endsWith(".webp", true)
                    }
                    ?.mapNotNull { fileName ->
                        val pattern = VivillonPattern.fromAssetName(fileName) ?: return@mapNotNull null
                        context.assets.open("$REFS_PATH/$fileName").use { input ->
                            val bitmap = BitmapFactory.decodeStream(input) ?: return@mapNotNull null
                            ReferenceSignature(
                                pattern = pattern,
                                fileName = fileName,
                                signature = createSignature(bitmap)
                            )
                        }
                    }
                    ?: emptyList()
            }.getOrElse { emptyList() }

            cachedReferences = loaded
            return loaded
        }
    }

    private fun cropVivillonIconCandidates(bitmap: Bitmap, pokemonName: String?): List<CandidateCrop> {
        val rects = if (candidateProfileFor(pokemonName) == "pre_evo") {
            preEvolutionIconCandidateRects(bitmap)
        } else {
            candidateBoundsFor(pokemonName).map { bounds ->
                normalizedRect(bitmap, bounds[0], bounds[1], bounds[2], bounds[3])
            }
        }
        return rects.distinctBy { listOf(it.left, it.top, it.right, it.bottom) }
            .map { rect -> CandidateCrop(Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height()), rect) }
    }

    private fun preEvolutionIconCandidateRects(bitmap: Bitmap): List<Rect> {
        val fixedCandidates = candidateBoundsFor("Scatterbug").map { bounds ->
            normalizedRect(bitmap, bounds[0], bounds[1], bounds[2], bounds[3])
        }
        val minDimension = minOf(bitmap.width, bitmap.height)
        val anchorSizes = listOf(36, 42, 52, 64, 78, 96, 118)
            .map { (minDimension * (it / 1220f)).roundToInt().coerceAtLeast(36) }
            .distinct()

        val anchorRects = buildList {
            anchorSizes.forEach { size ->
                val step = (size * 0.65f).roundToInt().coerceAtLeast(18)
                val startX = (bitmap.width * 0.045f).roundToInt()
                val endX = (bitmap.width * 0.430f).roundToInt()
                val startY = (bitmap.height * 0.610f).roundToInt()
                val endY = (bitmap.height * 0.825f).roundToInt()

                var top = startY
                while (top + size <= endY) {
                    var left = startX
                    while (left + size <= endX) {
                        val rect = Rect(left, top, left + size, top + size)
                        if (
                            looksLikeIconCandidate(bitmap, rect) &&
                            looksLikeEvolutionButtonContext(bitmap, rect)
                        ) {
                            add(rect)
                        }
                        left += step
                    }
                    top += step
                }
            }
        }
        val evolveAnchors = preferPatternIconRows(bitmap, anchorRects)
        val expandedRects = evolveAnchors
            .take(MAX_SCAN_ANCHORS)
            .flatMap { anchor -> expandedPreEvolutionIconRects(bitmap, anchor) }

        return fixedCandidates + expandedRects.take(MAX_SCAN_CANDIDATES) + evolveAnchors.take(MAX_SCAN_CANDIDATES)
    }

    private fun preferPatternIconRows(bitmap: Bitmap, rects: List<Rect>): List<Rect> {
        if (rects.size < 8) return rects

        val tolerance = (rects.minOf { it.height() } * 0.95f).roundToInt().coerceAtLeast(28)
        val rows = rects
            .sortedBy { it.centerY() }
            .fold(mutableListOf<MutableList<Rect>>()) { groups, rect ->
                val group = groups.firstOrNull { existing ->
                    abs(existing.map { it.centerY() }.average() - rect.centerY()) <= tolerance
                }
                if (group == null) {
                    groups.add(mutableListOf(rect))
                } else {
                    group.add(rect)
                }
                groups
            }
            .filter { group -> group.size >= MIN_BUTTON_ROW_ANCHORS }
            .sortedBy { group -> group.map { it.centerY() }.average() }

        val targetY = bitmap.height * 0.705
        val selectedRows = rows
            .sortedBy { group -> abs(group.map { it.centerY() }.average() - targetY) }
            .take(3)
        val selected = selectedRows
            .flatten()
            .filter { rect ->
                val centerY = rect.centerY().toFloat() / bitmap.height.toFloat()
                centerY in 0.61f..0.79f
            }
        if (selected.isEmpty()) {
            return rects.sortedWith(
                compareBy<Rect> { abs(it.centerY() - targetY) }
                    .thenBy { it.left }
            )
        }
        return selected.sortedWith(
            compareBy<Rect> { abs(it.centerY() - targetY) }
                .thenBy { it.left }
        )
    }

    private fun expandedPreEvolutionIconRects(bitmap: Bitmap, anchor: Rect): List<Rect> {
        val minDimension = minOf(bitmap.width, bitmap.height)
        val squareSizes = listOf(52, 64, 78, 96, 118, 144, 170)
            .map { (minDimension * (it / 1220f)).roundToInt().coerceAtLeast(anchor.width()) }
            .distinct()
        val tallSizes = listOf(
            52 to 64,
            64 to 82,
            78 to 104,
            96 to 128,
            118 to 158,
            144 to 190
        ).map { (width, height) ->
            (minDimension * (width / 1220f)).roundToInt().coerceAtLeast(anchor.width()) to
                (minDimension * (height / 1220f)).roundToInt().coerceAtLeast(anchor.height())
        }.distinct()

        val centers = listOf(
            anchor.centerX() to anchor.centerY(),
            anchor.centerX() to (anchor.centerY() - anchor.height() * 0.20f).roundToInt(),
            (anchor.centerX() - anchor.width() * 0.18f).roundToInt() to anchor.centerY(),
            (anchor.centerX() + anchor.width() * 0.18f).roundToInt() to anchor.centerY()
        )
        return buildList {
            centers.forEach { (centerX, centerY) ->
                squareSizes.forEach { size ->
                    add(candidateRectAround(bitmap, centerX, centerY, size, size))
                }
                tallSizes.forEach { (width, height) ->
                    add(candidateRectAround(bitmap, centerX, centerY, width, height))
                }
            }
        }.filter { rect ->
            rect.width() > 1 && rect.height() > 1 && isPreEvolutionPatternArea(bitmap, rect)
        }
    }

    private fun isPreEvolutionPatternArea(bitmap: Bitmap, rect: Rect): Boolean {
        val centerX = rect.centerX().toFloat() / bitmap.width.toFloat()
        val centerY = rect.centerY().toFloat() / bitmap.height.toFloat()
        if (centerX !in 0.05f..0.40f || centerY !in 0.61f..0.82f) return false

        val hsv = FloatArray(3)
        var sampled = 0
        var greenPixels = 0
        var palePixels = 0
        var saturatedPixels = 0
        val step = maxOf(2, minOf(rect.width(), rect.height()) / 16)
        var y = rect.top
        while (y < rect.bottom) {
            var x = rect.left
            while (x < rect.right) {
                val color = bitmap.getPixel(x, y)
                Color.colorToHSV(color, hsv)
                sampled++
                val hue = hsv[0]
                val saturation = hsv[1]
                val value = hsv[2]
                if (hue in 80f..170f && saturation >= 0.28f && value >= 0.38f) greenPixels++
                if (value >= 0.76f && saturation <= 0.36f) palePixels++
                if (saturation >= 0.18f && value >= 0.28f) saturatedPixels++
                x += step
            }
            y += step
        }
        if (sampled == 0) return false
        val greenRatio = greenPixels.toDouble() / sampled.toDouble()
        val paleRatio = palePixels.toDouble() / sampled.toDouble()
        val saturatedRatio = saturatedPixels.toDouble() / sampled.toDouble()
        return greenRatio <= 0.42 && paleRatio >= 0.05 && saturatedRatio >= 0.04
    }

    private fun candidateRectAround(bitmap: Bitmap, centerX: Int, centerY: Int, width: Int, height: Int): Rect {
        val actualWidth = width.coerceIn(2, bitmap.width)
        val actualHeight = height.coerceIn(2, bitmap.height)
        val left = (centerX - actualWidth / 2).coerceIn(0, bitmap.width - actualWidth)
        val top = (centerY - actualHeight / 2).coerceIn(0, bitmap.height - actualHeight)
        return Rect(left, top, left + actualWidth, top + actualHeight)
    }

    private fun looksLikeIconCandidate(bitmap: Bitmap, rect: Rect): Boolean {
        val hsv = FloatArray(3)
        var sampled = 0
        var palePixels = 0
        var detailPixels = 0
        val step = maxOf(2, minOf(rect.width(), rect.height()) / 18)

        var y = rect.top
        while (y < rect.bottom) {
            var x = rect.left
            while (x < rect.right) {
                val color = bitmap.getPixel(x, y)
                Color.colorToHSV(color, hsv)
                val saturation = hsv[1]
                val value = hsv[2]
                sampled++
                if (value >= 0.74f && saturation <= 0.36f) palePixels++
                if (value in 0.18f..0.92f && saturation >= 0.10f) detailPixels++
                x += step
            }
            y += step
        }

        if (sampled == 0) return false
        val paleRatio = palePixels.toDouble() / sampled.toDouble()
        val detailRatio = detailPixels.toDouble() / sampled.toDouble()
        return paleRatio in 0.08..0.82 && detailRatio >= 0.04
    }

    private fun looksLikeEvolutionButtonContext(bitmap: Bitmap, rect: Rect): Boolean {
        val centerY = rect.centerY()
        val bandHeight = (rect.height() * 0.90f).roundToInt().coerceAtLeast(28)
        val top = (centerY - bandHeight / 2).coerceAtLeast(0)
        val bottom = (centerY + bandHeight / 2).coerceAtMost(bitmap.height)
        val left = (rect.left - rect.width() * 0.75f).roundToInt().coerceAtLeast(0)
        val right = minOf(
            bitmap.width,
            (rect.left + rect.width() * 4.75f).roundToInt(),
            (bitmap.width * 0.52f).roundToInt()
        )
        if (right <= left || bottom <= top) return false

        val hsv = FloatArray(3)
        var sampled = 0
        var greenPixels = 0
        var buttonPixels = 0
        val step = maxOf(2, minOf(right - left, bottom - top) / 20)

        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val color = bitmap.getPixel(x, y)
                Color.colorToHSV(color, hsv)
                val hue = hsv[0]
                val saturation = hsv[1]
                val value = hsv[2]
                sampled++
                if (hue in 80f..170f && saturation >= 0.16f && value >= 0.35f) greenPixels++
                if (hue in 80f..170f && saturation >= 0.28f && value in 0.42f..0.92f) buttonPixels++
                x += step
            }
            y += step
        }

        if (sampled == 0) return false
        val greenRatio = greenPixels.toDouble() / sampled.toDouble()
        val buttonRatio = buttonPixels.toDouble() / sampled.toDouble()
        return greenRatio >= 0.18 && buttonRatio >= 0.08
    }

    private fun candidateBoundsFor(pokemonName: String?): List<FloatArray> {
        return when (candidateProfileFor(pokemonName)) {
            "pre_evo" -> listOf(
                floatArrayOf(0.095f, 0.285f, 0.640f, 0.775f),
                floatArrayOf(0.075f, 0.265f, 0.625f, 0.760f),
                floatArrayOf(0.115f, 0.305f, 0.625f, 0.775f),
                floatArrayOf(0.065f, 0.285f, 0.660f, 0.805f),
                floatArrayOf(0.045f, 0.245f, 0.675f, 0.820f),
                floatArrayOf(0.125f, 0.335f, 0.640f, 0.800f)
            )
            else -> listOf(
                floatArrayOf(0.13f, 0.27f, 0.64f, 0.79f),
                floatArrayOf(0.14f, 0.28f, 0.65f, 0.80f),
                floatArrayOf(0.15f, 0.29f, 0.66f, 0.81f),
                floatArrayOf(0.16f, 0.30f, 0.67f, 0.82f),
                floatArrayOf(0.17f, 0.31f, 0.68f, 0.83f),
                floatArrayOf(0.18f, 0.32f, 0.69f, 0.84f),
                floatArrayOf(0.19f, 0.33f, 0.66f, 0.81f),
                floatArrayOf(0.20f, 0.34f, 0.67f, 0.82f)
            )
        }
    }

    private fun candidateProfileFor(pokemonName: String?): String {
        return when (pokemonName?.trim()?.uppercase()) {
            "SCATTERBUG", "SPEWPA" -> "pre_evo"
            else -> "vivillon"
        }
    }

    private fun normalizeRect(bitmap: Bitmap, rect: Rect): NormalizedDebugRect {
        return NormalizedDebugRect(
            left = rect.left.toFloat() / bitmap.width,
            top = rect.top.toFloat() / bitmap.height,
            right = rect.right.toFloat() / bitmap.width,
            bottom = rect.bottom.toFloat() / bitmap.height
        )
    }

    private fun createSignature(bitmap: Bitmap): IntArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, SIGNATURE_SIZE, SIGNATURE_SIZE, true)
        val signature = IntArray(SIGNATURE_SIZE * SIGNATURE_SIZE * 3)
        var index = 0

        for (y in 0 until SIGNATURE_SIZE) {
            for (x in 0 until SIGNATURE_SIZE) {
                val color = scaled.getPixel(x, y)
                signature[index++] = (Color.red(color) / 16f).roundToInt().coerceIn(0, 15)
                signature[index++] = (Color.green(color) / 16f).roundToInt().coerceIn(0, 15)
                signature[index++] = (Color.blue(color) / 16f).roundToInt().coerceIn(0, 15)
            }
        }

        if (scaled != bitmap) scaled.recycle()
        return signature
    }

    private fun signatureDistance(a: IntArray, b: IntArray): Double {
        if (a.size != b.size) return Double.MAX_VALUE
        var total = 0.0
        for (i in a.indices) {
            total += abs(a[i] - b[i]).toDouble()
        }
        return total / a.size.toDouble()
    }

    private fun candidateMatchScore(
        bitmap: Bitmap,
        profile: String,
        candidate: IntArray,
        reference: IntArray,
        rect: Rect
    ): Double {
        val distance = signatureDistance(candidate, reference)
        if (profile != "pre_evo") return distance

        val minDimension = minOf(bitmap.width, bitmap.height).toDouble()
        val targetWidth = minDimension * 0.075
        val targetHeight = minDimension * 0.095
        val widthPenalty = ((targetWidth - rect.width()).coerceAtLeast(0.0) / targetWidth) * 0.65
        val heightPenalty = ((targetHeight - rect.height()).coerceAtLeast(0.0) / targetHeight) * 0.55
        val centerX = rect.centerX().toDouble() / bitmap.width.toDouble()
        val centerY = rect.centerY().toDouble() / bitmap.height.toDouble()
        val targetCenterX = 0.18
        val targetCenterY = 0.705
        val positionPenalty = (abs(centerX - targetCenterX) * 1.8) + (abs(centerY - targetCenterY) * 3.0)
        val lowerButtonPenalty = if (centerY > 0.79) (centerY - 0.79) * 12.0 else 0.0
        return distance + widthPenalty + heightPenalty + positionPenalty + lowerButtonPenalty
    }

    private fun normalizedRect(
        bitmap: Bitmap,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float
    ): Rect {
        val left = (bitmap.width * minX).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (bitmap.height * minY).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = (bitmap.width * maxX).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (bitmap.height * maxY).roundToInt().coerceIn(top + 1, bitmap.height)
        return Rect(left, top, right, bottom)
    }

    private companion object {
        const val REFS_PATH = "unique_pokemon_refs/vivillon"
        const val SIGNATURE_SIZE = 18
        const val MATCH_THRESHOLD = 4.0
        const val DISTINCT_FACTOR = 0.92
        const val MIN_BUTTON_ROW_ANCHORS = 4
        const val MAX_SCAN_ANCHORS = 96
        const val MAX_SCAN_CANDIDATES = 1200
        const val DEBUG_CANDIDATE_LIMIT = 24
    }
}
