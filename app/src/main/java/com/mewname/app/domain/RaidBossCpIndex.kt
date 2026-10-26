package com.mewname.app.domain

import android.content.Context
import org.json.JSONObject

/** Calculated references only: this index never controls raid availability. */
internal object RaidBossCpIndex {
    @Volatile private var cached: Map<Int, List<RaidChoice>>? = null
    fun load(context: Context): Map<Int, List<RaidChoice>> = cached ?: synchronized(this) {
        cached ?: parse(context.assets.open("raids/boss_cp.json").bufferedReader().use { it.readText() })
            .also { cached = it }
    }
    internal fun parse(text: String): Map<Int, List<RaidChoice>> {
        val json = JSONObject(text)
        require(json.getInt("schema") == 1)
        val result = mutableMapOf<Int, MutableList<RaidChoice>>()
        val pokemon = json.getJSONArray("pokemon")
        for (i in 0 until pokemon.length()) {
            val p = pokemon.getJSONObject(i)
            val values = p.getJSONObject("bossCp")
            for (tier in values.keys()) result.getOrPut(values.getInt(tier)) { mutableListOf() }
                .add(RaidChoice(p.getString("id"), tier))
        }
        return result.mapValues { it.value.distinct() }
    }
}
