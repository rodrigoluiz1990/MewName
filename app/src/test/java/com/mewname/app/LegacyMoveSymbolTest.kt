package com.mewname.app

import com.mewname.app.model.NamingConfig
import com.mewname.app.model.defaultSymbols
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyMoveSymbolTest {
    @Test fun usesCustomSymbolInsteadOfFixedStar() {
        assertEquals("LEG", legacyMoveSymbol(listOf(NamingConfig(symbols=mapOf("LEGACY" to "LEG")))))
    }
    @Test fun matchesNamingReviewPrecedence() {
        assertEquals("L2", legacyMoveSymbol(listOf(
            NamingConfig(symbols=mapOf("LEGACY" to " ")),
            NamingConfig(symbols=mapOf("LEGACY" to "L2")),
            NamingConfig(symbols=mapOf("LEGACY" to "L3")))))
    }
    @Test fun fallsBackToNamingDefault() {
        assertEquals(defaultSymbols()["LEGACY"], legacyMoveSymbol(emptyList()))
        assertEquals(defaultSymbols()["LEGACY"], legacyMoveSymbol(listOf(NamingConfig(symbols=emptyMap()))))
    }
}