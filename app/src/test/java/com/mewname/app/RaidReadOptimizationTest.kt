package com.mewname.app

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class RaidReadOptimizationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun clearAndNoisyVictreebelNamesKeepMegaIdentity() {
        // Transcribed from the observed screen, plus synthetic OCR variations.
        val meta = RaidCounterRepository(context).metadata()
        val choice = RaidChoice("VICTREEBEL_MEGA", "RAID_LEVEL_MEGA")
        for (name in listOf("Mega Victreebel", "MegaVictreebel", "Mega VictreebeI", "Mega Victree bel")) {
            val text = "Placa municipal Itaipulandia\nPC37188\n$name\nBATALHA usando um Passe de Reide\nGrupo privado"
            val lines = listOf(BattleOcrLine(name, .1f, .29f, .9f, .37f))
            val advice = BattleAdvisor.adviceForRaw(context, text, lines, 4, identityOnly = true)!!
            assertEquals(name, "Victreebel", advice.bossName)
            assertTrue(advice.suggestions.isEmpty())
            assertTrue(advice.defenderSuggestions.isEmpty())
            assertTrue(advice.healerSuggestions.isEmpty())
            assertEquals(text, advice.raidScreenText)
            assertEquals(choice, RaidBossResolver.resolve(advice.bossName!!, text, meta, listOf(choice), 4).selected)
        }
    }

    @Test fun indexedMetadataPreservesExactAndAmbiguousMatches() {
        val meta = RaidCounterRepository(context).metadata()
        val keys = meta.pokemon.values.map { it to raidKey(it.id) }
        for (pokemon in meta.pokemon.values) {
            assertEquals(pokemon, meta.resolve(pokemon.id))
            val name = raidName(pokemon.id)
            val key = raidKey(name)
            val words = key.split(' ').sorted()
            val previous = keys.filter { (_, candidate) ->
                candidate == key || candidate.split(' ').sorted() == words
            }.singleOrNull()?.first
            if (previous != null) assertEquals(name, previous, meta.resolve(name))
        }
        assertNull(meta.resolve("Missing Pokemon"))
    }

    @Test fun savedIndexDetectsAdditionDeletionAndCorruptReportsAcrossInstances() {
        val root = java.nio.file.Files.createTempDirectory("raid-index").toFile()
        val wrapped = object : ContextWrapper(context) { override fun getFilesDir() = root }
        val directory = File(root, "raid-counters-v1").apply { mkdirs() }
        val report = RaidReport("VICTREEBEL_MEGA", "RAID_LEVEL_MEGA", 40, 1L, emptyList(), emptyList(),
            listOf(RaidCounter("MEWTWO", RaidMove("CONFUSION_FAST", "Psychic"), RaidMove("PSYCHIC", "Psychic"), 1.2)))
        val file = File(directory, "VICTREEBEL_MEGA_RAID_LEVEL_MEGA_40.json")
        val repository = RaidCounterRepository(wrapped)
        assertTrue(repository.cachedChoices().isEmpty())
        file.writeText(RaidJson.encode(report).toString())
        val expected = listOf(RaidChoice(report.bossId, report.tier))
        assertEquals(expected, repository.cachedChoices())
        assertEquals(expected, RaidCounterRepository(wrapped).cachedChoices())
        file.writeText("broken")
        assertTrue(repository.cachedChoices().isEmpty())
        file.writeText(RaidJson.encode(report).toString())
        assertEquals(expected, repository.cachedChoices())
        assertTrue(file.delete())
        assertTrue(repository.cachedChoices().isEmpty())
    }

    @Test fun lightweightModePreservesMaxRecommendations() {
        val advice = BattleAdvisor.adviceForRaw(context, "Cinderace Gigamax\nBATALHA MAX\n800 PM", identityOnly = true)!!
        assertEquals(BattleMode.MAX, advice.mode)
        assertFalse(advice.suggestions.isEmpty())
    }
}
