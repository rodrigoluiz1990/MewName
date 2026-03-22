package com.mewname.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import com.mewname.app.model.VivillonPattern
import kotlin.math.abs
import kotlin.math.roundToInt

class VivillonIconMatcher {
    private data class ReferenceSignature(
        val pattern: VivillonPattern,
        val signature: IntArray
    )

    @Volatile
    private var cachedReferences: List<ReferenceSignature>? = null

    fun detectPattern(context: Context, bitmap: Bitmap): VivillonPattern? {
        val references = loadReferences(context)
        if (references.isEmpty()) return null

        val candidateSignatures = cropVivillonIconCandidates(bitmap).map { crop ->
            createSignature(crop)
        }
        val ranked = references.map { reference ->
            reference to (candidateSignatures.minOfOrNull { signature ->
                signatureDistance(signature, reference.signature)
            } ?: Double.MAX_VALUE)
        }.sortedBy { it.second }

        val best = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1)
        val bestDistance = best.second
        val clearlyBetter = second == null || bestDistance <= second.second * DISTINCT_FACTOR
        return if (bestDistance <= MATCH_THRESHOLD || clearlyBetter) best.first.pattern else null
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

    private fun cropVivillonIconCandidates(bitmap: Bitmap): List<Bitmap> {
        val rects = listOf(
            normalizedRect(bitmap, 0.105f, 0.175f, 0.785f, 0.845f),
            normalizedRect(bitmap, 0.11f, 0.17f, 0.78f, 0.83f),
            normalizedRect(bitmap, 0.115f, 0.18f, 0.79f, 0.84f),
            normalizedRect(bitmap, 0.10f, 0.16f, 0.78f, 0.82f),
            normalizedRect(bitmap, 0.12f, 0.19f, 0.80f, 0.85f),
            normalizedRect(bitmap, 0.125f, 0.17f, 0.775f, 0.83f)
        )
        return rects.distinctBy { listOf(it.left, it.top, it.right, it.bottom) }
            .map { rect -> Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height()) }
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
        const val REFS_PATH = "vivillon_refs"
        const val SIGNATURE_SIZE = 18
        const val MATCH_THRESHOLD = 4.0
        const val DISTINCT_FACTOR = 0.92
    }
}
