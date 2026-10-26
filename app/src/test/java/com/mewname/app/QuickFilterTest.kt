package com.mewname.app
import org.junit.Assert.*
import org.junit.Test
class QuickFilterTest {
    @Test fun savedExpressionsRoundTripWithAllSupportedOperators() {
        for (value in listOf("!favorito&!lendário&!mítico,traje", "@1planta;@2fogo&pc1500-2500", "#troca", "+Pikachu,25", ""))
            assertEquals(value, buildQuickFilter(parseQuickFilter(value)))
    }
    @Test fun cyclesNormalNegatedDisabledNormalWithoutDroppingTerms() {
        val normal = QuickFilterTerm("traje")
        assertEquals("!traje", buildQuickFilter(listOf(normal.cycle())))
        assertEquals("", buildQuickFilter(listOf(normal.cycle().cycle())))
        assertEquals(normal, normal.cycle().cycle().cycle())
    }
    @Test fun disablingFirstAndMiddleLeavesNoDanglingOperator() {
        val terms = parseQuickFilter("favorito&lendário,mítico")
        assertEquals("mítico", buildQuickFilter(terms.mapIndexed { i, term -> if (i < 2) term.copy(mode = 2) else term }))
        assertEquals("favorito,mítico", buildQuickFilter(terms.mapIndexed { i, term -> if (i == 1) term.copy(mode = 2) else term }))
        assertEquals("", buildQuickFilter(terms.map { it.copy(mode = 2) }))
    }
}
