package com.mewname.app

import com.mewname.app.model.NamingField
import org.junit.Assert.*
import org.junit.Test

class ReviewLogSelectionTest {
    @Test fun `picker exposes every export field exactly once independent of presets`() {
        val fields = reviewLogFieldGroups.flatten()
        assertEquals(NamingField.entries.toSet(), fields.toSet())
        assertEquals(fields.size, fields.distinct().size)
    }
    @Test fun `selecting gender keeps unrelated fields and extras excluded`() {
        val request = ReviewLogRequest(setOf(NamingField.GENDER))
        assertEquals(listOf(NamingField.GENDER), ReviewFieldDiagnostics.fields(request.fields))
        assertFalse(request.includeOcr)
        assertFalse(request.includeNames)
    }
}