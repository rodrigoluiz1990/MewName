package com.mewname.app

import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PvpTeamAnalysisTest {
    private val moves = listOf("FAST", "A", "B")
    private fun candidate(id: String, ratings: Map<String, Int>) = PvpTeamCandidate(id, id, 1, moves, listOf("FAST"), listOf("A", "B", "C"), ratings)
    private fun team() = listOf("one", "two", "three").map { PvpTeamSlot(it, moves) }

    @Test fun namesKeepRegionalAndShadowForms() {
        assertEquals("Corsola (Galar)", pvpTeamDisplayName("Corsola (Galarian)", AppLanguage.PT_BR))
        assertEquals("Ninetales (Alola) (Sombroso)", pvpTeamDisplayName("Ninetales (Alolan) (Shadow)", AppLanguage.PT_BR))
        assertEquals("Palkia (Origem)", pvpTeamDisplayName("Palkia (Origin)", AppLanguage.PT_BR))
    }
    @Test fun missingMatchupsStayUnknownNotEvenOrFavorable() {
        val result = pvpTeamMatchups(team(), listOf(candidate("one", mapOf("enemy" to 300)), candidate("two", emptyMap()), candidate("three", emptyMap()))).single()
        assertEquals(listOf(PvpTeamOutcome.UNFAVORABLE, PvpTeamOutcome.UNKNOWN, PvpTeamOutcome.UNKNOWN), result.outcomes)
        assertFalse(result.covered)
        assertFalse(result.sharedThreat)
    }
    @Test fun sharedThreatRequiresThreeKnownLosses() {
        val rows = listOf("one", "two", "three").map { candidate(it, mapOf("enemy" to 499)) }
        assertTrue(pvpTeamMatchups(team(), rows).single().sharedThreat)
        assertFalse(pvpTeamMatchups(team(), rows.dropLast(1)).single().sharedThreat)
    }
    @Test fun coverageRequiresAtLeastOneKnownWinAndTieIsNotWin() {
        val result = pvpTeamMatchups(team(), listOf(candidate("one", mapOf("enemy" to 500)), candidate("two", mapOf("enemy" to 501)))).single()
        assertTrue(result.covered)
        assertEquals(PvpTeamOutcome.EVEN, result.outcomes[0])
        assertFalse(pvpTeamMatchups(team(), listOf(candidate("one", mapOf("enemy" to 500)))).single().covered)
    }
    @Test fun customMovesInvalidateOnlyTheirMember() {
        val rows = listOf("one", "two", "three").map { candidate(it, mapOf("enemy" to 600)) }
        val changed = team().toMutableList().also { it[0] = it[0].copy(moves = listOf("FAST", "A", "C")) }
        assertEquals(listOf(PvpTeamOutcome.UNKNOWN, PvpTeamOutcome.FAVORABLE, PvpTeamOutcome.FAVORABLE), pvpTeamMatchups(changed, rows).single().outcomes)
    }
    @Test fun chargeSlotOrderDoesNotChangeMovesetButMissingOrDuplicateMoveDoes() {
        assertTrue(samePvpMoves(listOf("FAST", "B", "A"), moves))
        assertFalse(samePvpMoves(listOf("FAST", "A"), moves))
        assertFalse(samePvpMoves(listOf("FAST", "A", "A"), moves))
        assertFalse(samePvpMoves(listOf("OTHER", "A", "B"), moves))
    }
    @Test fun formsAndShadowAreNeverMerged() {
        val rows = listOf(candidate("one", mapOf("enemy" to 800, "enemy_shadow" to 200)))
        val results = pvpTeamMatchups(team(), rows).associateBy { it.opponent }
        assertEquals(2, results.size)
        assertEquals(PvpTeamOutcome.FAVORABLE, results.getValue("enemy").outcomes[0])
        assertEquals(PvpTeamOutcome.UNFAVORABLE, results.getValue("enemy_shadow").outcomes[0])
    }
    @Test fun parserPreservesRatingsAndIgnoresAbsentAndInvalidScores() {
        val rows = parsePvpTeamCandidates(
            """[{"speciesId":"one","speciesName":"One","moveset":["FAST","A","B"],"matchups":[{"opponent":"good","rating":800},{"opponent":"unknown"}],"counters":[{"opponent":"bad","rating":300},{"opponent":"invalid","rating":-1}]}]""",
            """{"pokemon":[{"speciesId":"one","dex":25}]}""")
        assertEquals(25, rows.single().dex)
        assertEquals(mapOf("good" to 800, "bad" to 300), rows.single().ratings)
        assertEquals(listOf("FAST"), rows.single().fast)
        assertEquals(listOf("A", "B"), rows.single().charged)
    }
}
