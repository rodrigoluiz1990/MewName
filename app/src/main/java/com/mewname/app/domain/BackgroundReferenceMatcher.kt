package com.mewname.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.roundToInt

class BackgroundReferenceMatcher {
    data class DebugResult(
        val isSpecial: Boolean?,
        val bestReferenceName: String?,
        val bestDistance: Double?,
        val referenceCount: Int
    )

    private data class ReferenceSignature(
        val name: String,
        val signature: IntArray
    )

    private data class MatchResult(
        val isNormal: Boolean,
        val distance: Double?
    )

    @Volatile
    private var cachedNormalReferences: List<ReferenceSignature>? = null

    fun isSpecialBackground(context: Context, bitmap: Bitmap): Boolean? {
        return debugSpecialBackground(context, bitmap).isSpecial
    }

    fun debugSpecialBackground(context: Context, bitmap: Bitmap): DebugResult {
        val references = loadNormalReferences(context)

        if (references.isEmpty()) {
            return DebugResult(
                isSpecial = null,
                bestReferenceName = null,
                bestDistance = null,
                referenceCount = 0
            )
        }

        val candidateSignatures = cropBackgroundCandidates(bitmap).map { crop ->
            createSignature(crop)
        }
        val ranked = references.map { reference ->
            reference to (candidateSignatures.minOfOrNull { signature ->
                signatureDistance(signature, reference.signature)
            } ?: Double.MAX_VALUE)
        }.sortedBy { it.second }
        val bestDistance = ranked.firstOrNull()?.second
        val secondDistance = ranked.getOrNull(1)?.second
        val clearlyNormal = bestDistance != null && (
            bestDistance <= NORMAL_MATCH_THRESHOLD ||
                (secondDistance != null && bestDistance <= secondDistance * DISTINCT_FACTOR)
            )

        val bestReference = ranked.firstOrNull()?.first?.name
        return DebugResult(
            isSpecial = !clearlyNormal,
            bestReferenceName = bestReference,
            bestDistance = bestDistance,
            referenceCount = references.size
        )
    }

    private fun loadNormalReferences(context: Context): List<ReferenceSignature> {
        cachedNormalReferences?.let { return it }
        synchronized(this) {
            cachedNormalReferences?.let { return it }

            val loaded = runCatching {
                context.assets.list(NORMAL_REFS_PATH)
                    ?.filter { fileName ->
                        fileName.endsWith(".png", ignoreCase = true) ||
                            fileName.endsWith(".jpg", ignoreCase = true) ||
                            fileName.endsWith(".jpeg", ignoreCase = true) ||
                            fileName.endsWith(".webp", ignoreCase = true)
                    }
                    ?.mapNotNull { fileName ->
                        context.assets.open("$NORMAL_REFS_PATH/$fileName").use { input ->
                            val bitmap = BitmapFactory.decodeStream(input) ?: return@mapNotNull null
                            ReferenceSignature(
                                name = fileName,
                                signature = createSignature(bitmap)
                            )
                        }
                    }
                    ?: emptyList()
            }.getOrElse { emptyList() }

            cachedNormalReferences = loaded
            return loaded
        }
    }

    private fun cropBackgroundCandidates(bitmap: Bitmap): List<Bitmap> {
        val rects = listOf(
            normalizedRect(bitmap, 0.06f, 0.78f, 0.05f, 0.36f),
            normalizedRect(bitmap, 0.10f, 0.74f, 0.06f, 0.34f),
            normalizedRect(bitmap, 0.08f, 0.80f, 0.08f, 0.30f)
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
                signature[index++] = ((android.graphics.Color.red(color) / 16f).roundToInt()).coerceIn(0, 15)
                signature[index++] = ((android.graphics.Color.green(color) / 16f).roundToInt()).coerceIn(0, 15)
                signature[index++] = ((android.graphics.Color.blue(color) / 16f).roundToInt()).coerceIn(0, 15)
            }
        }

        if (scaled != bitmap) {
            scaled.recycle()
        }
        return signature
    }

    private fun signatureDistance(a: IntArray, b: IntArray): Double {
        if (a.size != b.size) return Double.MAX_VALUE
        var total = 0.0
        for (index in a.indices) {
            total += abs(a[index] - b[index]).toDouble()
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
        const val NORMAL_REFS_PATH = "background_refs/normal"
        const val SIGNATURE_SIZE = 8
        const val NORMAL_MATCH_THRESHOLD = 4.8
        const val DISTINCT_FACTOR = 0.93
    }
}
