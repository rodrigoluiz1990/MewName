package com.mewname.app.ocr

import android.graphics.Rect
import java.util.Locale

/** Only re-read short, standalone badges beside the height, never nicknames or XL candy. */
internal fun sizeBadgeCrop(line: OcrTextLine, width: Int, height: Int): Rect? {
    if (width <= 0 || height <= 0 || line.text.trim().uppercase(Locale.ROOT) !in setOf("XS", "XL")) return null
    val bounds = line.boundingBox ?: return null
    if (bounds.centerX() !in (width * .65f).toInt()..(width * .96f).toInt() ||
        bounds.centerY() !in (height * .35f).toInt()..(height * .68f).toInt() ||
        bounds.width() > width * .12f || bounds.height() > height * .04f) return null
    return Rect(
        (bounds.centerX() - width * .10f).toInt().coerceAtLeast(0),
        (bounds.centerY() - height * .05f).toInt().coerceAtLeast(0),
        (bounds.centerX() + width * .06f).toInt().coerceAtMost(width),
        (bounds.centerY() + height * .03f).toInt().coerceAtMost(height)
    ).takeIf { it.width() > 0 && it.height() > 0 }
}

internal fun confirmedSizeBadge(original: String, detailLines: List<String>): String? {
    val source = original.trim().uppercase(Locale.ROOT)
    val expected = when (source) { "XS" -> "XXS"; "XL" -> "XXL"; else -> return null }
    val sizes = detailLines.map { it.trim().uppercase(Locale.ROOT) }
        .filter { it in setOf("XS", "XL", "XXS", "XXL") }.toSet()
    return expected.takeIf { sizes == setOf(expected) }
}