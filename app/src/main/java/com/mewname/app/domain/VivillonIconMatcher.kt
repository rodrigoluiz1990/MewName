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
            (createSignature(crop.bitmap) to crop.rect).also { crop.bitmap.recycle() }
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
        val second = ranked.firstOrNull { it.first.pattern != best.first.pattern }
        val bestDistance = best.second
        val clearlyBetter = second == null || bestDistance <= second.second * DISTINCT_FACTOR
        val threshold = if (candidateProfile == "pre_evo") BADGE_MATCH_THRESHOLD else MATCH_THRESHOLD
        val accepted = if (candidateProfile == "pre_evo") bestDistance <= threshold && clearlyBetter
            else bestDistance <= threshold || clearlyBetter
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
                    append(threshold)
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
                            val signature = createSignature(bitmap)
                            bitmap.recycle()
                            ReferenceSignature(
                                pattern = pattern,
                                fileName = fileName,
                                signature = signature
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

    private fun preEvolutionIconCandidateRects(bitmap: Bitmap): List<Rect> = circularBadgeCandidates(bitmap)

    /** Locate the white circular pattern badge by its rim and adjacent green button. */
    private fun circularBadgeCandidates(bitmap: Bitmap): List<Rect> {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        fun pixel(x: Int, y: Int) = pixels[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)]
        fun pale(c: Int) = minOf(Color.red(c), Color.green(c), Color.blue(c)) >= 205 &&
            maxOf(Color.red(c), Color.green(c), Color.blue(c)) - minOf(Color.red(c), Color.green(c), Color.blue(c)) < 40
        fun green(c: Int) = Color.green(c) > Color.red(c) + 12 &&
            Color.green(c) > Color.blue(c) + 4 && Color.green(c) > 110
        val angles = (0 until 16).map { it * Math.PI / 8.0 }
        val matches = mutableListOf<Pair<Rect, Int>>()
        val step = (width * .005).roundToInt().coerceAtLeast(3)
        for (sizePart in 60..96 step 4) {
            val size = (width * sizePart / 1000.0).roundToInt()
            val radius = size * .43
            for (cy in (height * .54).toInt()..(height * .93).toInt() step step) {
                for (cx in (width * .10).toInt()..(width * .32).toInt() step step) {
                    // The top of the badge may touch the white card; below it must be a button.
                    if (!green(pixel(cx + size, cy + size / 2))) continue
                    val rim = angles.count { pale(pixel(
                        (cx + kotlin.math.cos(it) * radius).roundToInt(),
                        (cy + kotlin.math.sin(it) * radius).roundToInt())) }
                    if (rim < 13) continue
                    var detail = 0
                    for (dy in -2..2) for (dx in -2..2) {
                        if (!pale(pixel(cx + dx * size / 9, cy + dy * size / 9))) detail++
                    }
                    if (detail < 7 || detail > 24) continue
                    matches += candidateRectAround(bitmap, cx, cy, size, size) to (rim * 3 + detail)
                }
            }
        }
        // Refine around geometric anchors instead of penalizing a badge's vertical position.
        return matches.sortedByDescending { it.second }.take(30).flatMap { (rect, _) ->
            buildList {
                for (dx in listOf(-step / 2, 0, step / 2))
                    for (dy in listOf(-step / 2, 0, step / 2))
                        for (delta in listOf(-step, 0, step)) {
                            val size = rect.width() + delta
                            add(candidateRectAround(bitmap, rect.centerX() + dx, rect.centerY() + dy, size, size))
                        }
            }
        }.distinctBy { listOf(it.left, it.top, it.right, it.bottom) }
    }
    private fun candidateRectAround(bitmap: Bitmap, centerX: Int, centerY: Int, width: Int, height: Int): Rect {
        val actualWidth = width.coerceIn(2, bitmap.width)
        val actualHeight = height.coerceIn(2, bitmap.height)
        val left = (centerX - actualWidth / 2).coerceIn(0, bitmap.width - actualWidth)
        val top = (centerY - actualHeight / 2).coerceIn(0, bitmap.height - actualHeight)
        return Rect(left, top, left + actualWidth, top + actualHeight)
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
        var weights = 0.0
        for (index in a.indices step 3) {
            val x = (index / 3) % SIGNATURE_SIZE
            val y = (index / 3) / SIGNATURE_SIZE
            // Most of the distinguishing colour is on the wings, not the white circular rim.
            val weight = if (x in 3..14 && y in 5..13) 3.0 else 1.0
            val rgb = (abs(a[index] - b[index]) + abs(a[index + 1] - b[index + 1]) +
                abs(a[index + 2] - b[index + 2])) / 3.0
            val chroma = (abs((a[index] - a[index + 1]) - (b[index] - b[index + 1])) +
                abs((a[index + 1] - a[index + 2]) - (b[index + 1] - b[index + 2]))) / 2.0
            total += weight * (rgb * .6 + chroma * .4)
            weights += weight
        }
        return total / weights
    }
    private fun candidateMatchScore(
        bitmap: Bitmap,
        profile: String,
        candidate: IntArray,
        reference: IntArray,
        rect: Rect
    ): Double {
        if (profile == "pre_evo") return signatureDistance(candidate, reference)
        // Preserve the existing comparison for an already evolved Vivillon.
        return candidate.indices.sumOf { abs(candidate[it] - reference[it]).toDouble() } / candidate.size
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
        const val BADGE_MATCH_THRESHOLD = 1.8
        const val DISTINCT_FACTOR = 0.92



        const val DEBUG_CANDIDATE_LIMIT = 24
    }
}
