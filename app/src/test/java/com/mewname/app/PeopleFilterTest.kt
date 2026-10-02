package com.mewname.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PeopleFilterTest {
    @Test fun usesSelectedOperatorInsteadOfAlwaysComma() {
        val first = FilterExpression("lucky")
        assertEquals("lucky&interactable", buildPeopleFilter("", listOf(first, FilterExpression("interactable", "&"))))
        assertEquals("lucky,interactable", buildPeopleFilter("", listOf(first, FilterExpression("interactable", ","))))
    }

    @Test fun preservesMixedOperatorsAndExclusions() {
        assertEquals("lucky,!interactable&friendlevel4", buildPeopleFilter("", listOf(
            FilterExpression("lucky"), FilterExpression("!interactable", ","), FilterExpression("friendlevel4", "&")
        )))
    }

    @Test fun preservesNicknameAlternativesAndConnectsFiltersWithTheirOperator() {
        assertEquals("Ana,Beto&lucky,!interactable", buildPeopleFilter(" Ana\n\nBeto\nAna ", listOf(
            FilterExpression("lucky", "&"), FilterExpression("!interactable", ",")
        )))
        assertEquals("Ana,lucky", buildPeopleFilter("Ana", listOf(FilterExpression("lucky", ","))))
    }

    @Test fun doesNotAddLeadingOperatorOrDuplicateTerms() {
        assertEquals("", buildPeopleFilter("", emptyList()))
        assertEquals("lucky", buildPeopleFilter("", listOf(FilterExpression("lucky", ","))))
        assertEquals("Ana,Beto", buildPeopleFilter("Ana\nAna\nBeto", emptyList()))
    }
}
