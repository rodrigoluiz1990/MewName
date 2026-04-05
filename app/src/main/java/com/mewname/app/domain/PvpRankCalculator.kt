package com.mewname.app.domain

import android.net.Uri
import android.content.Context
import com.mewname.app.model.PvpLeague
import com.mewname.app.model.PvpLeagueRankInfo
import com.mewname.app.model.PvpSpeciesRankInfo
import org.json.JSONObject
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

class PvpRankCalculator {

    // CP multipliers from level 1 to 51 in 0.5 steps.
    private val cpmTable = listOf(
        0.094, 0.135137432, 0.16639787, 0.192650919, 0.21573247, 0.236572661, 0.25572005, 0.273530381,
        0.29024988, 0.306057377, 0.3210876, 0.33425569, 0.34921268, 0.362457751, 0.37523559, 0.387592406,
        0.39956728, 0.411193551, 0.42250001, 0.432926419, 0.44310755, 0.453059958, 0.46279839, 0.472336083,
        0.48168495, 0.4908558, 0.49985844, 0.508701765, 0.51739395, 0.525942511, 0.53435433, 0.542635767,
        0.55079269, 0.558830576, 0.56675452, 0.574569153, 0.58227891, 0.589887917, 0.59740001, 0.604818814,
        0.61215729, 0.619399365, 0.62656713, 0.633644533, 0.64065295, 0.647576426, 0.65443563, 0.661214806,
        0.667934, 0.674577537, 0.68116492, 0.687680648, 0.69414365, 0.700538673, 0.70688421, 0.713164996,
        0.71939909, 0.725571552, 0.7317, 0.734741009, 0.73776948, 0.740785574, 0.74378943, 0.746781211,
        0.74976104, 0.752729087, 0.75568551, 0.758630378, 0.76156384, 0.764486065, 0.76739717, 0.770297266,
        0.7731865, 0.776064962, 0.77893275, 0.781790055, 0.78463697, 0.787473578, 0.79030001, 0.792803968,
        0.79530001, 0.797803921, 0.8003, 0.802803892, 0.8053, 0.807803863, 0.81029999, 0.812803834,
        0.81529999, 0.817803806, 0.82029999, 0.822803778, 0.82529999, 0.82780375, 0.83029999, 0.832803722,
        0.83529999, 0.837803694, 0.84029999, 0.842803667, 0.84529999
    )

    data class StatProduct(val atk: Int, val def: Int, val sta: Int, val product: Double, val cp: Int, val level: Double)

