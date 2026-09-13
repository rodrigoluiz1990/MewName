package com.mewname.app.domain

import android.net.Uri
import android.content.Context
import com.mewname.app.model.PvpLeague
import com.mewname.app.model.PvpLeagueRankInfo
import com.mewname.app.model.PvpSpeciesRankInfo
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

class PvpRankCalculator {

    // CP multipliers from level 1 to 51 in 0.5 steps. Keep full precision:
    // rounding can incorrectly admit a 1501 CP spread into Great League.
    // Reference: https://github.com/pvpoke/pvpoke/blob/master/src/js/pokemon/Pokemon.js
    private val cpmTable = listOf(
        0.0939999967813491, 0.135137430784308, 0.166397869586944, 0.192650914456886, 0.215732470154762,
        0.236572655026622, 0.255720049142837, 0.273530381100769, 0.290249884128570, 0.306057381335773,
        0.321087598800659, 0.335445032295077, 0.349212676286697, 0.362457748778790, 0.375235587358474,
        0.387592411085168, 0.399567276239395, 0.411193549517250, 0.422500014305114, 0.432926413410414,
        0.443107545375824, 0.453059953871985, 0.462798386812210, 0.472336077786704, 0.481684952974319,
        0.490855810259008, 0.499858438968658, 0.508701756943992, 0.517393946647644, 0.525942508771329,
        0.534354329109191, 0.542635762230353, 0.550792694091796, 0.558830599438087, 0.566754519939422,
        0.574569148039264, 0.582278907299041, 0.589887911977272, 0.597400009632110, 0.604823657502073,
        0.612157285213470, 0.619404110566050, 0.626567125320434, 0.633649181622743, 0.640652954578399,
        0.647580963301656, 0.654435634613037, 0.661219263506722, 0.667934000492096, 0.674581899290818,
        0.681164920330047, 0.687684905887771, 0.694143652915954, 0.700542893277978, 0.706884205341339,
        0.713169102333341, 0.719399094581604, 0.725575616972598, 0.731700003147125, 0.734741011137376,
        0.737769484519958, 0.740785574597326, 0.743789434432983, 0.746781208702482, 0.749761044979095,
        0.752729105305821, 0.755685508251190, 0.758630366519684, 0.761563837528228, 0.764486065255226,
        0.767397165298461, 0.770297273971590, 0.773186504840850, 0.776064945942412, 0.778932750225067,
        0.781790064808426, 0.784636974334716, 0.787473583646825, 0.790300011634826, 0.792803950958807,
        0.795300006866455, 0.797803921486970, 0.800300002098083, 0.802803892322847, 0.805299997329711,
        0.807803863460723, 0.810299992561340, 0.812803834895026, 0.815299987792968, 0.817803806620319,
        0.820299983024597, 0.822803778631297, 0.825299978256225, 0.827803750922782, 0.830299973487854,
        0.832803753381377, 0.835300028324127, 0.837803755931569, 0.840300023555755, 0.842803729034748,
        0.845300018787384
    )

    data class StatProduct(val atk: Int, val def: Int, val sta: Int, val product: Double, val cp: Int, val level: Double)
    data class LevelEstimate(val pokemonName: String, val level: Double, val cpDistance: Int)
    data class HpLevelEstimate(val pokemonName: String, val level: Double, val hpDistance: Int)

    private data class RankTable(
        val byIv: Map<String, StatProduct>,
        val productsDescending: List<Double>
    )

    @Volatile
    private var baseStatsRoot: JSONObject? = null
    @Volatile
    private var canonicalNameByAlias: Map<String, String>? = null
    private val rankTableCache = mutableMapOf<String, RankTable>()

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

    fun estimateLevelForCandidates(
        context: Context,
        pokemonNames: List<String>,
        cp: Int,
        atk: Int,
        def: Int,
        sta: Int
    ): LevelEstimate? {
        return pokemonNames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .mapNotNull { pokemonName ->
                val baseStats = loadBaseStats(context, pokemonName) ?: return@mapNotNull null
                estimateLevelWithDistance(baseStats, cp, atk, def, sta)?.copy(pokemonName = pokemonName)
            }
            .filter { it.cpDistance <= 1 }
            .minWithOrNull(compareBy<LevelEstimate> { it.cpDistance }.thenBy { it.pokemonName })
    }

