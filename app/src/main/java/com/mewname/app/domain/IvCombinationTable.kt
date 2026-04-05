package com.mewname.app.domain

import android.content.Context
import org.json.JSONArray
import kotlin.math.roundToInt

data class IvCombination(
    val attack: Int,
    val defense: Int,
    val stamina: Int
) {
    val key: String = "$attack/$defense/$stamina"
    val ivPercent: Int = ((attack + defense + stamina) * 100f / 45f).roundToInt()
}

object IvCombinationTable {
    // Attack stays close to the nominal segmented scale.
    private val attackFillRatios = floatArrayOf(
        0.00f, // 0
        0.05f, // 1
        0.10f, // 2
        0.16f, // 3
        0.23f, // 4
        0.29f, // 5
        0.36f, // 6
        0.43f, // 7
        0.50f, // 8
        0.56f, // 9
        0.62f, // 10
        0.68f, // 11
        0.74f, // 12
        0.80f, // 13
        0.86f, // 14
        0.92f  // 15
    )

    // Defense is visually more compressed in the appraisal card screenshots.
    private val defenseFillRatios = floatArrayOf(
        0.00f, // 0
        0.04f, // 1
        0.08f, // 2
        0.12f, // 3
        0.16f, // 4
        0.20f, // 5
        0.25f, // 6
        0.30f, // 7
        0.34f, // 8
        0.368f, // 9
        0.453f, // 10
        0.54f, // 11
        0.63f, // 12
        0.72f, // 13
        0.80f, // 14
        0.88f  // 15
    )

    // HP/PS also compresses, but less aggressively than Defense.
    private val staminaFillRatios = floatArrayOf(
        0.00f, // 0
        0.05f, // 1
        0.10f, // 2
        0.15f, // 3
        0.19f, // 4
        0.27f, // 5
        0.35f, // 6
        0.43f, // 7
        0.50f, // 8
        0.57f, // 9
        0.64f, // 10
        0.71f, // 11
        0.77f, // 12
        0.82f, // 13
        0.86f, // 14
        0.90f  // 15
    )

    @Volatile
    private var isLoadedFromAsset = false

    private var combinations: List<IvCombination> = generateDefaultCombinations()
    private var byKey = combinations.associateBy { it.key }
    private var byPercent = combinations.groupBy { it.ivPercent }

    fun ensureLoaded(context: Context) {
        if (isLoadedFromAsset) return
        synchronized(this) {
            if (isLoadedFromAsset) return
            val loaded = loadFromAsset(context) ?: return
            combinations = loaded
            byKey = loaded.associateBy { it.key }
            byPercent = loaded.groupBy { it.ivPercent }
            isLoadedFromAsset = true
        }
    }

    fun combinationsForPercent(ivPercent: Int): List<IvCombination> {
        return byPercent[ivPercent] ?: emptyList()
    }

    fun fromValues(attack: Int, defense: Int, stamina: Int): IvCombination? {
        return byKey["$attack/$defense/$stamina"]
    }

    fun nearestFromRatios(
        attackRatio: Float?,
        defenseRatio: Float?,
        staminaRatio: Float?,
        ivPercentHint: Int? = null
    ): IvCombination? {
        if (attackRatio == null || defenseRatio == null || staminaRatio == null) return null

        val candidates = ivPercentHint?.let { hint ->
            combinationsForPercent(hint).takeIf { it.isNotEmpty() }
        } ?: combinations

        return candidates.minByOrNull { combo ->
            squaredDistance(attackRatio, calibratedRatioFor(combo.attack, IvBarType.ATTACK)) +
                squaredDistance(defenseRatio, calibratedRatioFor(combo.defense, IvBarType.DEFENSE)) +
                squaredDistance(staminaRatio, calibratedRatioFor(combo.stamina, IvBarType.STAMINA))
        }
    }

    fun nearestValueFromRatio(ratio: Float?, barType: IvBarType = IvBarType.ATTACK): Int? {
        if (ratio == null) return null
        return ratiosFor(barType)
            .withIndex()
            .minByOrNull { (_, calibratedRatio) -> squaredDistance(ratio, calibratedRatio) }
            ?.index
    }

    private fun generateDefaultCombinations(): List<IvCombination> = buildList {
        for (attack in 0..15) {
            for (defense in 0..15) {
                for (stamina in 0..15) {
                    add(IvCombination(attack, defense, stamina))
                }
            }
        }
    }

    private fun loadFromAsset(context: Context): List<IvCombination>? {
        return try {
            val jsonString = context.assets.open(AssetPaths.PVP_IV_COMBINATIONS)
                .bufferedReader()
                .use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            buildList(jsonArray.length()) {
                for (index in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(index)
                    add(
                        IvCombination(
                            attack = obj.getInt("attack"),
                            defense = obj.getInt("defense"),
                            stamina = obj.getInt("stamina")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun calibratedRatioFor(iv: Int, barType: IvBarType): Float {
        return ratiosFor(barType)[iv.coerceIn(0, 15)]
    }

    private fun ratiosFor(barType: IvBarType): FloatArray {
        return when (barType) {
            IvBarType.ATTACK -> attackFillRatios
            IvBarType.DEFENSE -> defenseFillRatios
            IvBarType.STAMINA -> staminaFillRatios
        }
    }

    private fun squaredDistance(a: Float, b: Float): Float {
        val diff = a - b
        return diff * diff
    }
}

enum class IvBarType {
    ATTACK,
    DEFENSE,
    STAMINA
}
