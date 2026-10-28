package com.mewname.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Items laid out along the list axis with a 6 px gap, like the name pattern chips. */
class ReorderStepTest {
    private fun middles(order: List<String>, widths: Map<String, Int>): Map<String, Float> {
        var x = 0f
        return order.associateWith { id -> (x + widths.getValue(id) / 2f).also { x += widths.getValue(id) + 6 } }
    }

    private fun step(order: List<String>, widths: Map<String, Int>, center: Float): List<String> {
        val middle = middles(order, widths)
        val to = reorderStepTarget(order, "A", center, middle::get) ?: return order
        return order.toMutableList().apply { add(to, removeAt(indexOf("A"))) }
    }

    @Test fun narrowChipHeldOverAWideOneDoesNotSwapBackAndForth() {
        // Held at the edge, the same center is evaluated every frame of the auto-scroll.
        val widths = mapOf("A" to 50, "B" to 300, "C" to 80)
        var order = listOf("A", "B", "C")
        val center = 230f
        val seen = (1..10).map { order = step(order, widths, center); order }
        assertEquals(listOf("B", "A", "C"), seen.first())
        assertEquals(1, seen.toSet().size)
    }

    @Test fun movesOnlyAfterPassingTheNeighbourMiddle() {
        val widths = mapOf("A" to 100, "B" to 100)
        assertNull(reorderStepTarget(listOf("A", "B"), "A", 150f, middles(listOf("A", "B"), widths)::get))
        assertEquals(1, reorderStepTarget(listOf("A", "B"), "A", 160f, middles(listOf("A", "B"), widths)::get))
    }

    @Test fun dragsBackToTheLeft() {
        val widths = mapOf("A" to 60, "B" to 200, "C" to 90)
        var order = listOf("B", "C", "A")
        for (center in listOf(260f, 120f, 40f)) order = step(order, widths, center)
        assertEquals(listOf("A", "B", "C"), order)
    }

    @Test fun chipScrolledOutOfViewIsNotACandidate() {
        assertNull(reorderStepTarget(listOf("A", "B"), "A", 999f) { null })
    }
}
