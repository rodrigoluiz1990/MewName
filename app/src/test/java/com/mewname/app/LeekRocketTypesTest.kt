package com.mewname.app

import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class LeekRocketTypesTest {
    private fun lineup(first: String) = """<div class="rocket-profile">
        <div class="employee-info"><span class="name">Arlo</span><span class="title">Leader</span></div>
        <div class="lineup-info">
        <div class="slot"><span class="number">1</span>$first</div>
        <div class="slot"><span class="number">2</span><span class="shadow-pokemon" data-pokemon="Steelix" data-type1="steel" data-type2="Ground"></span></div>
        <div class="slot"><span class="number">3</span><span class="shadow-pokemon" data-pokemon="Alakazam" data-type1="psychic" data-type2="None"></span></div>
        </div></div>"""
    @Test fun arloKeepsRegionalTypingFromSource() {
        val entry = LeekDuckParser.parse(LeekSection.ROCKET, lineup("""<span class="shadow-pokemon" data-pokemon="Alolan Grimer" data-type1="poison" data-type2="Dark"></span>"""), 1).entries.single()
        assertEquals(listOf("Poison", "Dark"), entry.slots[0].pokemon.single().types)
        assertEquals(listOf("Steel", "Ground"), entry.slots[1].pokemon.single().types)
        assertEquals(listOf("Psychic"), entry.slots[2].pokemon.single().types)
    }
    @Test fun absentOrInvalidTypesRemainUnknown() {
        val entry = LeekDuckParser.parse(LeekSection.ROCKET, lineup("""<span class="shadow-pokemon" data-pokemon="Unknown" data-type1="invalid" data-type2="None"></span>"""), 1).entries.single()
        assertTrue(entry.slots[0].pokemon.single().types.isEmpty())
    }
}
