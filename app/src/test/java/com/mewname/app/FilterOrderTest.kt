package com.mewname.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterOrderTest {
    @Test fun preservesClickOrderAcrossPokemonSections() {
        val groups = linkedMapOf(
            "type:Fire" to listOf(FilterExpression("fire")),
            "token:shiny" to listOf(FilterExpression("shiny")),
            "move:Surf" to listOf(FilterExpression("@Surf")),
            "manual:cp300" to listOf(FilterExpression("cp300")),
            "name:Pikachu" to listOf(FilterExpression("Pikachu"))
        )
        assertEquals("shiny&@Surf&fire&Pikachu&cp300", buildOrderedFilter(
            listOf("token:shiny", "move:Surf", "type:Fire", "name:Pikachu", "manual:cp300"), groups))
    }

    @Test fun peopleChangesKeepPositionAndChosenOperator() {
        val order = listOf("token:lucky", "token:interactable")
        val groups = linkedMapOf("token:interactable" to listOf(FilterExpression("!interactable", ",")),
            "token:lucky" to listOf(FilterExpression("lucky")))
        assertEquals(order, reconcileFilterOrder(order, groups.keys))
        assertEquals("lucky,!interactable", buildOrderedFilter(order, groups))
        assertEquals("lucky&!interactable", buildOrderedFilter(order,
            groups + ("token:interactable" to listOf(FilterExpression("!interactable", "&")))))
    }

    @Test fun removedAndReaddedSelectionGoesToEnd() {
        val removed = reconcileFilterOrder(listOf("a", "b", "c"), setOf("b", "c"))
        assertEquals(listOf("b", "c", "a"), reconcileFilterOrder(removed, linkedSetOf("a", "b", "c")))
        assertEquals(emptyList<String>(), reconcileFilterOrder(removed, emptySet()))
    }

    @Test fun keepsCompoundTokensTogetherAndConsecutiveNamesAsAlternatives() {
        val groups = linkedMapOf(
            "name:Ana" to listOf(FilterExpression("Ana")),
            "name:Beto" to listOf(FilterExpression("Beto")),
            "token:levels" to listOf(FilterExpression("friendlevel3"), FilterExpression("friendlevel4", ","))
        )
        assertEquals("friendlevel3,friendlevel4&Ana,Beto", buildOrderedFilter(
            listOf("token:levels", "name:Ana", "name:Beto"), groups))
    }
}