    fun estimateLevel(
        context: Context,
        pokemonName: String,
        cp: Int,
        atk: Int,
        def: Int,
        sta: Int
    ): Double? {
        val baseStats = loadBaseStats(context, pokemonName) ?: return null
        return estimateLevel(baseStats, cp, atk, def, sta)
    }

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
        return calculateBestFamilyLeagueRanks(context, listOf(pokemonName), atk, def, sta)
    }

    fun calculateBestFamilyLeagueRanks(
        context: Context,
        pokemonNames: List<String>,
        atk: Int,
        def: Int,
        sta: Int
    ): List<PvpLeagueRankInfo> {
        val familyCandidates = pokemonNames.distinct().filter { it.isNotBlank() }
        if (familyCandidates.isEmpty()) return emptyList()

        return listOf(PvpLeague.LITTLE, PvpLeague.GREAT, PvpLeague.ULTRA, PvpLeague.MASTER).mapNotNull { league ->
            val infos = familyCandidates.mapNotNull speciesLoop@{ name ->
                val baseStats = loadBaseStats(context, name) ?: return@speciesLoop null
                calculateLeagueRankInfo(baseStats, name, atk, def, sta, league)
            }
            selectBestFamilyOption(infos)
        }
    }

    fun calculateFamilySpeciesLeagueRanks(
        context: Context,
        pokemonNames: List<String>,
        atk: Int,
        def: Int,
        sta: Int
    ): List<PvpSpeciesRankInfo> {
        val familyCandidates = pokemonNames.distinct().filter { it.isNotBlank() }
        if (familyCandidates.isEmpty()) return emptyList()

        return listOf(PvpLeague.LITTLE, PvpLeague.GREAT, PvpLeague.ULTRA, PvpLeague.MASTER).flatMap { league ->
            familyCandidates.mapNotNull { name ->
                val baseStats = loadBaseStats(context, name) ?: return@mapNotNull null
                calculateSpeciesLeagueRankInfo(baseStats, name, atk, def, sta, league)
            }
        }
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
                    val best = getBestStatProduct(baseStats, a, d, s, cap, maxLevelForLeague(league))
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
                pokemonName = pokemonName,
            eligible = false,
            bestStatProduct = null,
            stadiumUrl = stadiumUrl,
                description = if (minCp > cap) {
                    "Acima do limite da ${leagueLabel(league)}: mesmo no nível 1 esse Pokémon fica com CP mínimo $minCp, acima de $cap."
                } else {
                    "Não foi possível encontrar um nível elegível para a ${leagueLabel(league)} com essa combinação de IV."
                }
            )
        }

        eligibleProducts.sortDescending()
        val rank = eligibleProducts.indexOf(currentBest.product) + 1
        return PvpLeagueRankInfo(
            league = league,
            pokemonName = pokemonName,
            eligible = true,
            rank = if (rank > 0) rank else null,
            bestCp = currentBest.cp,
            bestLevel = currentBest.level,
            bestStatProduct = currentBest.product,
            stadiumUrl = stadiumUrl,
            description = buildString {
                append("Rank ${if (rank > 0) rank else "-"} na ${leagueLabel(league)}.")
                if (league != PvpLeague.MASTER) {
                    append(" Melhor CP ${currentBest.cp}")
                }
                append(" no nível ${formatLevel(currentBest.level)}.")
            }
        )
    }

    private fun calculateSpeciesLeagueRankInfo(
        baseStats: JSONObject,
        pokemonName: String,
        atk: Int,
        def: Int,
        sta: Int,
        league: PvpLeague
    ): PvpSpeciesRankInfo {
        val info = calculateLeagueRankInfo(baseStats, pokemonName, atk, def, sta, league)
        return PvpSpeciesRankInfo(
            pokemonName = pokemonName,
            league = info.league,
            eligible = info.eligible,
            rank = info.rank,
            bestCp = info.bestCp,
            bestLevel = info.bestLevel,
            bestStatProduct = info.bestStatProduct,
            stadiumUrl = info.stadiumUrl,
            description = info.description
        )
    }

    private fun estimateLevel(
        baseStats: JSONObject,
        observedCp: Int,
        atk: Int,
        def: Int,
        sta: Int
    ): Double? {
        val bAtk = baseStats.getInt("attack")
        val bDef = baseStats.getInt("defense")
        val bSta = baseStats.getInt("stamina")

        var bestLevel: Double? = null
        var bestDistance = Int.MAX_VALUE

        for (i in cpmTable.indices) {
            val cpm = cpmTable[i]
            val level = 1.0 + (i * 0.5)
            val cp = calculateCp(bAtk + atk, bDef + def, bSta + sta, cpm)
            val distance = kotlin.math.abs(cp - observedCp)

            if (distance < bestDistance || (distance == bestDistance && level > (bestLevel ?: 0.0))) {
                bestDistance = distance
                bestLevel = level
            }
        }

        return bestLevel?.takeIf { bestDistance <= 1 }
    }

    private fun getBestStatProduct(
        base: JSONObject,
        ivAtk: Int,
        ivDef: Int,
        ivSta: Int,
        cap: Int,
        maxLevel: Double
    ): StatProduct? {
        val bAtk = base.getInt("attack")
        val bDef = base.getInt("defense")
        val bSta = base.getInt("stamina")

        var bestProduct: StatProduct? = null

        for (i in cpmTable.indices) {
            val cpm = cpmTable[i]
            val level = 1.0 + (i * 0.5)
            if (level > maxLevel) break
            val cp = calculateCp(bAtk + ivAtk, bDef + ivDef, bSta + ivSta, cpm)
            
            if (cp <= cap) {
                val effectiveAttack = (bAtk + ivAtk) * cpm
                val effectiveDefense = (bDef + ivDef) * cpm
                val effectiveHp = floor((bSta + ivSta) * cpm).toInt().coerceAtLeast(10)
                val product = effectiveAttack * effectiveDefense * effectiveHp
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
            val jsonString = context.assets.open(AssetPaths.POKEMON_STATS).bufferedReader().use { it.readText() }
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
        val includeBestBuddy = league == PvpLeague.MASTER
        val levelCap = if (includeBestBuddy) "51" else "50"
        return Uri.Builder()
            .scheme("https")
            .authority("www.stadiumgaming.gg")
            .path("rank-checker")
            .appendQueryParameter("pokemon", pokemonName.uppercase())
            .appendQueryParameter("att_iv", atk.toString())
            .appendQueryParameter("def_iv", def.toString())
            .appendQueryParameter("hp_iv", sta.toString())
            .appendQueryParameter("league", leagueCap(league).toString())
            .appendQueryParameter("levelCap", levelCap)
            .appendQueryParameter("min_iv", "0")
            .appendQueryParameter("include_best_buddy", includeBestBuddy.toString())
            .build()
            .toString()
    }

    private fun maxLevelForLeague(league: PvpLeague): Double {
        return when (league) {
            PvpLeague.MASTER -> 51.0
            else -> 50.0
        }
    }

    private fun selectBestFamilyOption(options: List<PvpLeagueRankInfo>): PvpLeagueRankInfo? {
        if (options.isEmpty()) return null
        val eligible = options.filter { it.eligible && it.bestStatProduct != null }
        if (eligible.isNotEmpty()) {
            return eligible.maxWithOrNull(
                compareBy<PvpLeagueRankInfo> { it.bestStatProduct ?: Double.NEGATIVE_INFINITY }
                    .thenByDescending { it.bestLevel ?: 0.0 }
                    .thenByDescending { it.bestCp ?: 0 }
            )
        }
        return options.firstOrNull()
    }

    private fun formatLevel(level: Double): String {
        return if (level % 1.0 == 0.0) {
            level.toInt().toString()
        } else {
            level.toString().replace(".", ",")
        }
    }
}
