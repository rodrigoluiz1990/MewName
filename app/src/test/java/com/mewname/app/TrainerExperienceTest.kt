package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.TrainerProfileParser
import com.mewname.app.domain.TrainerProfileLine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrainerExperienceTest {
    private val header = "Nome do treinador: Trainer42\nNível: 50\n"
    @Test fun readsTotalExperienceInDifferentLocalesAndBeyondIntegerRange() {
        for (value in listOf("Total de PE 2.241.261.735", "Total XP: 2,241,261,735", "Total Experience\n2241261735", "PE total 2 241 261 735")) {
            assertEquals(value,2241261735L,TrainerProfileParser.parse(header+value).totalExperience)
        }
    }
    @Test fun doesNotConfuseProgressCaptureRewardsCodesOrAmbiguousTotals() {
        for (value in listOf("138.658.735 / 9.500.000", "Total de PE 1.000 / 2.000", "Total de PE 12.34", "Total de PE 1.000\nTotal de PE 2.000", "Total de PE 999999999999999999999999")) {
            assertNull(value,TrainerProfileParser.parse(header+value).totalExperience)
        }
        assertNull(TrainerProfileParser.parse("Pokémon capturado\nTotal de PE 1000").totalExperience)
    }
    @Test fun readsValueOnSameVisualRowWhenOcrBlocksAreInterleaved() {
        val text = header+"Total de PE\nPokémon pegos\n12345\n241.261.735"
        val regions = listOf(TrainerProfileLine("Total de PE",.1f,.8f),
            TrainerProfileLine("241.261.735",.65f,.8f),TrainerProfileLine("12345",.65f,.7f))
        assertEquals(241261735L,TrainerProfileParser.parse(text,regions).totalExperience)
    }
    @Test fun missingExperienceDoesNotEraseSameTrainerButDifferentIdentityClearsIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("trainer_profile",0)
        prefs.edit().clear().commit()
        try {
            TrainerScreenReader.save(context,TrainerProfileParser.parse(header+"Total de PE 241.261.735"))
            assertEquals(241261735L,prefs.getLong("total_experience",-1))
            assertEquals("Trainer42",prefs.getString("experience_trainer",null))
            TrainerScreenReader.save(context,TrainerProfileParser.parse(header))
            assertEquals(241261735L,prefs.getLong("total_experience",-1))
            TrainerScreenReader.save(context,TrainerProfileParser.parse("Total de PE 999999"))
            assertEquals(241261735L,prefs.getLong("total_experience",-1))
            TrainerScreenReader.save(context,TrainerProfileParser.parse("Nome do treinador: Other123\nNível: 40"))
            assertFalse(prefs.contains("total_experience"))
        } finally { prefs.edit().clear().commit() }
    }
    @Test fun actualRodinOcrReadsPrefixedLabelAndSeparatedValue() {
        val json = org.json.JSONArray(javaClass.getResourceAsStream("/trainer/xp-rodin.json")!!.bufferedReader().use { it.readText() })
        val lines = (0 until json.length()).map { index ->
            val line = json.getJSONObject(index)
            val rect = line.getJSONArray("rect")
            TrainerProfileLine(line.getString("text"),rect.getInt(0)/1220f,rect.getInt(1)/2712f)
        }
        val result = TrainerProfileParser.parse(lines.joinToString("\n") { it.text }, lines)
        assertEquals("RodWhite001",result.name)
        assertEquals("73",result.level)
        assertEquals(243850350L,result.totalExperience)
    }    @Test fun actualRedmiOcrReadsScrolledTotalWithoutInventingIdentity() {
        val json = org.json.JSONArray(javaClass.getResourceAsStream("/trainer/xp-redmi.json")!!.bufferedReader().use { it.readText() })
        val lines = (0 until json.length()).map { index ->
            val line = json.getJSONObject(index)
            val rect = line.getJSONArray("rect")
            TrainerProfileLine(line.getString("text"),rect.getInt(0)/1080f,rect.getInt(1)/2400f)
        }
        val result = TrainerProfileParser.parse(lines.joinToString("\n") { it.text }, lines)
        assertEquals(236013402L,result.totalExperience)
        assertNull(result.name)
    }    @Test fun scrolledTotalRequiresRecentReadIdentityAndRejectsManualNameChanges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("trainer_profile",0)
        prefs.edit().clear().commit()
        val partial = com.mewname.app.domain.TrainerProfileDraft(totalExperience=236013402L)
        try {
            prefs.edit().putString("name","ManualOnly999").commit()
            assertFalse(TrainerScreenReader.save(context,partial))
            TrainerScreenReader.save(context,com.mewname.app.domain.TrainerProfileDraft(name="RodSarah",level="73"))
            assertTrue(TrainerScreenReader.save(context,partial))
            assertEquals(236013402L,prefs.getLong("total_experience",-1))
            assertEquals("RodSarah",prefs.getString("experience_trainer",null))
            prefs.edit().putString("name","AnotherManual123").commit()
            assertFalse(TrainerScreenReader.save(context,partial))
            prefs.edit().putString("name","RodSarah").commit()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(121))
            assertFalse(TrainerScreenReader.save(context,partial))
        } finally { prefs.edit().clear().commit() }
    }}