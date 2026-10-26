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

    internal fun levelMultipliers(): List<Pair<Double, Double>> = cpmTable.mapIndexed { index, value -> (1.0 + index * .5) to value }

    data class StatProduct(val atk: Int, val def: Int, val sta: Int, val product: Double, val cp: Int, val level: Double)
    data class LevelEstimate(val pokemonName: String, val level: Double, val cpDistance: Int)
    data class HpLevelEstimate(val pokemonName: String, val level: Double, val hpDistance: Int)

    private class RankTable(
        val byIv: Array<StatProduct?>,
        val productsDescending: DoubleArray
    ) {
        // Value equality over every spread and product, as the former data class over maps provided.
        override fun equals(other: Any?): Boolean = other is RankTable &&
            byIv.contentEquals(other.byIv) && productsDescending.contentEquals(other.productsDescending)

        override fun hashCode(): Int = 31 * byIv.contentHashCode() + productsDescending.contentHashCode()
    }

    // Squares computed exactly as calculateCp does, so cached values match it bit for bit.
    private val cpmSquared = DoubleArray(cpmTable.size) { cpmTable[it].pow(2.0) }

    @Volatile
    private var baseStatsRoot: JSONObject? = null
    @Volatile
    private var canonicalNameByAlias: Map<String, String>? = null
    // Nine species across all four leagues (temporary capacity for the Eevee family). Each table contains at most 4,096 IVs.
    // Access order requires locking reads as well as writes (see getRankTable).
    private val rankTableCache = object : LinkedHashMap<String, RankTable>(36, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RankTable>?): Boolean = size > 36
    }

    data class CpIvEvidence(val matches: Int, val attack: Int?, val defense: Int?, val stamina: Int?, val level: Double?)

    /** Exact CP/maximum HP constraints. Ambiguous spreads must never become a guessed IV. */
    fun resolveIvEvidence(context: Context, pokemonName: String, cp: Int, maxHp: Int?,
        knownAttack: Int? = null, knownDefense: Int? = null, knownStamina: Int? = null,
        knownLevel: Double? = null, allowedLevels: Set<Double>? = null): CpIvEvidence {
        val empty = CpIvEvidence(0, null, null, null, null)
        val knownIvCount = listOf(knownAttack, knownDefense, knownStamina).count { it != null }
        if (cp < 10 || (maxHp == null && knownLevel == null && knownIvCount < 2) || (maxHp != null && maxHp < 10)) return empty
        if (listOf(knownAttack, knownDefense, knownStamina).any { it != null && it !in 0..15 }) return empty
        if (knownLevel != null && (knownLevel !in 1.0..51.0 || knownLevel * 2 != kotlin.math.floor(knownLevel * 2))) return empty
        val base = loadBaseStats(context, pokemonName) ?: return empty
        val a = base.getInt("attack"); val d = base.getInt("defense"); val h = base.getInt("stamina")
        var matches = 0
        var first: Triple<Int, Int, Int>? = null
        var sameAttack = true
        var sameDefense = true
        var sameStamina = true
        var firstLevel: Double? = null
        var sameLevel = true
        for (i in cpmTable.indices) {
            if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException("IV resolution cancelled")
            val level = 1.0 + i * 0.5
            if (knownLevel != null && level != knownLevel) continue
            if (allowedLevels != null && level !in allowedLevels) continue
            val cpm = cpmTable[i]
            if (cp !in calculateCp(a, d, h, cpm)..calculateCp(a + 15, d + 15, h + 15, cpm)) continue
            for (sta in (knownStamina?.let { it..it } ?: 0..15)) {
                if (maxHp != null && floor((h + sta) * cpm).toInt().coerceAtLeast(10) != maxHp) continue
                for (atk in (knownAttack?.let { it..it } ?: 0..15)) for (def in (knownDefense?.let { it..it } ?: 0..15)) {
                    if (calculateCp(a + atk, d + def, h + sta, cpm) != cp) continue
                    val iv = Triple(atk, def, sta)
                    if (matches == 0) { first = iv; firstLevel = level }
                    else {
                        if (first?.first != atk) sameAttack = false
                        if (first?.second != def) sameDefense = false
                        if (first?.third != sta) sameStamina = false
                        if (firstLevel != level) sameLevel = false
                    }
                    matches++
                }
            }
        }
        return CpIvEvidence(matches, first?.first?.takeIf { sameAttack }, first?.second?.takeIf { sameDefense },
            first?.third?.takeIf { sameStamina }, firstLevel?.takeIf { sameLevel })
    }

    /** All exact CP matches, not a probability or a confirmed appraisal. */
    fun encounterPossibilities(context: Context, name: String, cp: Int, level: Double? = null): List<StatProduct> {
        if (cp < 10 || (level != null && (level !in 1.0..51.0 || level * 2 != floor(level * 2)))) return emptyList()
        val base = loadBaseStats(context, name) ?: return emptyList()
        val a = base.getInt("attack"); val d = base.getInt("defense"); val h = base.getInt("stamina")
        val results = mutableListOf<StatProduct>()
        for (i in cpmTable.indices) {
            if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
            val lv = 1.0 + i * 0.5
            if (level != null && lv != level) continue
            val cpm = cpmTable[i]
            if (cp !in calculateCp(a, d, h, cpm)..calculateCp(a + 15, d + 15, h + 15, cpm)) continue
            for (atk in 0..15) for (def in 0..15) for (sta in 0..15) {
                if (calculateCp(a + atk, d + def, h + sta, cpm) == cp)
                    results += StatProduct(atk, def, sta, 0.0, cp, lv)
            }
        }
        return results.sortedWith(compareByDescending<StatProduct> { it.atk + it.def + it.sta }.thenBy { it.level })
    }

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
        sta: Int,
        observedCp: Int? = null,
        atk: Int? = null,
        def: Int? = null
    ): HpLevelEstimate? {
        val matches = pokemonNames.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            .flatMap { name ->
                val stats = loadBaseStats(context, name) ?: return@flatMap emptyList()
                cpmTable.mapIndexedNotNull { index, cpm ->
                    val hp = floor((stats.getInt("stamina") + sta) * cpm).toInt().coerceAtLeast(10)
                    if (hp != observedHp) return@mapIndexedNotNull null
                    // A visible CP can disambiguate HP only with all three IVs known.
                    if (observedCp != null && atk != null && def != null &&
                        calculateCp(stats.getInt("attack") + atk, stats.getInt("defense") + def,
                            stats.getInt("stamina") + sta, cpm) != observedCp) return@mapIndexedNotNull null
                    HpLevelEstimate(name, 1.0 + index * 0.5, 0)
                }
            }
        // HP rounding often gives the same value at adjacent half-levels.
        // Never choose the highest tied level without additional evidence.
        if (matches.map { it.level }.distinct().size != 1) return null
        return matches.firstOrNull()
    }

    /** Infer only a unique level supported by exact HP, known IVs and every visible CP fragment. */
    fun estimateLevelFromPartialCp(
        context: Context, pokemonName: String, observedHp: Int,
        atk: Int, def: Int, sta: Int, fragments: List<String>
    ): HpLevelEstimate? {
        if (listOf(atk, def, sta).any { it !in 0..15 } || observedHp <= 0 || fragments.isEmpty() ||
            fragments.any { !it.matches(Regex("[0-9]{2,5}")) }) return null
        val stats = loadBaseStats(context, pokemonName) ?: return null
        val matches = cpmTable.mapIndexedNotNull { index, cpm ->
            val hp = floor((stats.getInt("stamina") + sta) * cpm).toInt().coerceAtLeast(10)
            if (hp != observedHp) return@mapIndexedNotNull null
            val cp = calculateCp(stats.getInt("attack") + atk, stats.getInt("defense") + def,
                stats.getInt("stamina") + sta, cpm).toString()
            if (!fragments.all { cp.contains(it) }) return@mapIndexedNotNull null
            HpLevelEstimate(pokemonName, 1.0 + index * 0.5, 0)
        }
        return matches.singleOrNull()
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

        // Resolve the source level once; all evolutions keep that level.
        val sourceLevel = currentLevel ?: if (currentPokemonName != null && currentCp != null) {
            estimateLevel(context, currentPokemonName, currentCp, atk, def, sta)
        } else null
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
                    currentLevel = sourceLevel,
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
        val currentBest = if (atk in 0..15 && def in 0..15 && sta in 0..15) rankTable.byIv[ivIndex(atk, def, sta)] else null

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

        val rank = rankInDescendingProducts(rankTable.productsDescending, currentBest.product)
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


    // Entry point checked against the exhaustive level search in PvpRankingOptimizationTest.
    @Suppress("unused")
    private fun getBestStatProduct(base: JSONObject, ivAtk: Int, ivDef: Int, ivSta: Int, cap: Int, maxLevel: Double): StatProduct? =
        getBestStatProduct(base.getInt("attack"), base.getInt("defense"), base.getInt("stamina"), ivAtk, ivDef, ivSta, cap,
            minOf(cpmTable.lastIndex, floor((maxLevel - 1.0) * 2.0).toInt()))

    private fun getBestStatProduct(
        bAtk: Int,
        bDef: Int,
        bSta: Int,
        ivAtk: Int,
        ivDef: Int,
        ivSta: Int,
        cap: Int,
        highestIndex: Int
    ): StatProduct? {
        val atk = bAtk + ivAtk
        val def = bDef + ivDef
        val sta = bSta + ivSta
        // Same operation order as calculateCp, with the level-independent part hoisted out of the search.
        val cpBase = atk * sqrt(def.toDouble()) * sqrt(sta.toDouble())

        // CP and stat product are monotonic for positive stats. Find the last legal
        // half-level instead of evaluating every level for all 4,096 IV spreads.
        var low = 0
        var high = highestIndex
        var bestIndex = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val cp = floor(cpBase * cpmSquared[mid] / 10.0).toInt().coerceAtLeast(10)
            if (cp <= cap) {
                bestIndex = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        if (bestIndex < 0) return null
        val cpm = cpmTable[bestIndex]
        val effectiveHp = floor(sta * cpm).toInt().coerceAtLeast(10)
        // Preserve the original multiplication order and exact ties.
        val statFactors = atk.toDouble() * def * effectiveHp
        return StatProduct(ivAtk, ivDef, ivSta, statFactors * cpmSquared[bestIndex],
            floor(cpBase * cpmSquared[bestIndex] / 10.0).toInt().coerceAtLeast(10), 1.0 + bestIndex * 0.5)
    }

    private fun getRankTable(baseStats: JSONObject, pokemonName: String, league: PvpLeague, options: PvpCalculationOptions): RankTable {
        val cacheKey = "${pokemonName.uppercase()}|${league.name}|${options.effectiveMaxLevel}"
        synchronized(rankTableCache) {
            rankTableCache[cacheKey]?.let { return it }
        }

        val bAtk = baseStats.getInt("attack")
        val bDef = baseStats.getInt("defense")
        val bSta = baseStats.getInt("stamina")
        val byIv = arrayOfNulls<StatProduct>(16 * 16 * 16)
        val products = DoubleArray(byIv.size)
        var productCount = 0
        val cap = leagueCap(league)
        val highestIndex = minOf(cpmTable.lastIndex, floor((options.effectiveMaxLevel - 1.0) * 2.0).toInt())
        for (a in 0..15) {
            if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException("Ranking cancelled")
            for (d in 0..15) {
                for (s in 0..15) {
                    val best = getBestStatProduct(bAtk, bDef, bSta, a, d, s, cap, highestIndex)
                    if (best != null) {
                        byIv[ivIndex(a, d, s)] = best
                        products[productCount++] = best.product
                    }
                }
            }
        }
        val descending = products.copyOf(productCount).apply { sort(); reverse() }

        val table = RankTable(byIv = byIv, productsDescending = descending)
        synchronized(rankTableCache) {
            // Another worker may have filled this key while we calculated outside the lock.
            rankTableCache[cacheKey]?.let { return it }
            rankTableCache[cacheKey] = table
        }
        return table
    }

    private fun ivIndex(atk: Int, def: Int, sta: Int): Int = (atk * 16 + def) * 16 + sta

    private fun calculateCp(atk: Int, def: Int, sta: Int, cpm: Double): Int {
        return floor(atk * sqrt(def.toDouble()) * sqrt(sta.toDouble()) * cpm.pow(2.0) / 10.0).toInt().coerceAtLeast(10)
    }

    private fun loadBaseStats(context: Context, name: String): JSONObject? {
        return try {
            val jsonObject = loadBaseStatsRoot(context)
            val canonicalName = resolveCanonicalPokemonName(context, name)
            jsonObject.optJSONObject(normalizeKey(canonicalName))
                ?: jsonObject.optJSONObject(normalizeKey(name))
        } catch (e: java.util.concurrent.CancellationException) {
            throw e
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
            .replace(COMBINING_DIACRITICS, "")
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

/** First equal product preserves competition ranking for ties. */
internal fun rankInDescendingProducts(products: DoubleArray, product: Double): Int {
    var low = 0
    var high = products.size
    while (low < high) {
        val mid = (low + high) ushr 1
        if (products[mid] > product) low = mid + 1 else high = mid
    }
    return if (low < products.size && products[low] == product) low + 1 else 0
}

internal fun rankInDescendingProducts(products: List<Double>, product: Double): Int {
    var low = 0
    var high = products.size
    while (low < high) {
        val mid = (low + high) ushr 1
        if (products[mid] > product) low = mid + 1 else high = mid
    }
    return if (low < products.size && products[low] == product) low + 1 else 0
}