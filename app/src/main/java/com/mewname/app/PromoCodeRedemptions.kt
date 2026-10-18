package com.mewname.app

import android.content.Context
import java.util.Locale

/** Local, manual confirmation; opening the redemption website is not proof of redemption. */
internal class PromoCodeRedemptions(context: Context) {
    val preferences = context.getSharedPreferences("promo_code_redemptions", Context.MODE_PRIVATE)
    fun redeemedCodes(): Set<String> = preferences.getStringSet("redeemed", emptySet()).orEmpty().toSet()
    fun isRedeemed(code: String, codes: Set<String> = redeemedCodes()) = key(code) in codes
    fun setRedeemed(code: String, redeemed: Boolean) {
        val key = key(code)
        if (key.isBlank()) return
        val codes = redeemedCodes().toMutableSet()
        if (redeemed) codes.add(key) else codes.remove(key)
        preferences.edit().putStringSet("redeemed", codes).apply()
    }
    private fun key(code: String) = code.trim().uppercase(Locale.ROOT)
}