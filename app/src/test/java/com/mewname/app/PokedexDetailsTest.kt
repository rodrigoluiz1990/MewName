package com.mewname.app

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PokedexDetailsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun regionalFormNeverMatchesOrdinaryOrShadowRank() {
        val array = JSONArray("""[
            {"dex":59,"speciesId":"arcanine","speciesName":"Arcanine"},
            {"dex":59,"speciesId":"arcanine_hisuian","speciesName":"Arcanine (Hisuian)"},
            {"dex":59,"speciesId":"arcanine_shadow","speciesName":"Arcanine (Shadow)"}
        ]""")
        assertEquals("arcanine_hisuian", matchPvpokeSpecies(array, PokedexCatalogEntry(59,"Arcanine (Hisui)"))?.getString("speciesId"))
        assertEquals("arcanine", matchPvpokeSpecies(array, PokedexCatalogEntry(59,"Arcanine"))?.getString("speciesId"))
        assertNull(matchPvpokeSpecies(array, PokedexCatalogEntry(59,"Arcanine (Galar)")))
        assertNull(matchPvpokeSpecies(array, PokedexCatalogEntry(58,"Arcanine")))
    }
    @Test fun rankingMatchesExactIdAndPreservesMissingAndTimestamp() {
        val snap = PokedexOnlineSnapshot("""[{"speciesId":"arcanine_shadow","score":90},{"speciesId":"arcanine","score":71,"moveset":["FIRE_FANG","WILD_CHARGE"]}]""",123L,true)
        val rank = parsePokedexLeague(1500,"arcanine",snap)
        assertEquals(2,rank.rank); assertEquals(71.0,rank.score)
        assertEquals(listOf("FIRE_FANG","WILD_CHARGE"),rank.moves)
        assertTrue(rank.snapshot.stale)
        assertNull(parsePokedexLeague(1500,"arcanine_hisuian",snap).rank)
    }
    @Test fun localHisuiStatsTypesAndCpStaySeparate() {
        val entry = GameInfoRepository.loadPokedexCatalog(context).first { it.name == "Arcanine (Hisui)" }
        val result = PokedexDetailsRepository(context).local(entry, AppLanguage.PT_BR)
        assertEquals(listOf(232,165,216),result.stats)
        assertEquals(setOf("Fire","Rock"),result.types.toSet())
        assertTrue(result.matchups.any { it.attackType == "Water" && it.multiplier > 2.0 })
        assertEquals(PvpRankCalculator().estimateCpAtLevel(context,entry.name,15,15,15,50.0),result.cp[50]?.second)
        assertNotEquals(PvpRankCalculator().estimateCpAtLevel(context,"Arcanine",15,15,15,50.0),result.cp[50]?.second)
    }
    @Test fun freshCacheIsSharedWithoutNetworkAndInvalidPathsRejected() {
        val root = java.nio.file.Files.createTempDirectory("pokedex-cache").toFile()
        val wrapped = object : ContextWrapper(context) { override fun getFilesDir() = root }
        val dir = File(root,"pokedex-online-v1").apply { mkdirs() }
        val timestamp = System.currentTimeMillis()
        File(dir,"rankings-1500.json").writeText(JSONObject().put("fetchedAt",timestamp).put("data","[]").toString())
        val cached = PokedexDetailsRepository(wrapped).online("rankings-1500.json")
        assertEquals(timestamp,cached.fetchedAt); assertFalse(cached.stale)
        assertThrows(IllegalArgumentException::class.java) { PokedexDetailsRepository(wrapped).online("../other.json") }
    }
}
