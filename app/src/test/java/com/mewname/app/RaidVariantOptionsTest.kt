package com.mewname.app

import com.mewname.app.domain.RaidChoice
import org.junit.Assert.*
import org.junit.Test

class RaidVariantOptionsTest {
    @Test fun duplicateEntriesAreRemovedButShadowRemainsIdentifiable() {
        val normal = RaidChoice("PIKACHU", "RAID_LEVEL_1")
        val shadow = RaidChoice("PIKACHU_SHADOW", "RAID_LEVEL_1")
        val options = raidVariantOptions(listOf(normal, normal, shadow), { "Raid 1" },
            { if (it == "PIKACHU") "Pikachu" else "Pikachu (Sombroso)" })
        assertEquals(listOf(normal, shadow), options.map { it.choice })
        assertEquals(2, options.map { it.label }.distinct().size)
        assertTrue(options[1].label.contains("Sombroso"))
    }

    @Test fun differentTiersAlsoIdentifyThePokemon() {
        val choices = listOf(RaidChoice("CHARIZARD", "RAID_LEVEL_3"), RaidChoice("CHARIZARD_MEGA_X", "RAID_LEVEL_MEGA"))
        val options = raidVariantOptions(choices, { it.tier }, { it })
        assertTrue(options.zip(choices).all { (option, choice) -> option.label.contains(choice.id) && option.label.contains(choice.tier) })
    }
}
