package com.mewname.app

import com.mewname.app.domain.AppLanguage
import com.mewname.app.domain.RaidHistoryCategory
import com.mewname.app.domain.RaidHistoryItem
import com.mewname.app.domain.RaidMetadata
import com.mewname.app.domain.RaidPokemon
import org.junit.Assert.assertEquals
import org.junit.Test

class RaidCategoryTitleTest {
    private fun category(id: String) = RaidHistoryCategory(id, id, id, id, emptyList())

    @Test
    fun currentRaidCategoriesAreLocalized() {
        val cases = listOf(
            "live_RAID_LEVEL_1" to listOf("Raid 1", "Raid 1", "Raid 1"),
            "live_RAID_LEVEL_1_MAX" to listOf("Raid 1 · Dinamax", "Raid 1 · Dynamax", "Raid 1 · Dinamax"),
            "live_RAID_LEVEL_3_MAX" to listOf("Raid 3 · Dinamax", "Raid 3 · Dynamax", "Raid 3 · Dinamax"),
            "live_RAID_LEVEL_5_MAX" to listOf("Raid 5 · Dinamax", "Raid 5 · Dynamax", "Raid 5 · Dinamax"),
            "dynamax" to listOf("Raid · Dinamax", "Raid · Dynamax", "Raid · Dinamax"),
            "live_RAID_LEVEL_3" to listOf("Raid 3", "Raid 3", "Raid 3"),
            "live_RAID_LEVEL_4_5" to listOf("Raid 4.5", "Raid 4.5", "Raid 4.5"),
            "live_RAID_LEVEL_MEGA" to listOf("Raid \u00B7 Mega", "Raid \u00B7 Mega", "Raid \u00B7 Mega"),
            "live_RAID_LEVEL_ULTRA_BEAST" to listOf("Raid \u00B7 Ultra Criaturas", "Raid \u00B7 Ultra Beasts", "Raid \u00B7 Ultraentes"),
            "live_RAID_LEVEL_5_SHADOW" to listOf("Raid 5 \u00B7 Sombroso", "Raid 5 \u00B7 Shadow", "Raid 5 \u00B7 Oscura"),
            "live_RAID_LEVEL_4_MEGA_ENHANCED" to listOf("Raid \u00B7 Super Mega", "Raid \u00B7 Super Mega", "Raid \u00B7 Super Mega"),
            "live_RAID_LEVEL_ELITE" to listOf("Raid \u00B7 Elite", "Raid \u00B7 Elite", "Raid \u00B7 \u00C9lite"),
            "live_RAID_LEVEL_4_EVENTS" to listOf("Raid 4 \u00B7 Eventos", "Raid 4 \u00B7 Events", "Raid 4 \u00B7 Eventos")
        )
        val languages = listOf(AppLanguage.PT_BR, AppLanguage.EN, AppLanguage.ES)
        cases.forEach { (id, expected) ->
            languages.forEachIndexed { index, language ->
                assertEquals("$id / $language", expected[index], raidCategoryTabTitle(category(id), language))
            }
        }
    }

    @Test
    fun savedRaidCategoriesUseTheSameCompactVocabulary() {
        assertEquals("Raid 5", raidCategoryTabTitle(category("raids5"), AppLanguage.PT_BR))
        assertEquals("Raid 5 \u00B7 Sombroso", raidCategoryTabTitle(category("shadow"), AppLanguage.PT_BR))
        assertEquals("Raid \u00B7 Mega", raidCategoryTabTitle(category("mega"), AppLanguage.PT_BR))
        assertEquals("Raid \u00B7 Super Mega", raidCategoryTabTitle(category("megaSuper"), AppLanguage.PT_BR))
        assertEquals("Raid \u00B7 Gigamax", raidCategoryTabTitle(category("gigantamax"), AppLanguage.ES))
        assertEquals("Raid · Dynamax", raidCategoryTabTitle(category("dynamax"), AppLanguage.EN))
    }

    @Test
    fun maxCategoriesStayInsideCurrentAndSavedGroups() {
        assertEquals(RaidCategoryGroup.CURRENT, raidCategoryGroup(category("live_RAID_LEVEL_5")))
        assertEquals(RaidCategoryGroup.CURRENT, raidCategoryGroup(category("live_RAID_LEVEL_5_MAX")))
        assertEquals(RaidCategoryGroup.SAVED, raidCategoryGroup(category("raids5")))
        assertEquals(RaidCategoryGroup.SAVED, raidCategoryGroup(category("dynamax")))
        assertEquals(RaidCategoryGroup.SAVED, raidCategoryGroup(category("gigantamax")))
        assertEquals(true, isMaxRaidCategory(category("live_RAID_LEVEL_5_MAX")))
        assertEquals(true, isMaxRaidCategory(category("gigantamax")))
    }

    @Test
    fun raidEntriesAreSortedByNationalDex() {
        fun pokemon(id: String, dex: Int) = RaidPokemon(id, emptyList(), 1, 1, 1, emptyList(), emptyList(), emptyList(), dex)
        val metadata = RaidMetadata(
            pokemon = mapOf(
                "ZAMAZENTA_HERO_FORM" to pokemon("ZAMAZENTA_HERO_FORM", 889),
                "PIKACHU" to pokemon("PIKACHU", 25),
                "SKELEDIRGE" to pokemon("SKELEDIRGE", 911)
            ),
            moves = emptyMap()
        )
        val unsorted = RaidHistoryCategory(
            "live_RAID_LEVEL_5", "", "", "",
            listOf(
                RaidHistoryItem("Skeledirge", "https://www.pokebattler.com/raids/SKELEDIRGE", "", ""),
                RaidHistoryItem("Zamazenta", "https://www.pokebattler.com/raids/ZAMAZENTA_HERO_FORM", "", ""),
                RaidHistoryItem("Pikachu", "https://www.pokebattler.com/raids/PIKACHU", "", "")
            )
        )
        assertEquals(
            listOf("Pikachu", "Zamazenta", "Skeledirge"),
            sortRaidCategoriesByDex(listOf(unsorted), metadata).single().items.map { it.name }
        )
    }
}
