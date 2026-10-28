package com.mewname.app.domain

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Same result as [android.graphics.Color.colorToHSV] (Skia's SkRGBToHSV), without a JNI call per pixel.
 * Visual detectors sample tens of thousands of pixels per read, where those calls dominated.
 */
internal fun colorToHsv(color: Int, hsv: FloatArray) {
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF
    val max = maxOf(r, g, b)
    val delta = max - minOf(r, g, b)
    hsv[2] = max / 255f
    if (delta == 0) {
        hsv[0] = 0f
        hsv[1] = 0f
        return
    }
    hsv[1] = delta.toFloat() / max
    var h = when (max) {
        r -> (g - b).toFloat() / delta
        g -> 2f + (b - r).toFloat() / delta
        else -> 4f + (r - g).toFloat() / delta
    }
    h *= 60f
    if (h < 0f) h += 360f
    hsv[0] = h
}

/** Visits every [stepX]-th pixel of every [stepY]-th row of [region], reading each sampled row with a single bitmap call. */
internal inline fun Bitmap.forEachSampledPixel(
    region: Rect,
    stepX: Int,
    stepY: Int = stepX,
    visit: (x: Int, y: Int, color: Int) -> Unit
) {
    val left = region.left.coerceAtLeast(0)
    val right = region.right.coerceAtMost(width)
    val bottom = region.bottom.coerceAtMost(height)
    if (left >= right) return
    val row = IntArray(right - left)
    var y = region.top.coerceAtLeast(0)
    while (y < bottom) {
        getPixels(row, 0, row.size, left, y, row.size, 1)
        var x = left
        while (x < right) {
            visit(x, y, row[x - left])
            x += stepX
        }
        y += stepY
    }
}
