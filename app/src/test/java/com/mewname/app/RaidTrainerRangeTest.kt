package com.mewname.app

import com.mewname.app.domain.raidTrainerRange
import org.junit.Assert.*
import org.junit.Test

class RaidTrainerRangeTest {
    @Test fun roundsUpInsteadOfUnderestimatingGroupSize() {
        assertEquals(2..4, raidTrainerRange(listOf(1.1, 2.0, 3.01)))
    }
    @Test fun soloAndExactBoundaries() {
        assertEquals(1..1, raidTrainerRange(listOf(0.7, 1.0)))
        assertEquals(2..2, raidTrainerRange(listOf(2.0)))
    }
    @Test fun ignoresInvalidDataWithoutInventingAGroup() {
        assertNull(raidTrainerRange(emptyList()))
        assertNull(raidTrainerRange(listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)))
        assertEquals(3..3, raidTrainerRange(listOf(Double.NaN, 2.7)))
    }
}
