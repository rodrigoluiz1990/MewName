package com.mewname.app.domain

import android.graphics.Bitmap
import com.mewname.app.ocr.OcrResult
import kotlin.math.abs
import kotlin.math.max

/** Counts the repeated raid emblems immediately above the CP line. */
internal object RaidLevelIconDetector {
    fun detect(result: OcrResult): Int? {
        val bitmap = result.bitmap ?: return null
        val lines = result.blocks.flatMap { it.lines }.mapNotNull { line ->
            line.boundingBox?.let { box -> BattleOcrLine(line.text,
                box.left.toFloat() / bitmap.width, box.top.toFloat() / bitmap.height,
                box.right.toFloat() / bitmap.width, box.bottom.toFloat() / bitmap.height) }
        }
        val anchor = cpAnchor(lines)
        val cpTop = ((anchor?.top ?: .20f) * bitmap.height).toInt()
        debug("levelAnchor source=${if (anchor == null) "fixed" else "digits"} text=${anchor?.text} box=$anchor cpTop=$cpTop bitmap=${bitmap.width}x${bitmap.height}")
        return detect(bitmap, cpTop)
    }

    // The small PC label sits below the top of its large digits. Using that label
    // includes part of the number in the icon crop and corrupts the symbol count.
    internal fun cpAnchor(lines: List<BattleOcrLine>): BattleOcrLine? = lines.filter {
        (it.top + it.bottom) / 2f in .13f.. .28f && it.left < .65f && it.right > .35f &&
            RaidBossResolver.parseBossCp(it.text) != null
    }.maxByOrNull { it.bottom - it.top }

    private fun debug(message: String) {
        if (com.mewname.app.BuildConfig.DEBUG) android.util.Log.d("RaidReadTiming", message)
    }

    internal fun detect(bitmap: Bitmap, cpTop: Int): Int? {
        if (bitmap.width < 120 || bitmap.height < 240) return null
        val left = (bitmap.width * 0.20f).toInt()
        val right = (bitmap.width * 0.80f).toInt()
        val top = max((bitmap.height * 0.10f).toInt(), cpTop - (bitmap.height * 0.075f).toInt())
        val bottom = (cpTop - bitmap.height * 0.004f).toInt().coerceAtMost((bitmap.height * 0.23f).toInt())
        debug("levelRegion left=$left top=$top right=$right bottom=$bottom")
        if (bottom - top < 12 || right - left < 80) return null
        // One native read for the region instead of hundreds of thousands of getPixel calls.
        val pixels = IntArray((right - left) * (bottom - top))
        bitmap.getPixels(pixels, 0, right - left, left, top, right - left, bottom - top)
        fun pixel(x: Int, y: Int) = pixels[(y - top) * (right - left) + x - left]
        connectedIconCount(bitmap, left, top, right, bottom, pixels)?.let {
            debug("levelResult method=components count=$it")
            return it
        }

        val scores = IntArray(right - left)
        for (x in left + 1 until right - 1) {
            var score = 0
            for (y in top + 1 until bottom - 1 step 2) {
                val horizontal = abs(luma(pixel(x + 1, y)) - luma(pixel(x - 1, y)))
                val vertical = abs(luma(pixel(x, y + 1)) - luma(pixel(x, y - 1)))
                if (horizontal + vertical >= 86) score++
            }
            scores[x - left] = score
        }
        val smoothed = IntArray(scores.size) { index ->
            var total = 0
            var samples = 0
            for (offset in -2..2) {
                val position = index + offset
                if (position in scores.indices) {
                    total += scores[position]
                    samples++
                }
            }
            if (samples == 0) 0 else total / samples
        }
        val positive = smoothed.filter { it > 0 }.sorted()
        if (positive.size < smoothed.size / 12) return null
        val percentile = positive[(positive.size * 0.62f).toInt().coerceIn(positive.indices)]
        val threshold = max(2, percentile)
        val active = BooleanArray(smoothed.size) { smoothed[it] >= threshold }
        closeSmallGaps(active, max(2, (bitmap.width * 0.006f).toInt()))

        val minWidth = max(8, (bitmap.width * 0.024f).toInt())
        val maxWidth = max(minWidth + 1, (bitmap.width * 0.105f).toInt())
        val runs = mutableListOf<IntRange>()
        var start = -1
        active.forEachIndexed { index, value ->
            if (value && start < 0) start = index
            if ((!value || index == active.lastIndex) && start >= 0) {
                val end = if (value && index == active.lastIndex) index else index - 1
                if (end - start + 1 in minWidth..maxWidth) runs += start..end
                start = -1
            }
        }
        val centered = runs.filter { run ->
            val center = left + (run.first + run.last) / 2f
            center in bitmap.width * 0.25f..bitmap.width * 0.75f
        }
        val count = centered.size
        debug("levelProjection count=$count runs=$centered")
        if (count !in 1..6) return null

        val centers = centered.map { (it.first + it.last) / 2f }.sorted()
        if (centers.size >= 3) {
            val gaps = centers.zipWithNext { a, b -> b - a }
            val average = gaps.average()
            if (average <= 0.0 || gaps.any { abs(it - average) > average * 0.45 }) return null
        }
        return count
    }

