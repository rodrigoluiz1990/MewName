package com.mewname.app.domain

import android.content.Context

data class PvpCalculationOptions(val maxLevel: Int = 50, val bestBuddy: Boolean = false) {
    init { require(maxLevel == 40 || maxLevel == 50) }
    val effectiveMaxLevel: Double get() = maxLevel.toDouble() + if (bestBuddy) 1 else 0
}

object PvpCalculationSettings {
    fun preferences(context: Context) = context.getSharedPreferences("pvp_calculation", Context.MODE_PRIVATE)
    fun read(context: Context): PvpCalculationOptions {
        val prefs = preferences(context)
        return PvpCalculationOptions(if (prefs.getInt("max_level", 50) == 40) 40 else 50,
            prefs.getBoolean("best_buddy", false))
    }
    fun save(context: Context, options: PvpCalculationOptions) {
        preferences(context).edit().putInt("max_level", options.maxLevel)
            .putBoolean("best_buddy", options.bestBuddy).apply()
    }
}
