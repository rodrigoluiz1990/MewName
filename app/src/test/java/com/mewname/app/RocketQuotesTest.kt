package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RocketQuotesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun everyCurrentRocketQuoteHasPortugueseAndSpanishGameText() {
        val html = javaClass.getResourceAsStream("/leekduck/rocket-lineups.html")!!.bufferedReader().use { it.readText() }
        val entries = LeekDuckParser.parse(LeekSection.ROCKET, html, 0).entries
        for (language in listOf(AppLanguage.PT_BR, AppLanguage.ES, AppLanguage.EN)) {
            val quotes = GameTextRepository.rocketQuotes(context, language)
            for (entry in entries) assertNotNull("$language: ${entry.title}: ${entry.description}",
                translatedRocketQuote(entry.description, entry.title, quotes))
        }
    }
    @Test fun languageSwitchAndPunctuationUseGameTranslations() {
        val pt = GameTextRepository.rocketQuotes(context, AppLanguage.PT_BR)
        assertEquals("Normal e fraco são duas coisas bem diferentes.",
            translatedRocketQuote("Normal does not mean weak.", "Normal-type Male Grunt", pt))
        val es = GameTextRepository.rocketQuotes(context, AppLanguage.ES)
        assertNotEquals(translatedRocketQuote("Normal does not mean weak.", "Normal-type Male Grunt", pt),
            translatedRocketQuote("Normal does not mean weak.", "Normal-type Male Grunt", es))
        assertNotNull(translatedRocketQuote("Normal does not mean weak!", "Normal-type Female Grunt", pt))
        assertNull(translatedRocketQuote("Future unknown quote", "Grunt", pt))
    }
}