    private fun connectedIconCount(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int, pixels: IntArray): Int? {
        val width = right - left
        val height = bottom - top
        fun pixel(x: Int, y: Int) = pixels[(y - top) * width + x - left]
        val mask = BooleanArray(width * height)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val absoluteX = left + x
                val absoluteY = top + y
                val horizontal = abs(luma(pixel(absoluteX + 1, absoluteY)) - luma(pixel(absoluteX - 1, absoluteY)))
                val vertical = abs(luma(pixel(absoluteX, absoluteY + 1)) - luma(pixel(absoluteX, absoluteY - 1)))
                mask[y * width + x] = horizontal + vertical >= 68
            }
        }
        val visited = BooleanArray(mask.size)
        val queue = IntArray(mask.size)
        data class Component(val left: Int, val top: Int, val right: Int, val bottom: Int, val pixels: Int)
        val components = mutableListOf<Component>()
        for (seed in mask.indices) {
            if (!mask[seed] || visited[seed]) continue
            var head = 0
            var tail = 0
            queue[tail++] = seed
            visited[seed] = true
            var minX = width
            var maxX = 0
            var minY = height
            var maxY = 0
            var componentPixels = 0
            while (head < tail) {
                val position = queue[head++]
                val x = position % width
                val y = position / width
                minX = minOf(minX, x); maxX = maxOf(maxX, x)
                minY = minOf(minY, y); maxY = maxOf(maxY, y)
                componentPixels++
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nextX = x + dx
                    val nextY = y + dy
                    if (nextX !in 0 until width || nextY !in 0 until height) continue
                    val next = nextY * width + nextX
                    if (mask[next] && !visited[next]) {
                        visited[next] = true
                        queue[tail++] = next
                    }
                }
            }
            components += Component(minX, minY, maxX, maxY, componentPixels)
        }
        val candidates = components.filter { component ->
            val componentWidth = component.right - component.left + 1
            val componentHeight = component.bottom - component.top + 1
            componentWidth in max(10, (bitmap.width * 0.025f).toInt())..max(12, (bitmap.width * 0.11f).toInt()) &&
                componentHeight in max(10, (bitmap.height * 0.014f).toInt())..max(12, (bitmap.height * 0.065f).toInt()) &&
                component.pixels >= 24 &&
                left + (component.left + component.right) / 2f in bitmap.width * 0.25f..bitmap.width * 0.75f
        }.sortedBy { it.left }
        if (candidates.size !in 1..6) return null
        val widths = candidates.map { it.right - it.left + 1 }
        val medianWidth = widths.sorted()[widths.size / 2].toFloat()
        if (widths.any { abs(it - medianWidth) > medianWidth * 0.5f }) return null
        val centers = candidates.map { (it.left + it.right) / 2f }
        if (centers.size >= 3) {
            val gaps = centers.zipWithNext { a, b -> b - a }
            val average = gaps.average()
            if (gaps.any { abs(it - average) > average * 0.4 }) return null
        }
        return candidates.size
    }
    private fun closeSmallGaps(values: BooleanArray, maxGap: Int) {
        var index = 0
        while (index < values.size) {
            if (values[index]) { index++; continue }
            val start = index
            while (index < values.size && !values[index]) index++
            if (start > 0 && index < values.size && index - start <= maxGap) {
                for (fill in start until index) values[fill] = true
            }
        }
    }

    private fun luma(color: Int): Int {
        val red = color shr 16 and 0xff
        val green = color shr 8 and 0xff
        val blue = color and 0xff
        return (red * 77 + green * 150 + blue * 29) shr 8
    }
}
