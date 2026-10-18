package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Regression values checked against the device GAME_MASTER cache dated 2026-10-17. */
@RunWith(RobolectricTestRunner::class)
class CurrentMoveCatalogTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun moves() = GameInfoRepository.loadMoveCatalog(context).associateBy { it.moveId }

    @Test fun quickMovesHaveBothBattleModesAndCorrectTurns() {
        val moves = moves()
        assertEquals(328, moves.size)
        for (id in listOf(401, 402, 403)) {
            val move = requireNotNull(moves[id])
            assertEquals(MoveCategory.FAST, move.category)
            assertNotNull(move.pvpPower)
            assertNotNull(move.pvpEnergyDelta)
        }
        assertEquals(1, moves[401]?.pvpTurnDuration)
        assertEquals(2, moves[402]?.pvpTurnDuration)
        assertEquals(1, moves[403]?.pvpTurnDuration)
        assertEquals(5, moves[346]?.pvpTurnDuration)
    }
    @Test fun currentCombatValuesAndVariantsRemainDistinct() {
        val moves = moves()
        assertEquals(2, moves[202]?.pvpPower) // Bite
        assertEquals(4, moves[202]?.pvpEnergyDelta)
        assertEquals(130, moves[288]?.power) // Psycho Boost
        assertEquals(-33, moves[288]?.energyDelta)
        assertEquals("Aura Wheel Dark", moves[407]?.name)
        assertEquals("Dark", moves[407]?.type)
        assertEquals(-50, moves[407]?.energyDelta)
        assertEquals("Mystical Fire", moves[382]?.name)
        assertEquals(3, listOf(335, 360, 361).map { moves[it]?.name }.distinct().size)
        assertEquals("Espada Secreta", moves[489]?.namePt)
    }
    @Test fun newMovesResolveInPokemonAndLegacyCatalogs() {
        val moves = moves()
        val pairs = listOf("Toucannon" to 490, "Blacephalon" to 491, "Rillaboom" to 492,
            "Cinderace" to 493, "Zeraora" to 497, "Inteleon" to 592, "Cramorant" to 593)
        val raw = JSONArray(context.assets.open("pokemon/current_moves.json").bufferedReader().use { it.readText() })
        for ((pokemon, id) in pairs) {
            val move = requireNotNull(moves[id])
            assertNotNull(move.power)
            assertNotNull(move.pvpPower)
            assertNotNull(move.namePt)
            val entry = (0 until raw.length()).map { raw.getJSONObject(it) }.single { it.getString("name") == pokemon }
            val charged = entry.getJSONArray("chargedMoves")
            val link = (0 until charged.length()).map { charged.getJSONObject(it) }.single { it.getString("name") == move.name }
            assertEquals(id in listOf(490, 491), link.getBoolean("legacy"))
        }
        for (id in listOf(594, 595)) {
            assertNull(moves[id]?.power)
            assertNull(moves[id]?.energyDelta)
            assertNull(moves[id]?.pvpPower)
        }
        val effects = loadMoveEffects(context)
        assertEquals(-1.0, effects[490]?.get("target_attack_stat_stage_change"))
        assertEquals(0.125, effects[592]?.get("activation_chance"))
    }
}
