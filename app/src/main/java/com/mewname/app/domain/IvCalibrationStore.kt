package com.mewname.app.domain

import android.content.Context

data class IvCalibration(
    val attackOffset: Float = 0f,
    val defenseOffset: Float = 0f,
    val staminaOffset: Float = 0f
) {
    fun adjustedAttack(ratio: Float?): Float? = ratio?.plus(attackOffset)?.coerceIn(0f, 1f)
    fun adjustedDefense(ratio: Float?): Float? = ratio?.plus(defenseOffset)?.coerceIn(0f, 1f)
    fun adjustedStamina(ratio: Float?): Float? = ratio?.plus(staminaOffset)?.coerceIn(0f, 1f)
}

object IvCalibrationStore {
    private const val PREFS = "mewname_prefs"
    private const val KEY_ATTACK = "iv_calibration_attack"
    private const val KEY_DEFENSE = "iv_calibration_defense"
    private const val KEY_STAMINA = "iv_calibration_stamina"

    fun load(context: Context): IvCalibration {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return IvCalibration(
            attackOffset = prefs.getFloat(KEY_ATTACK, 0f),
            defenseOffset = prefs.getFloat(KEY_DEFENSE, 0f),
            staminaOffset = prefs.getFloat(KEY_STAMINA, 0f)
        )
    }

    fun save(context: Context, calibration: IvCalibration) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_ATTACK, calibration.attackOffset)
            .putFloat(KEY_DEFENSE, calibration.defenseOffset)
            .putFloat(KEY_STAMINA, calibration.staminaOffset)
            .apply()
    }
}
