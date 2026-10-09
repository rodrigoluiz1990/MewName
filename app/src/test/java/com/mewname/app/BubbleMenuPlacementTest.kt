package com.mewname.app

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class BubbleMenuPlacementTest {
    @Test fun allShortcutsFitWithoutOverlapAtEdgesAndCenterOnBothOrientations() {
        for ((width,height) in listOf(320 to 400,600 to 272,320 to 480,360 to 800,412 to 915,640 to 320,800 to 360)) {
            for (cx in listOf(24,width/2,width-24)) for (cy in listOf(24,height/2,height-24)) {
                for (count in listOf(1,4,6,12,19)) {
                    val positions = BubbleMenuPlacement.positions(width,height,cx,cy,56,8,count)
                    val case = "$width/$height at $cx/$cy with $count"
                    assertEquals(case, count, positions.size)
                    positions.forEachIndexed { index,(x,y) ->
                        assertTrue(case, x >= 8 && x+56 <= width-8 && y >= 8 && y+56 <= height-8)
                        assertTrue(case, abs(x+28-cx)>=64 || abs(y+28-cy)>=64)
                        positions.take(index).forEach { (px,py) ->
                            assertTrue(case, abs(x-px)>=64 || abs(y-py)>=64)
                        }
                    }
                }
            }
        }
    }
    @Test fun enablingMoreOptionsKeepsTheInnerArcOrderOnRegularScreens() {
        val first = BubbleMenuPlacement.positions(412,915,388,440,56,8,6)
        val all = BubbleMenuPlacement.positions(412,915,388,440,56,8,19)
        assertEquals(first,all.take(6))
    }
    @Test fun leftEdgeUsesAvailableSpaceToTheRightWithoutMovingAnchor() {
        val positions = BubbleMenuPlacement.positions(400,800,24,400,56,8,4)
        assertEquals(4,positions.size)
        assertTrue(positions.any { it.first > 80 })
    }
}
