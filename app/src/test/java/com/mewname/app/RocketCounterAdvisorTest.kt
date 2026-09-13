package com.mewname.app

import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class RocketCounterAdvisorTest {
    @Test fun usesActualDualTypeWeaknessesInsteadOfGruntTitle() {
        val entry = LeekEntry("test", "Water-type Grunt", "Grunts", slots = listOf(
            LeekSlot(1, true, listOf(LeekPokemon("Wooper", "", detail = "Grass (2×)"))),
            LeekSlot(2, false, listOf(LeekPokemon("Quagsire", "", detail = "Grass (2×)")))
        ))
        val counters = RocketCounterAdvisor.suggest(entry)
        assertEquals(listOf("Grass"), counters.map { it.attackType })
        assertEquals("@1Planta,@2Planta,@3Planta", RocketCounterAdvisor.filter(counters) { "Planta" })
        assertFalse(counters.any { it.attackType == "Electric" })
    }
    @Test fun noWeaknessEvidenceDoesNotInventCountersOrQuery() {
        val counters = RocketCounterAdvisor.suggest(LeekEntry("test", "Fire-type Grunt", "Grunts"))
        assertTrue(counters.isEmpty())
        assertEquals("", RocketCounterAdvisor.filter(counters) { it })
    }
    @Test fun alternativesInOneSlotDoNotOutweighCoverageAcrossSlots() {
        val entry = LeekEntry("test", "Grunt", "Grunts", slots = listOf(
            LeekSlot(1, true, (1..6).map { LeekPokemon("$it", "", detail = "Fire") }),
            LeekSlot(2, false, listOf(LeekPokemon("a", "", detail = "Water"))),
            LeekSlot(3, false, listOf(LeekPokemon("b", "", detail = "Water")))
        ))
        assertEquals("Water", RocketCounterAdvisor.suggest(entry).first().attackType)
    }
}