package com.mewname.app

import org.junit.Assert.*
import org.junit.Test

class BubbleMenuPlacementTest {
    @Test fun rightEdgeMenuRemainsToTheLeftAndInsideScreenAtEveryHeight() {
        for (cy in listOf(40, 400, 760)) {
            val positions = BubbleMenuPlacement.positions(400, 800, 360, cy, 80, 8)
            assertEquals(4, positions.size)
            positions.forEach { (x, y) ->
                assertTrue(x >= 0 && x + 80 <= 332)
                assertTrue(y >= 0 && y + 80 <= 800)
            }
            positions.zipWithNext().forEach { (a, b) -> assertTrue(a.second + 80 <= b.second) }
        }
    }
    @Test fun leftEdgeFallbackAvoidsCoveringBubbleWithoutRelocatingIt() {
        val positions = BubbleMenuPlacement.positions(400, 800, 40, 400, 80, 8)
        positions.forEach { (x, y) ->
            assertTrue(x >= 0 && x + 80 <= 400)
            assertTrue(y + 80 <= 364 || y >= 436)
        }
        assertEquals(4, positions.distinct().size)
    }
}