    fun estimateCpAtLevel(
        context: Context,
        pokemonName: String,
        atk: Int,
        def: Int,
        sta: Int,
        level: Double
    ): Int? {
        val baseStats = loadBaseStats(context, pokemonName) ?: return null
        val index = ((level - 1.0) / 0.5).toInt()
            .coerceIn(0, cpmTable.lastIndex)
        val cpm = cpmTable.getOrNull(index) ?: return null
        return calculateCp(
            baseStats.getInt("attack") + atk,
            baseStats.getInt("defense") + def,
            baseStats.getInt("stamina") + sta,
            cpm
        )
    }

    fun estimateHpAtLevel(
        context: Context,
        pokemonName: String,
        sta: Int,
        level: Double
    ): Int? {
        val baseStats = loadBaseStats(context, pokemonName) ?: return null
        val index = ((level - 1.0) / 0.5).toInt().coerceIn(0, cpmTable.lastIndex)
        val cpm = cpmTable.getOrNull(index) ?: return null
        return floor((baseStats.getInt("stamina") + sta) * cpm).toInt().coerceAtLeast(10)
    }

    fun estimateLevelFromHpForCandidates(
        context: Context,
        pokemonNames: List<String>,
        observedHp: Int,
        sta: Int
    ): HpLevelEstimate? {
        return pokemonNames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .mapNotNull { pokemonName ->
                val baseStats = loadBaseStats(context, pokemonName) ?: return@mapNotNull null
                estimateLevelFromHpWithDistance(baseStats, observedHp, sta)?.copy(pokemonName = pokemonName)
            }
            .filter { it.hpDistance == 0 }
            .minWithOrNull(compareBy<HpLevelEstimate> { it.hpDistance }.thenByDescending { it.level }.thenBy { it.pokemonName })
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
        val options = PvpCalculationSettings.read(context)
        val familyCandidates = pokemonNames.filter { it.isNotBlank() }.map { resolveCanonicalPokemonName(context, it) }.distinct()
        if (familyCandidates.isEmpty()) return emptyList()

        return listOf(PvpLeague.LITTLE, PvpLeague.GREAT, PvpLeague.ULTRA, PvpLeague.MASTER).mapNotNull { league ->
            val infos = familyCandidates.mapNotNull speciesLoop@{ name ->
                val baseStats = loadBaseStats(context, name) ?: return@speciesLoop null
                calculateLeagueRankInfo(baseStats, name, atk, def, sta, league, options)
            }
            selectBestFamilyOption(infos)
        }
    }

