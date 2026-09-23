package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MaxBossDetailsTest {
    private fun metadata() = RaidCounterRepository(ApplicationProvider.getApplicationContext<Context>()).metadata()

    @Test fun articunoCaptureAndDoubleWeaknessUseActualStats() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val boss = maxBossPokemon(metadata(), "Articuno")!!
        assertEquals(1665, raidCp(boss, 10, false))
        assertEquals(1743, raidCp(boss, 15, false))
        val rock = GameInfoRepository.matchupAgainst(context, boss.types).single { it.attackType == "Rock" }
        assertEquals(2.56, rock.multiplier, 0.001)
        assertFalse(maxCatalogMoves(boss, false).contains("RETURN"))
        assertFalse(maxCatalogMoves(boss, false).contains("HURRICANE"))
        assertTrue(maxCatalogMoves(boss, false).contains("ICE_BEAM"))
    }

    @Test fun gigantamaxKeepsItsIdentityButUsesBaseCaptureStats() {
        val metadata = metadata()
        val boss = maxBossPokemon(metadata, "Gigantamax Charizard")!!
        assertEquals("CHARIZARD_GIGANTAMAX", boss.id)
        val capture = metadata.resolve(boss.id.removeSuffix("_GIGANTAMAX"))!!
        assertEquals(1651, raidCp(capture, 15, false))
        assertEquals("https://www.pokebattler.com/max/DYNAMAX_ARTICUNO", maxBossUrl("ARTICUNO"))
        assertEquals("https://www.pokebattler.com/max/GIGANTAMAX_CHARIZARD", maxBossUrl(boss.id))
        assertNull(maxBossPokemon(metadata, "Unknown"))
    }
}