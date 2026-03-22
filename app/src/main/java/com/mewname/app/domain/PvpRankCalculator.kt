package com.mewname.app.domain

import android.net.Uri
import android.content.Context
import com.mewname.app.model.PvpLeague
import com.mewname.app.model.PvpLeagueRankInfo
import org.json.JSONObject
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

class PvpRankCalculator {

    // CP Multipliers for levels 1 to 50 (steps of 0.5)
    private val cpmTable = listOf(
        0.094, 0.135137432, 0.16639787, 0.192650919, 0.21573247, 0.236572661, 0.25572005, 0.273530381, 0.29024988, 
        0.306057377, 0.3210876, 0.335445036, 0.34921268, 0.362457751, 0.37523559, 0.387592406, 0.39956728, 
        0.411193551, 0.42250001, 0.43351174, 0.44424921, 0.454730751, 0.465, 0.47505952, 0.4848845, 0.49449069, 
        0.5038925, 0.51309967, 0.52212024, 0.53096376, 0.53963599, 0.54814354, 0.5564908, 0.56468311, 0.5727256, 
        0.5806247, 0.58838587, 0.59601444, 0.603515, 0.6108924, 0.6181511, 0.62529505, 0.63232714, 0.639252, 
        0.64607405, 0.6527975, 0.65942605, 0.665963, 0.6724123, 0.6787772, 0.6850605, 0.691265, 0.6974, 
        0.703449, 0.70945728, 0.7153905, 0.721279, 0.727113, 0.73286, 0.73858, 0.744208, 0.74976104, 0.755249, 
        0.760657, 0.765987, 0.7712361, 0.776415, 0.781525, 0.78657, 0.791557, 0.7965, 0.80135, 0.80618, 
        0.81096, 0.81571, 0.8204, 0.82508, 0.8296, 0.8341, 0.8386, 0.843, 0.8474, 0.8517, 0.856, 0.8603, 
        0.8645, 0.8687, 0.8728, 0.8769, 0.881, 0.885, 0.889, 0.893, 0.897, 0.901, 0.905, 0.909, 0.913, 0.917, 0.921
    )

    data class StatProduct(val atk: Int, val def: Int, val sta: Int, val product: Double, val cp: Int, val level: Double)

    fun calculateRank(context: Context, pokemonName: String, atk: Int, def: Int, sta: Int, league: PvpLeague): Int? {
        return calculateLeagueRankInfo(context, pokemonName, atk, def, sta, league)?.rank
    }

    fun calculateLeagueRanks(
        context: Context,
        pokemonName: String,
        atk: Int,
        def: Int,
        sta: Int
    ): List<PvpLeagueRankInfo> {
        val baseStats = loadBaseStats(context, pokemonName) ?: return emptyList()
        return listOf(PvpLeague.LITTLE, PvpLeague.GREAT, PvpLeague.ULTRA, PvpLeague.MASTER)
            .map { league -> calculateLeagueRankInfo(baseStats, pokemonName, atk, def, sta, league) }
    }

    fun calculateLeagueRankInfo(
        context: Context,
        pokemonName: String,
        atk: Int,
        def: Int,
        sta: Int,
        league: PvpLeague
    ): PvpLeagueRankInfo? {
        val baseStats = loadBaseStats(context, pokemonName) ?: return null
        return calculateLeagueRankInfo(baseStats, pokemonName, atk, def, sta, league)
    }

    private fun calculateLeagueRankInfo(
        baseStats: JSONObject,
        pokemonName: String,
        atk: Int,
        def: Int,
        sta: Int,
        league: PvpLeague
    ): PvpLeagueRankInfo {
        val cap = leagueCap(league)
        val stadiumUrl = buildStadiumUrl(pokemonName, atk, def, sta, league)
        val eligibleProducts = mutableListOf<Double>()
        var currentBest: StatProduct? = null

        for (a in 0..15) {
            for (d in 0..15) {
                for (s in 0..15) {
                    val best = getBestStatProduct(baseStats, a, d, s, cap)
                    if (best != null) {
                        eligibleProducts += best.product
                        if (a == atk && d == def && s == sta) {
                            currentBest = best
                        }
                    }
                }
            }
        }

        if (currentBest == null) {
            val minCp = minimumCpAtLowestLevel(baseStats, atk, def, sta)
            return PvpLeagueRankInfo(
                league = league,
                eligible = false,
                stadiumUrl = stadiumUrl,
                description = if (minCp > cap) {
                    "Acima do limite da ${leagueLabel(league)}: mesmo no nivel 1 esse Pokemon fica com CP minimo $minCp, acima de $cap."
                } else {
                    "Nao foi possivel encontrar um nivel elegivel para a ${leagueLabel(league)} com essa combinacao de IV."
                }
            )
        }

        eligibleProducts.sortDescending()
        val rank = eligibleProducts.indexOf(currentBest.product) + 1
        return PvpLeagueRankInfo(
            league = league,
            eligible = true,
            rank = if (rank > 0) rank else null,
            bestCp = currentBest.cp,
            bestLevel = currentBest.level,
            stadiumUrl = stadiumUrl,
            description = buildString {
                append("Rank ${if (rank > 0) rank else "-"} na ${leagueLabel(league)}.")
                if (league != PvpLeague.MASTER) {
                    append(" Melhor CP ${currentBest.cp}")
                }
                append(" no nivel ${formatLevel(currentBest.level)}.")
            }
        )
    }