    fun calculateFamilySpeciesLeagueRanks(
        context: Context,
        pokemonNames: List<String>,
        atk: Int,
        def: Int,
        sta: Int,
        currentPokemonName: String? = null,
        currentLevel: Double? = null,
        currentCp: Int? = null,
        options: PvpCalculationOptions = PvpCalculationSettings.read(context)
    ): List<PvpSpeciesRankInfo> {
        val familyCandidates = pokemonNames.filter { it.isNotBlank() }.map { resolveCanonicalPokemonName(context, it) }.distinct()
        if (familyCandidates.isEmpty()) return emptyList()

        return listOf(PvpLeague.LITTLE, PvpLeague.GREAT, PvpLeague.ULTRA, PvpLeague.MASTER).flatMap { league ->
            familyCandidates.mapNotNull { name ->
                val baseStats = loadBaseStats(context, name) ?: return@mapNotNull null
                calculateSpeciesLeagueRankInfo(
                    baseStats = baseStats,
                    pokemonName = name,
                    atk = atk,
                    def = def,
                    sta = sta,
                    league = league,
                    currentPokemonName = currentPokemonName,
                    currentLevel = currentLevel,
                    currentCp = currentCp,
                    options = options,
                    context = context
                )
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
        return calculateLeagueRankInfo(baseStats, pokemonName, atk, def, sta, league, PvpCalculationSettings.read(context))
    }

    private fun calculateLeagueRankInfo(
        baseStats: JSONObject,
        pokemonName: String,
        atk: Int,
        def: Int,
        sta: Int,
        league: PvpLeague,
        options: PvpCalculationOptions
    ): PvpLeagueRankInfo {
        val cap = leagueCap(league)
        val stadiumUrl = buildStadiumUrl(pokemonName, atk, def, sta, league, options)
        val rankTable = getRankTable(baseStats, pokemonName, league, options)
        val currentBest = rankTable.byIv[ivKey(atk, def, sta)]

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

        val rank = rankTable.productsDescending.indexOf(currentBest.product) + 1
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
        league: PvpLeague,
        currentPokemonName: String? = null,
        currentLevel: Double? = null,
        currentCp: Int? = null,
        context: Context? = null,
        options: PvpCalculationOptions
    ): PvpSpeciesRankInfo {
        val info = calculateLeagueRankInfo(baseStats, pokemonName, atk, def, sta, league, options)
        val sameSpecies = context != null && currentPokemonName != null &&
            normalizeKey(resolveCanonicalPokemonName(context, currentPokemonName)) ==
                normalizeKey(resolveCanonicalPokemonName(context, pokemonName))
        val levelCp = if (context != null && currentLevel != null) {
            estimateCpAtLevel(context, pokemonName, atk, def, sta, currentLevel)
        } else null
        // A low OCR CP must not override the level constraint for the selected form.
        val projectedCp = if (sameSpecies) listOfNotNull(currentCp, levelCp).maxOrNull() else levelCp
        val evolutionEligible = projectedCp == null || projectedCp <= leagueCap(league)
        val eligible = info.eligible && evolutionEligible
        return PvpSpeciesRankInfo(
            pokemonName = pokemonName,
            league = info.league,
            eligible = eligible,
            rank = info.rank,
            bestCp = info.bestCp,
            bestLevel = info.bestLevel,
            bestStatProduct = info.bestStatProduct,
            stadiumUrl = info.stadiumUrl,
            description = if (eligible) {
                info.description
            } else {
                "A evolução ultrapassa o limite da ${leagueLabel(league)} no nível atual."
            }
        )
    }

    private fun estimateLevel(
        baseStats: JSONObject,
        observedCp: Int,
        atk: Int,
        def: Int,
        sta: Int
    ): Double? {
        return estimateLevelWithDistance(baseStats, observedCp, atk, def, sta)
            ?.takeIf { it.cpDistance <= 1 }
            ?.level
    }

    private fun estimateLevelWithDistance(
        baseStats: JSONObject,
        observedCp: Int,
        atk: Int,
        def: Int,
        sta: Int
    ): LevelEstimate? {
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

        return bestLevel?.let { LevelEstimate(pokemonName = "", level = it, cpDistance = bestDistance) }
    }

    private fun estimateLevelFromHpWithDistance(
        baseStats: JSONObject,
        observedHp: Int,
        sta: Int
    ): HpLevelEstimate? {
        val baseStamina = baseStats.getInt("stamina")
        var bestLevel: Double? = null
        var bestDistance = Int.MAX_VALUE

        for (i in cpmTable.indices) {
            val cpm = cpmTable[i]
            val level = 1.0 + (i * 0.5)
            val hp = floor((baseStamina + sta) * cpm).toInt().coerceAtLeast(10)
            val distance = kotlin.math.abs(hp - observedHp)

            if (distance < bestDistance || (distance == bestDistance && level > (bestLevel ?: 0.0))) {
                bestDistance = distance
                bestLevel = level
            }
        }

        return bestLevel?.let { HpLevelEstimate(pokemonName = "", level = it, hpDistance = bestDistance) }
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
                val effectiveHp = floor((bSta + ivSta) * cpm).toInt().coerceAtLeast(10)
                // Multiply the integer factors first so equivalent IV products at the same level
                // remain exactly tied; separate CPM multiplications introduce rounding noise.
                val statFactors = (bAtk + ivAtk).toDouble() * (bDef + ivDef) * effectiveHp
                val product = statFactors * cpm.pow(2.0)
                if (bestProduct == null || product > bestProduct.product) {
                    bestProduct = StatProduct(ivAtk, ivDef, ivSta, product, cp, level)
                }
            }
        }
        return bestProduct
    }

