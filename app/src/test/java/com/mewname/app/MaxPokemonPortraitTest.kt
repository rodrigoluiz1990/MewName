package com.mewname.app

import com.mewname.app.domain.RaidHistoryItem
import com.mewname.app.domain.GameInfoRepository
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MaxPokemonPortraitTest {
    @Test fun preservesMaxAndCrownedForms() {
        assertEquals("CHARIZARD", maxPortraitId("Dynamax Charizard"))
        assertEquals("CHARIZARD", maxPortraitId("Dinamax Charizard"))
        assertEquals("CHARIZARD_GIGANTAMAX", maxPortraitId("Gigantamax Charizard"))
        assertEquals("CHARIZARD_GIGANTAMAX", maxPortraitId("CHARIZARD_GIGANTAMAX"))
        assertEquals("ZACIAN_CROWNED_SWORD_FORM", maxPortraitId("Zacian Crowned Sword"))
        assertEquals("ZAMAZENTA_CROWNED_SHIELD_FORM", maxPortraitId("Zamazenta Crowned Shield"))
        assertEquals("HO_OH", maxPortraitId("Ho-Oh"))
    }

    @Test fun readsSpeciesBeforeTheTierInHistoryUrls() {
        val dynamax = RaidHistoryItem("Registeel", "https://www.pokebattler.com/raids/REGISTEEL/RAID_LEVEL_5_MAX", "", "")
        val gigantamax = RaidHistoryItem("Charizard", "https://www.pokebattler.com/raids/CHARIZARD_GIGANTAMAX", "", "")
        assertEquals("REGISTEEL", raidHistorySpriteId(dynamax))
        assertEquals("CHARIZARD_GIGANTAMAX", raidHistorySpriteId(gigantamax))
    }

    @Test fun everySuggestedMaxPokemonHasABundledPng() {
        GameInfoRepository.loadMaxBattleRoster().forEach { entry ->
            val file = File("src/main/assets/raids/max/${maxPortraitId(entry.name)}.png")
            assertTrue("Missing sprite for ${entry.name}", file.isFile)
            assertArrayEquals("Invalid PNG for ${entry.name}", byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), file.inputStream().use { input -> ByteArray(8).also { input.read(it) } })
        }
    }
}
