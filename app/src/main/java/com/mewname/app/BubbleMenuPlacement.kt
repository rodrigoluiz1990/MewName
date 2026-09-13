package com.mewname.app

/** Coordinates for the options only: opening the menu never relocates the bubble. */
internal object BubbleMenuPlacement {
    fun positions(width: Int, height: Int, cx: Int, cy: Int, size: Int, gap: Int, count: Int = 4): List<Pair<Int, Int>> {
        if (count <= 0) return emptyList()
        val step = size + gap
        val total = count * size + (count - 1) * gap
        val top = (cy - total / 2).coerceIn(gap, maxOf(gap, height - gap - total))
        val bubbleRadius = size * 0.5f
        val outerX = (cx - bubbleRadius - gap - size).toInt()
        val maxX = maxOf(gap, width - gap - size)
        val arc = List(count) { index -> if (count == 1) 0 else (kotlin.math.sin(Math.PI * index / (count - 1)) * size / 3).toInt() }
        if (outerX >= gap) {
            return List(count) { index ->
                (outerX - arc[index]).coerceIn(gap, maxX) to top + index * step
            }
        }
        // At the left edge there is physically no room to its left. Keep the bubble
        // still and place options above/below it, instead of moving it to the center.
        val slots = generateSequence(gap) { it + step }
            .takeWhile { it + size <= height - gap }
            .filter { it + size <= cy - bubbleRadius - gap || it >= cy + bubbleRadius + gap }
            .sortedBy { kotlin.math.abs(it + size / 2 - cy) }
            .take(count).toList().sorted()
        return List(count) { index ->
            gap.coerceAtMost(maxX) to (slots.getOrNull(index) ?: (top + index * step))
        }
    }
}