package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.GameTextFileReader
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameTextFileReaderTest {
    @Test fun streamingParserPreservesEveryBundledTranslation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legacy = Regex("""RESOURCE ID:\s*([^\s]+)\s*TEXT:\s*(.*?)(?=\s*RESOURCE ID:|\z)""", RegexOption.DOT_MATCHES_ALL)
        for (language in listOf("en", "ptbr", "es")) {
            val raw = context.assets.open("catalogs/text_$language.txt").bufferedReader().use { it.readText() }
            val expected = legacy.findAll(raw).mapNotNull { match ->
                val text = match.groupValues[2].replace(Regex("\\s+"), " ").trim()
                if (text.isEmpty()) null else match.groupValues[1].trim() to text
            }.toMap()
            val actual = GameTextFileReader.read(raw.reader().buffered())
            // Legacy regex skipped IDs containing spaces; retain every previously readable value.
            val differences = expected.keys.filter { expected[it] != actual[it] }
            assertEquals(language + differences.take(8).joinToString { "$it: expected=${expected[it]?.take(160)} actual=${actual[it]?.take(160)}" }, emptyList<String>(), differences)
        }
    }
    @Test fun multilineTextAndFinalRecordArePreserved() {
        val raw = "\uFEFFRESOURCE ID: first\r\nTEXT: Pokémon\r\ncom várias linhas\r\n\r\nRESOURCE ID: last\r\nTEXT: Chocar {0} Ovos."
        assertEquals(mapOf("first" to "Pokémon com várias linhas", "last" to "Chocar {0} Ovos."),
            GameTextFileReader.read(raw.reader().buffered()))
    }
    @Test fun identifiersWithSpacesAreAlsoReadable() {
        assertEquals(mapOf("autumn event" to "Autumn Event"), GameTextFileReader.read(
            "RESOURCE ID: autumn event\nTEXT: Autumn Event".reader().buffered()))
    }
}