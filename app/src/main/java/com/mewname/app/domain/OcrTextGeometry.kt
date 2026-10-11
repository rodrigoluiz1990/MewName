package com.mewname.app.domain

import android.graphics.Bitmap
import android.graphics.Rect
import com.mewname.app.model.NormalizedDebugRect
import com.mewname.app.ocr.OcrResult
import com.mewname.app.ocr.OcrTextLine
import java.text.Normalizer
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

internal object OcrTextGeometry {
    // Shared text normalization and coordinate conversion; no recognition state.
    fun normalizeDebugRect(rect: Rect, bitmap: Bitmap): NormalizedDebugRect {
        return NormalizedDebugRect(
            left = rect.left.toFloat() / bitmap.width.toFloat(),
            top = rect.top.toFloat() / bitmap.height.toFloat(),
            right = rect.right.toFloat() / bitmap.width.toFloat(),
            bottom = rect.bottom.toFloat() / bitmap.height.toFloat()
        )
    }

    fun orderedLines(ocrResult: OcrResult): List<OcrTextLine> {
        return ocrResult.blocks
            .sortedWith(compareBy({ it.boundingBox?.top ?: Int.MAX_VALUE }, { it.boundingBox?.left ?: Int.MAX_VALUE }))
            .flatMap { block ->
                block.lines.sortedWith(compareBy({ it.boundingBox?.top ?: Int.MAX_VALUE }, { it.boundingBox?.left ?: Int.MAX_VALUE }))
            }
    }

    fun normalizeText(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .uppercase(Locale.US)
            .trim()
    }

    fun repairMojibake(text: String): String {
        if (!text.any { it == 'Ã' || it == 'Â' || it == 'â' }) return text
        return runCatching {
            String(text.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        }.getOrDefault(text)
    }

    fun normalizedLinesInRegion(
        lines: List<OcrTextLine>,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float,
        referenceBounds: Rect? = null
    ): List<String> {
        val screenBounds = referenceBounds ?: ocrBounds(lines) ?: return emptyList()
        return lines
            .filter { line ->
                line.boundingBox?.let { rect ->
                    rect.inNormalizedRegion(screenBounds, minX, maxX, minY, maxY)
                } ?: false
            }
            .map { normalizeText(it.text) }
    }

    fun rawLinesInRegion(
        lines: List<OcrTextLine>,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float,
        referenceBounds: Rect? = null
    ): List<OcrTextLine> {
        val screenBounds = referenceBounds ?: ocrBounds(lines) ?: return emptyList()
        return lines.filter { line ->
            line.boundingBox?.let { rect ->
                rect.inNormalizedRegion(screenBounds, minX, maxX, minY, maxY)
            } ?: false
        }
    }

    fun ocrBounds(lines: List<OcrTextLine>): Rect? {
        val rects = lines.mapNotNull { it.boundingBox }
        if (rects.isEmpty()) return null
        val left = rects.minOf { it.left }
        val top = rects.minOf { it.top }
        val right = rects.maxOf { it.right }
        val bottom = rects.maxOf { it.bottom }
        return Rect(left, top, right, bottom)
    }

    fun Rect.inNormalizedRegion(
        screenBounds: Rect,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float
    ): Boolean {
        val width = (screenBounds.width()).coerceAtLeast(1)
        val height = (screenBounds.height()).coerceAtLeast(1)
        val centerX = (exactCenterX() - screenBounds.left).toFloat() / width.toFloat()
        val centerY = (exactCenterY() - screenBounds.top).toFloat() / height.toFloat()
        return centerX in minX..maxX && centerY in minY..maxY
    }

    fun normalizedBitmapRect(
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
        return Rect(left, top, min(right, bitmap.width), min(bottom, bitmap.height))
    }
}
