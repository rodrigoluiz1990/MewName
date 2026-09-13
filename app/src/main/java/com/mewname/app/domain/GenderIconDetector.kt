package com.mewname.app.domain

import android.graphics.Bitmap
import android.graphics.Color
import com.mewname.app.ocr.OcrTextLine
import com.mewname.app.model.Gender
import com.mewname.app.model.NormalizedDebugRect
import kotlin.math.*

/** Conservative fallback for the small gray gender glyph beside the HP bar. */
internal object GenderIconDetector {
    data class Result(val gender: Gender, val rect: NormalizedDebugRect? = null, val notes: String, val region: NormalizedDebugRect? = null)
    private data class Point(val x: Double, val y: Double)

    private val maleTemplate by lazy { template(true) }
    private val femaleTemplate by lazy { template(false) }

    fun detect(bitmap: Bitmap, lines: List<OcrTextLine> = emptyList()): Result {
        val hpLine = lines.firstOrNull {
            Regex("""\d+\s*/\s*\d+\s*(PS|HP)\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(it.text) && it.boundingBox?.let { box ->
                    box.centerY().toFloat() / bitmap.height in .3f.. .65f &&
                        box.centerX().toFloat() / bitmap.width in .2f.. .8f
                } == true
        }?.boundingBox
        val region = if (hpLine != null) NormalizedDebugRect(.82f,
            ((hpLine.centerY() - bitmap.width * .105f) / bitmap.height).coerceAtLeast(.28f), .96f,
            ((hpLine.centerY() + bitmap.width * .025f) / bitmap.height).coerceAtMost(.65f))
        else NormalizedDebugRect(.82f, .34f, .96f, .57f)
        return scan(bitmap, region, if (hpLine != null) "hp" else "fallback", canExpand = true)
    }

    private fun scan(bitmap: Bitmap, region: NormalizedDebugRect, anchor: String, canExpand: Boolean): Result {
        val left = (bitmap.width * region.left).toInt()
        val top = (bitmap.height * region.top).toInt()
        val right = (bitmap.width * region.right).toInt()
        val bottom = (bitmap.height * region.bottom).toInt()
        val step = max(1, bitmap.width / 610)
        val w = (right - left) / step
        val h = (bottom - top) / step
        if (w < 3 || h < 3) return Result(Gender.UNKNOWN, notes = "Image too small")
        val mask = BooleanArray(w * h)
        val radius = max(4, (bitmap.width * .018).toInt())
        var foreground = 0
        for (y in 0 until h) for (x in 0 until w) {
            val pixel = bitmap.getPixel(left + x * step, top + y * step)
            val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
            val px = left + x * step; val py = top + y * step
            val localBackground = maxOf(
                Color.green(bitmap.getPixel((px - radius).coerceAtLeast(0), py)),
                Color.green(bitmap.getPixel((px + radius).coerceAtMost(bitmap.width - 1), py)),
                Color.green(bitmap.getPixel(px, (py - radius).coerceAtLeast(0))),
                Color.green(bitmap.getPixel(px, (py + radius).coerceAtMost(bitmap.height - 1))))
            mask[y * w + x] = r in 40..225 && g in r..min(245, r + 45) &&
                b in r..min(245, r + 50) && abs(g - b) <= 25 && localBackground - g >= 14
            if (mask[y * w + x]) foreground++
        }
        var components = 0; var rejectedSize = 0; var rejectedEdge = 0
        val queue = IntArray(mask.size)
        val edgeDetails = mutableListOf<String>()
        val matches = mutableListOf<Result>()
        val scores = mutableListOf<String>()
        for (start in mask.indices) {
            if (!mask[start]) continue
            components++
            var count = 1; var cursor = 0
            queue[0] = start; mask[start] = false
            var x0 = w; var y0 = h; var x1 = 0; var y1 = 0
            while (cursor < count) {
                val index = queue[cursor++]; val x = index % w; val y = index / w
                x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y)
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val next = ny * w + nx
                    if (mask[next]) { mask[next] = false; queue[count++] = next }
                }
            }
            val width = x1 - x0 + 1; val height = y1 - y0 + 1
            val relativeHeight = height * step.toDouble() / bitmap.width
            val aspect = width.toDouble() / height
            if (relativeHeight !in .025..0.075 || aspect !in .45..1.25) { rejectedSize++; continue }
            if (x0 == 0 || y0 == 0 || x1 == w - 1 || y1 == h - 1) {
                rejectedEdge++
                if (edgeDetails.size < 8) edgeDetails += buildList {
                    if (x0 == 0) add("left")
                    if (y0 == 0) add("top")
                    if (x1 == w - 1) add("right")
                    if (y1 == h - 1) add("bottom")
                }.joinToString("+")
                continue
            }
            val points = (0 until count).map {
                Point((queue[it] % w - x0).toDouble() / max(1, width - 1),
                    (queue[it] / w - y0).toDouble() / max(1, height - 1))
            }
            val male = score(points, maleTemplate)
            val female = score(points, femaleTemplate)
            scores += "male=%.3f female=%.3f aspect=%.2f".format(java.util.Locale.US, male, female, aspect)
            val gender = when {
                male < .065 && female - male > .018 && aspect > .78 -> Gender.MALE
                female < .065 && male - female > .018 && aspect < .9 -> Gender.FEMALE
                else -> Gender.UNKNOWN
            }
            if (gender != Gender.UNKNOWN) matches += Result(gender, NormalizedDebugRect(
                (left + x0 * step).toFloat() / bitmap.width, (top + y0 * step).toFloat() / bitmap.height,
                (left + (x1 + 1) * step).toFloat() / bitmap.width, (top + (y1 + 1) * step).toFloat() / bitmap.height), scores.last())
        }
        val diagnostics = "anchor=$anchor; foreground=$foreground; " +
            "components=$components; rejectedSize=$rejectedSize; rejectedEdge=$rejectedEdge; " +
            "edges=${edgeDetails.joinToString(",")}; scored=${scores.size}; accepted=${matches.size}; ${scores.take(8).joinToString("; ")}"
        // Never classify the clipped fragment: read the pixels again with a bounded margin.
        // A successful first pass remains unchanged, including female glyphs already recognized.
        if (matches.isEmpty() && rejectedEdge > 0 && canExpand) {
            val verticalMargin = bitmap.width * .04f / bitmap.height
            val expanded = NormalizedDebugRect(
                (region.left - .025f).coerceAtLeast(.78f),
                (region.top - verticalMargin).coerceAtLeast(.28f),
                (region.right + .025f).coerceAtMost(.99f),
                (region.bottom + verticalMargin).coerceAtMost(.65f))
            val retried = scan(bitmap, expanded, anchor, canExpand = false)
            return retried.copy(notes = "edge_retry: first={$diagnostics}; expanded={${retried.notes}}")
        }
        return if (matches.size == 1) matches.single().copy(notes = diagnostics, region = region)
        else Result(Gender.UNKNOWN, notes = diagnostics, region = region)
    }
    private fun score(actual: List<Point>, expected: List<Point>): Double {
        fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)
        // Symmetric distance penalizes both missing arrow/cross and extra foreground.
        return (actual.map { a -> expected.minOf { distance(a, it) } }.average() +
            expected.map { e -> actual.minOf { distance(e, it) } }.average()) / 2
    }

    private fun template(male: Boolean): List<Point> = buildList {
        val cx = if (male) .34 else .5; val cy = if (male) .66 else .31
        val rx = if (male) .29 else .43; val ry = if (male) .29 else .26
        for (i in 0 until 60) {
            val angle = i * 2 * PI / 60
            add(Point(cx + rx * cos(angle), cy + ry * sin(angle)))
        }
        fun line(x0: Double, y0: Double, x1: Double, y1: Double) {
            for (i in 0..20) { val t = i / 20.0; add(Point(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t)) }
        }
        if (male) {
            line(.55, .45, .95, .05); line(.57, .05, .95, .05); line(.95, .05, .95, .43)
        } else {
            line(.5, .57, .5, .95); line(.22, .77, .78, .77)
        }
    }
}
