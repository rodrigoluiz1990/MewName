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
    @Test fun sequentialSelectionAndDragMatchVisibleBlocksAndSavedText() {
        var order = emptyList<String>()
        val groups = mutableMapOf<String, List<FilterExpression>>()
        listOf("token:shiny", "type:Fire", "token:shadow").forEach { key ->
            groups[key] = listOf(FilterExpression(key.substringAfter(":")))
            // Simulate a state map whose iteration differs from the click order.
            order = reconcileFilterOrder(order, groups.keys.sorted().toSet())
        }
        assertEquals(listOf("token:shiny", "type:Fire", "token:shadow"), order)
        order = listOf("token:shadow", "token:shiny", "type:Fire")
        groups["token:shiny"] = listOf(FilterExpression("!shiny", ","))
        assertEquals(order, reconcileFilterOrder(order, groups.keys))
        val blocks = order.mapIndexed { index, key ->
            filterGroupPreview(key, groups.getValue(key), order.getOrNull(index - 1))
        }
        val savedValue = buildOrderedFilter(order, groups)
        assertEquals("shadow,!shiny&Fire", savedValue)
        assertEquals(savedValue, blocks.joinToString(""))
        assertEquals(savedValue, buildQuickFilter(parseQuickFilter(savedValue)))
    }
    @Test fun savedFilterRestoresConditionsOrderAndUnknownTerms() {
        val original = "!favorite,shiny&25;!favorite&@Surf"
        val restored = restoreFilterTerms(original, mapOf("token:shiny" to "shiny", "token:favorite" to "favorite"))
        assertEquals(listOf("token:favorite", "token:shiny", "imported:2", "imported:3", "imported:4"), restored.keys.toList())
        assertEquals(original, buildOrderedFilter(restored.keys.toList(), restored.mapValues { listOf(it.value) }))
        assertEquals(emptyMap<String, FilterExpression>(), restoreFilterTerms("", emptyMap()))
    }
}
