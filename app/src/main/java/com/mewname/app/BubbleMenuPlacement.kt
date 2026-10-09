package com.mewname.app

import kotlin.math.*

/** Pack successive arcs inward from the bubble; never move the bubble or overlap touch targets. */
internal object BubbleMenuPlacement {
    fun positions(width: Int, height: Int, cx: Int, cy: Int, size: Int, gap: Int, count: Int = 4): List<Pair<Int, Int>> {
        if (count <= 0 || size <= 0) return emptyList()
        val step = size + gap
        val side = if (cx >= width / 2) -1 else 1
        val result = mutableListOf<Pair<Int, Int>>()
        fun fits(p: Pair<Int, Int>, existing: List<Pair<Int, Int>>): Boolean {
            val (x, y) = p
            if (x < gap || y < gap || x + size > width - gap || y + size > height - gap) return false
            if (abs(x + size / 2 - cx) < step && abs(y + size / 2 - cy) < step) return false
            return existing.none { abs(it.first - x) < step && abs(it.second - y) < step }
        }
        val rings = ceil(hypot(width.toDouble(), height.toDouble()) / step).toInt()
        for (ring in 0..rings) {
            val radius = step * (1.4 + ring * 1.4)
            // First traverse the inward arc from top to bottom, then the remaining side.
            val angles = (-90..90 step 2).toList() + (92..270 step 2).toList()
            for (degrees in angles) {
                val angle = Math.toRadians(degrees.toDouble())
                val point = (cx + side * radius * cos(angle) - size / 2).roundToInt() to
                    (cy + radius * sin(angle) - size / 2).roundToInt()
                if (fits(point, result)) result.add(point)
                if (result.size == count) return result
            }
        }
        // In very constrained windows, use all available space in distance-ordered bands.
        // Repack rather than clipping outer arcs or placing targets on top of each other.
        val packed = mutableListOf<Pair<Int, Int>>()
        val slots = (gap..(width - gap - size) step step).flatMap { x ->
            (gap..(height - gap - size) step step).map { y -> x to y }
        }.sortedWith(compareBy<Pair<Int, Int>> {
            (hypot((it.first + size / 2 - cx).toDouble(), (it.second + size / 2 - cy).toDouble()) / (step * 1.4)).toInt()
        }.thenBy { it.second }.thenBy { if (side > 0) it.first else -it.first })
        for (p in slots) if (fits(p, packed)) {
            packed.add(p)
            if (packed.size == count) return packed
        }
        return if (packed.size > result.size) packed else result
    }
}
