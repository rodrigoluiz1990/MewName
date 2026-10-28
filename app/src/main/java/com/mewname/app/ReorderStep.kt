package com.mewname.app

/**
 * New index for [dragged] in a drag-to-reorder list, one step at a time, once its [center] passes the middle of a
 * visible neighbour along the list axis. Swapping with whatever item lay under the center made items of different
 * sizes trade places every frame, which edge auto-scroll turned into a loop to the end of the list.
 */
internal fun reorderStepTarget(order: List<String>, dragged: String, center: Float, middle: (String) -> Float?): Int? {
    val from = order.indexOf(dragged)
    if (from < 0) return null
    return when {
        order.getOrNull(from + 1)?.let(middle)?.let { center > it } == true -> from + 1
        order.getOrNull(from - 1)?.let(middle)?.let { center < it } == true -> from - 1
        else -> null
    }
}
