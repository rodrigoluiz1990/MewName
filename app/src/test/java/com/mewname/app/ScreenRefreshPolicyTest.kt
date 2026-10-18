package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ScreenRefreshPolicyTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun save(name: String, data: String, time: Long) {
        val file = File(context.filesDir, "pokedex-online-v1/$name")
        file.parentFile!!.mkdirs()
        file.writeText(JSONObject().put("data", data).put("fetchedAt", time).toString())
    }
    @Test fun refreshBoundaryMissingDatesAndClockSkew() {
        val now = 100_000_000L
        assertFalse(screenCacheExpired(now - RAID_CATALOG_MAX_AGE + 1, RAID_CATALOG_MAX_AGE, now))
        assertTrue(screenCacheExpired(now - RAID_CATALOG_MAX_AGE, RAID_CATALOG_MAX_AGE, now))
        assertTrue(screenCacheExpired(null, RAID_CATALOG_MAX_AGE, now))
        assertTrue(screenCacheExpired(0, RAID_CATALOG_MAX_AGE, now))
        assertTrue(screenCacheExpired(now + 1, RAID_CATALOG_MAX_AGE, now))
    }
    @Test fun expiredPvpCacheIsAvailableWithoutNetworkOrModification() {
        val data = """[{"speciesId":"pikachu","moveset":["THUNDER_SHOCK"]}]"""
        save("rankings-1500.json", data, 1L)
        val result = PokedexDetailsRepository(context).cachedOnline("rankings-1500.json")!!
        assertEquals(data, result.json)
        assertEquals(1L, result.fetchedAt)
        assertFalse(result.stale) // Expiration alone is not a failed refresh.
        assertEquals(result, PokedexDetailsRepository(context).cachedOnline("rankings-1500.json"))
    }
    @Test fun freshPvpCacheIsReusedByOnlineLookup() {
        val now = System.currentTimeMillis()
        save("rankings-2500.json", "[{\"speciesId\":\"pikachu\"}]", now)
        val repo = PokedexDetailsRepository(context)
        assertEquals(repo.cachedOnline("rankings-2500.json"), repo.online("rankings-2500.json"))
    }
    @Test fun invalidCacheDoesNotPreventLoading() {
        save("rankings-10000.json", "invalid", 1L)
        assertNull(PokedexDetailsRepository(context).cachedOnline("rankings-10000.json"))
        save("gamemaster.json", "{\"pokemon\":[]}", 1L)
        assertNull(PokedexDetailsRepository(context).cachedOnline("gamemaster.json"))
    }
    @Test fun raidCatalogWithoutTimestampRefreshesAndFreshCatalogDoesNot() {
        val file = File(context.filesDir, "raid-counters-v1/catalog.json")
        file.parentFile!!.mkdirs()
        file.writeText("{}")
        val repo = RaidCounterRepository(context)
        assertTrue(repo.catalogNeedsRefresh())
        file.writeText(JSONObject().put("mewnameFetchedAt", System.currentTimeMillis()).toString())
        assertFalse(repo.catalogNeedsRefresh())
        file.writeText(JSONObject().put("mewnameFetchedAt", System.currentTimeMillis() - RAID_CATALOG_MAX_AGE).toString())
        assertTrue(repo.catalogNeedsRefresh())
    }
}
