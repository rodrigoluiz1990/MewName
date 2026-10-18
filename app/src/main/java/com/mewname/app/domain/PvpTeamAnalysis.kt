package com.mewname.app.domain

import org.json.JSONArray

/** Published shortlist, not a full battle matrix. Missing pairs must stay unknown. */
internal data class PvpTeamCandidate(
    val id: String, val name: String, val dex: Int?, val recommended: List<String>,
    val fast: List<String>, val charged: List<String>, val ratings: Map<String, Int>
)
internal data class PvpTeamSlot(val id: String = "", val moves: List<String> = emptyList())
internal enum class PvpTeamOutcome { FAVORABLE, EVEN, UNFAVORABLE, UNKNOWN }
internal data class PvpTeamMatchup(val opponent: String, val outcomes: List<PvpTeamOutcome>) {
    val covered get() = PvpTeamOutcome.FAVORABLE in outcomes
    val sharedThreat get() = outcomes.size == 3 && outcomes.all { it == PvpTeamOutcome.UNFAVORABLE }
}
internal fun samePvpMoves(selected: List<String>, recommended: List<String>): Boolean =
    selected.size == recommended.size && selected.isNotEmpty() && selected.first() == recommended.first() &&
        selected.drop(1).toSet() == recommended.drop(1).toSet() && selected.distinct().size == selected.size

internal fun pvpTeamMatchups(team: List<PvpTeamSlot>, candidates: List<PvpTeamCandidate>): List<PvpTeamMatchup> {
    require(team.size == 3)
    val index = candidates.associateBy { it.id }
    val rows = team.map { index[it.id] }
    return rows.flatMap { it?.ratings?.keys.orEmpty() }.distinct().map { opponent ->
        PvpTeamMatchup(opponent, team.mapIndexed { i, slot ->
            val row = rows[i]
            val rating = if (row != null && samePvpMoves(slot.moves, row.recommended)) row.ratings[opponent] else null
            when { rating == null -> PvpTeamOutcome.UNKNOWN; rating > 500 -> PvpTeamOutcome.FAVORABLE
                rating < 500 -> PvpTeamOutcome.UNFAVORABLE; else -> PvpTeamOutcome.EVEN }
        })
    }.sortedWith(compareByDescending<PvpTeamMatchup> { it.sharedThreat }.thenBy { it.covered }.thenBy { it.opponent })
}
internal fun parsePvpTeamCandidates(rankings: String, gameMaster: String): List<PvpTeamCandidate> {
    val species = org.json.JSONObject(gameMaster).getJSONArray("pokemon")
    val dex = (0 until species.length()).associate { species.getJSONObject(it).let { p -> p.getString("speciesId") to p.optInt("dex").takeIf { it > 0 } } }
    val array = JSONArray(rankings)
    return (0 until array.length()).map { index ->
        val row = array.getJSONObject(index)
        val recommended = raidStrings(row.optJSONArray("moveset"))
        fun moves(key: String): List<String> {
            val values = row.optJSONObject("moves")?.optJSONArray(key) ?: JSONArray()
            return (0 until values.length()).map { values.getJSONObject(it).getString("moveId") }.distinct()
        }
        val ratings = linkedMapOf<String, Int>()
        for (key in listOf("matchups", "counters")) {
            val values = row.optJSONArray(key) ?: JSONArray()
            for (i in 0 until values.length()) {
                val value = values.getJSONObject(i)
                if (value.has("rating") && !value.isNull("rating")) {
                    val rating = value.getInt("rating")
                    if (rating in 0..1000) ratings[value.getString("opponent")] = rating
                }
            }
        }
        val id = row.getString("speciesId")
        PvpTeamCandidate(id, row.optString("speciesName", id), dex[id], recommended,
            (moves("fastMoves") + recommended.take(1)).distinct(),
            (moves("chargedMoves") + recommended.drop(1)).distinct(), ratings)
    }.distinctBy { it.id }
}

/** Keep the source's form qualifiers; sprite labels may contain only the base species name. */
internal fun pvpTeamDisplayName(name: String, language: AppLanguage): String {
    if (language == AppLanguage.EN) return name
    val forms = if (language == AppLanguage.PT_BR) mapOf(
        "Shadow" to "Sombroso", "Alolan" to "Alola", "Galarian" to "Galar", "Hisuian" to "Hisui", "Paldean" to "Paldea",
        "Origin" to "Origem", "Altered" to "Alterada", "Defense" to "Defesa", "Attack" to "Ataque", "Speed" to "Velocidade",
        "Dawn Wings" to "Asas da Alvorada", "Dusk Mane" to "Juba Crepúsculo", "Resolute" to "Resoluta"
    ) else mapOf("Shadow" to "Oscuro", "Alolan" to "Alola", "Galarian" to "Galar", "Hisuian" to "Hisui", "Paldean" to "Paldea",
        "Origin" to "Origen", "Altered" to "Modificada", "Defense" to "Defensa", "Attack" to "Ataque", "Speed" to "Velocidad")
    return forms.entries.fold(name) { result, (from, to) -> result.replace(Regex("\\b${Regex.escape(from)}\\b"), to) }
}
