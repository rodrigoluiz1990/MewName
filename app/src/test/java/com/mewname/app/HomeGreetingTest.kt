package com.mewname.app

import com.mewname.app.domain.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGreetingTest {
    @Test fun changesAtMorningAfternoonAndNightBoundaries() {
        val language = AppLanguage.PT_BR
        assertEquals("Boa noite", greetingForHour(0, language))
        assertEquals("Boa noite", greetingForHour(4, language))
        assertEquals("Bom dia", greetingForHour(5, language))
        assertEquals("Bom dia", greetingForHour(11, language))
        assertEquals("Boa tarde", greetingForHour(12, language))
        assertEquals("Boa tarde", greetingForHour(17, language))
        assertEquals("Boa noite", greetingForHour(18, language))
        assertEquals("Boa noite", greetingForHour(23, language))
    }
    @Test fun usesSelectedLanguage() {
        assertEquals("Good morning", greetingForHour(8, AppLanguage.EN))
        assertEquals("Buenas tardes", greetingForHour(14, AppLanguage.ES))
    }
}