package com.mewname.app

import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class MoveSortTest {
    @Test fun sortsByCombatStatsWithUnknownValuesLast() {
        val a = MoveCatalogEntry("Alpha", type = "Fire", power = 50, energyDelta = -100, duration = 2500, category = MoveCategory.CHARGED)
        val b = a.copy(name = "Beta", power = 150, energyDelta = -50, duration = 1500)
        val missing = a.copy(name = "Unknown", power = null, energyDelta = null, duration = null)
        val moves = listOf(missing, a, b)
        for (order in listOf(MoveSort.PVE, MoveSort.ENERGY, MoveSort.DURATION)) {
            assertEquals(listOf(b, a, missing), moves.sortedWith(moveComparator(order, AppLanguage.EN)))
        }
        assertEquals(listOf(a, b, missing), moves.sortedWith(moveComparator(MoveSort.NAME, AppLanguage.EN)))
    }
    @Test fun reversesEveryCriterionKeepingMissingStatsLast() {
        val a = MoveCatalogEntry("Alpha", type = "Fire", power = 10, pvpPower = 20, energyDelta = -25, duration = 500, category = MoveCategory.CHARGED)
        val b = a.copy(name = "Beta", type = "Water", power = 100, pvpPower = 80, energyDelta = -100, duration = 3000)
        val unknown = a.copy(name = "Unknown", power = null, pvpPower = null, energyDelta = null, duration = null)
        for (order in MoveSort.entries.filter { it != MoveSort.NATIONAL_DEX }) {
            val forward = listOf(a, b).sortedWith(moveComparator(order, AppLanguage.EN))
            assertEquals(order.name, forward.reversed(), listOf(a, b).sortedWith(moveComparator(order, AppLanguage.EN, true)))
            if (order !in listOf(MoveSort.NAME, MoveSort.TYPE)) {
                for (reverse in listOf(false, true)) assertEquals(unknown,
                    listOf(unknown, a, b).sortedWith(moveComparator(order, AppLanguage.EN, reverse)).last())
            }
        }
    }
    @Test fun nationalDexUsesSpeciesNumberAcrossFormsAndKeepsUnknownLast() {
        val numbers = mapOf("DIALGA" to 483, "MEWTWO" to 150, "KYUREM" to 646)
        assertEquals(483, nationalDexNumber("Origin Forme Dialga", numbers))
        assertEquals(150, nationalDexNumber("Mewtwo (Mega X)", numbers))
        val names = listOf("Unknown", "Black Kyurem", "Mewtwo (Mega X)", "Origin Forme Dialga")
        for (reverse in listOf(false, true)) {
            val sorted = names.sortedWith { a, b -> compareSortValues(nationalDexNumber(a, numbers), nationalDexNumber(b, numbers), reverse) }
            assertEquals("Unknown", sorted.last())
            assertEquals(if (reverse) "Black Kyurem" else "Mewtwo (Mega X)", sorted.first())
        }
    }
}
