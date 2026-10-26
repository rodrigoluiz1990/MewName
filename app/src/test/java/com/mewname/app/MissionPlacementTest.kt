package com.mewname.app

import org.junit.Assert.*
import org.junit.Test

class MissionPlacementTest {
    @Test fun splitPreservesEveryMissionInOrderWithoutDuplicates() {
        for (count in 0..100) {
            val pending = (0 until count).toList()
            for (mode in MissionPlacement.entries) {
                val (above, below) = mode.split(pending)
                assertEquals(pending, above + below)
                when (mode) {
                    MissionPlacement.BOTH -> assertTrue(above.size - below.size in 0..1)
                    MissionPlacement.ABOVE -> assertTrue(below.isEmpty())
                    else -> assertTrue(above.isEmpty())
                }
            }
        }
    }
    @Test fun completionRedistributesRemainingMissions() {
        assertEquals(listOf(1,2,3) to listOf(4,5), MissionPlacement.BOTH.split(listOf(1,2,3,4,5)))
        assertEquals(listOf(1,3) to listOf(4,5), MissionPlacement.BOTH.split(listOf(1,3,4,5)))
        assertEquals(listOf(5) to emptyList<Int>(), MissionPlacement.BOTH.split(listOf(5)))
        assertEquals(emptyList<Int>() to emptyList<Int>(), MissionPlacement.BOTH.split(emptyList<Int>()))
    }
}