    private fun getBestStatProduct(base: JSONObject, ivAtk: Int, ivDef: Int, ivSta: Int, cap: Int): StatProduct? {
        val bAtk = base.getInt("attack")
        val bDef = base.getInt("defense")
        val bSta = base.getInt("stamina")

        var bestProduct: StatProduct? = null

        for (i in cpmTable.indices) {
            val cpm = cpmTable[i]
            val level = 1.0 + (i * 0.5)
            val cp = calculateCp(bAtk + ivAtk, bDef + ivDef, bSta + ivSta, cpm)
            
            if (cp <= cap) {
                val product = (bAtk + ivAtk) * cpm * (bDef + ivDef) * cpm * (bSta + ivSta) * cpm
                if (bestProduct == null || product > bestProduct.product) {
                    bestProduct = StatProduct(ivAtk, ivDef, ivSta, product, cp, level)
                }
            }
        }
        return bestProduct
    }

    private fun calculateCp(atk: Int, def: Int, sta: Int, cpm: Double): Int {
        return floor(atk * sqrt(def.toDouble()) * sqrt(sta.toDouble()) * cpm.pow(2.0) / 10.0).toInt().coerceAtLeast(10)
    }

    private fun loadBaseStats(context: Context, name: String): JSONObject? {
        return try {
            val jsonString = context.assets.open("pokemon_stats.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            jsonObject.optJSONObject(name.uppercase())
        } catch (e: Exception) {
            null
        }
    }

    private fun minimumCpAtLowestLevel(base: JSONObject, ivAtk: Int, ivDef: Int, ivSta: Int): Int {
        val bAtk = base.getInt("attack")
        val bDef = base.getInt("defense")
        val bSta = base.getInt("stamina")
        return calculateCp(bAtk + ivAtk, bDef + ivDef, bSta + ivSta, cpmTable.first())
    }

    private fun leagueCap(league: PvpLeague): Int {
        return when (league) {
            PvpLeague.GREAT -> 1500
            PvpLeague.ULTRA -> 2500
            PvpLeague.LITTLE -> 500
            PvpLeague.MASTER -> 10000
        }
    }

    private fun leagueLabel(league: PvpLeague): String {
        return when (league) {
            PvpLeague.LITTLE -> "Little League"
            PvpLeague.GREAT -> "Great League"
            PvpLeague.ULTRA -> "Ultra League"
            PvpLeague.MASTER -> "Master League"
        }
    }

    private fun buildStadiumUrl(
        pokemonName: String,
        atk: Int,
        def: Int,
        sta: Int,
        league: PvpLeague
    ): String {
        return Uri.Builder()
            .scheme("https")
            .authority("www.stadiumgaming.gg")
            .path("rank-checker")
            .appendQueryParameter("pokemon", pokemonName.uppercase())
            .appendQueryParameter("att_iv", atk.toString())
            .appendQueryParameter("def_iv", def.toString())
            .appendQueryParameter("hp_iv", sta.toString())
            .appendQueryParameter("league", leagueCap(league).toString())
            .appendQueryParameter("levelCap", "50")
            .appendQueryParameter("min_iv", "0")
            .appendQueryParameter("include_best_buddy", "false")
            .build()
            .toString()
    }

    private fun formatLevel(level: Double): String {
        return if (level % 1.0 == 0.0) {
            level.toInt().toString()
        } else {
            level.toString().replace(".", ",")
        }
    }
}
