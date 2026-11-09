package com.mewname.app

import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class RaidEnhancedMoveTest {
    @Test fun translatesEnhancedMoveWithoutDroppingPlus() {
        assertEquals("Vento Ominoso+", raidMoveLabel("OMINOUS_WIND_PLUS", mapOf(raidKey("Ominous Wind") to "Vento Ominoso")))
        assertEquals("Ominous Wind+", raidMoveLabel("OMINOUS_WIND_PLUS", mapOf(raidKey("Ominous Wind") to "Ominous Wind")))
        assertEquals("Exact translation", raidMoveLabel("OMINOUS_WIND_PLUS", mapOf(raidKey("OMINOUS_WIND_PLUS") to "Exact translation")))
    }
    @Test fun missingMetadataStillProvidesGhostIconWithoutChangingIdentityOrEliteStatus() {
        val move = RaidMetadata(emptyMap(), emptyMap()).move("OMINOUS_WIND_PLUS")
        assertEquals("Ghost", move.type)
        assertEquals("OMINOUS_WIND_PLUS", move.id)
        assertFalse(move.special)
        assertEquals("", RaidMetadata(emptyMap(), emptyMap()).move("UNKNOWN_MOVE").type)
    }
    @Test fun preservesExplicitMetadataAndOrdinaryNames() {
        assertEquals("Ghost", RaidMetadata(emptyMap(), mapOf("OMINOUS_WIND_PLUS" to "Ghost")).move("OMINOUS_WIND_PLUS").type)
        assertEquals("Vento Ominoso", raidMoveLabel("OMINOUS_WIND", mapOf(raidKey("Ominous Wind") to "Vento Ominoso")))
    }
}