    private fun getRankTable(baseStats: JSONObject, pokemonName: String, league: PvpLeague, options: PvpCalculationOptions): RankTable {
        val cacheKey = "${pokemonName.uppercase()}|${league.name}|${options.effectiveMaxLevel}"
        synchronized(rankTableCache) {
            rankTableCache[cacheKey]?.let { return it }
        }

        val byIv = mutableMapOf<String, StatProduct>()
        val products = mutableListOf<Double>()
        val cap = leagueCap(league)
        val maxLevel = options.effectiveMaxLevel
        for (a in 0..15) {
            if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException("Ranking cancelled")
            for (d in 0..15) {
                for (s in 0..15) {
                    val best = getBestStatProduct(baseStats, a, d, s, cap, maxLevel)
                    if (best != null) {
                        byIv[ivKey(a, d, s)] = best
                        products += best.product
                    }
                }
            }
        }
        products.sortDescending()

        val table = RankTable(byIv = byIv, productsDescending = products)
        synchronized(rankTableCache) {
            rankTableCache[cacheKey] = table
        }
        return table
    }

    private fun ivKey(atk: Int, def: Int, sta: Int): String = "$atk/$def/$sta"

    private fun calculateCp(atk: Int, def: Int, sta: Int, cpm: Double): Int {
        return floor(atk * sqrt(def.toDouble()) * sqrt(sta.toDouble()) * cpm.pow(2.0) / 10.0).toInt().coerceAtLeast(10)
    }

    private fun loadBaseStats(context: Context, name: String): JSONObject? {
        return try {
            val jsonObject = loadBaseStatsRoot(context)
            val canonicalName = resolveCanonicalPokemonName(context, name)
            jsonObject.optJSONObject(normalizeKey(canonicalName))
                ?: jsonObject.optJSONObject(normalizeKey(name))
        } catch (e: Exception) {
            CatalogLoadFeedback.reportFailure(CatalogLoadArea.PVP, e)
            null
        }
    }

    private fun loadBaseStatsRoot(context: Context): JSONObject {
        baseStatsRoot?.let { return it }
        synchronized(this) {
            baseStatsRoot?.let { return it }
            val jsonString = context.assets.open(AssetPaths.POKEMON_STATS).bufferedReader().use { it.readText() }
            val loaded = JSONObject(jsonString)
            baseStatsRoot = loaded
            return loaded
        }
    }

    fun canonicalName(context: Context, name: String): String = resolveCanonicalPokemonName(context, name)

    private fun resolveCanonicalPokemonName(context: Context, name: String): String {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return name
        val aliasMap = loadCanonicalNameByAlias(context)
        return aliasMap[normalizeKey(trimmed)] ?: trimmed
    }

    private fun loadCanonicalNameByAlias(context: Context): Map<String, String> {
        canonicalNameByAlias?.let { return it }
        synchronized(this) {
            canonicalNameByAlias?.let { return it }
            val loaded = runCatching {
                val jsonString = context.assets.open(AssetPaths.POKEMON_NAMES).bufferedReader().use { it.readText() }
                val array = JSONArray(jsonString)
                buildMap {
                    for (index in 0 until array.length()) {
                        val obj = array.getJSONObject(index)
                        val canonicalName = obj.optString("name").trim()
                        if (canonicalName.isBlank()) continue
                        put(normalizeKey(canonicalName), canonicalName)
                        val aliases = obj.optJSONArray("aliases")
                        if (aliases != null) {
                            for (aliasIndex in 0 until aliases.length()) {
                                val alias = aliases.optString(aliasIndex).trim()
                                if (alias.isNotBlank()) {
                                    put(normalizeKey(alias), canonicalName)
                                }
                            }
                        }
                    }
                }
            }.getOrElse { emptyMap() }
            canonicalNameByAlias = loaded
            return loaded
        }
    }

    private fun normalizeKey(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .uppercase(Locale.US)
            .trim()
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
        league: PvpLeague,
        options: PvpCalculationOptions
    ): String {
        val includeBestBuddy = options.bestBuddy
        val levelCap = options.effectiveMaxLevel.toInt().toString()
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

    private fun selectBestFamilyOption(options: List<PvpLeagueRankInfo>): PvpLeagueRankInfo? =
        options.filter { it.eligible && it.rank != null }
            .minWithOrNull(pvpRankComparator(rank = { it.rank }, name = { it.pokemonName }))
            ?: options.firstOrNull()
    private fun formatLevel(level: Double): String {
        return if (level % 1.0 == 0.0) {
            level.toInt().toString()
        } else {
            level.toString().replace(".", ",")
        }
    }
}
