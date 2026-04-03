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
        val references = loadReferences(context)
        if (references.isEmpty()) {
            return MatchResult(
                pattern = null,
                debugInfo = VivillonDebugInfo(
                    notes = "nenhuma referência Vivillon carregada"
                )
            )
        }

        val candidateCrops = cropVivillonIconCandidates(bitmap)
        val candidateSignatures = candidateCrops.map { crop ->
            createSignature(crop.bitmap) to crop.rect
        }
        val ranked = references.map { reference ->
            val bestCandidate = candidateSignatures.minByOrNull { (signature, _) ->
                signatureDistance(signature, reference.signature)
            }
            val distance = bestCandidate?.let { (signature, _) ->
                signatureDistance(signature, reference.signature)
            } ?: Double.MAX_VALUE
            Triple(reference, distance, bestCandidate?.second)
        }.sortedBy { it.second }

        val best = ranked.firstOrNull()
        if (best == null) {
            return MatchResult(
                pattern = null,
                debugInfo = VivillonDebugInfo(
                    candidateRects = candidateCrops.map { normalizeRect(bitmap, it.rect) },
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
                candidateRects = candidateCrops.map { normalizeRect(bitmap, it.rect) },
                notes = buildString {
                    append("refs=")
                    append(references.size)
                    append("; threshold=")
                    append(MATCH_THRESHOLD)
                    append("; distinctlyBetter=")
                    append(clearlyBetter)
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

    private fun cropVivillonIconCandidates(bitmap: Bitmap): List<CandidateCrop> {
        val rects = listOf(
            normalizedRect(bitmap, 0.08f, 0.22f, 0.64f, 0.79f),
            normalizedRect(bitmap, 0.09f, 0.21f, 0.66f, 0.81f),
            normalizedRect(bitmap, 0.10f, 0.20f, 0.68f, 0.82f),
            normalizedRect(bitmap, 0.11f, 0.21f, 0.69f, 0.83f),
            normalizedRect(bitmap, 0.10f, 0.18f, 0.70f, 0.84f),
            normalizedRect(bitmap, 0.12f, 0.23f, 0.67f, 0.80f),
            normalizedRect(bitmap, 0.13f, 0.24f, 0.69f, 0.82f),
            normalizedRect(bitmap, 0.14f, 0.25f, 0.71f, 0.84f)
        )
        return rects.distinctBy { listOf(it.left, it.top, it.right, it.bottom) }
            .map { rect -> CandidateCrop(Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height()), rect) }
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
    }
}
