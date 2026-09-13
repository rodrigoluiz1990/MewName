package com.mewname.app.domain

/** Source: combat_*quote resource IDs in bundled text_en/text_ptbr/text_es game catalogs. */
data class RocketQuote(val english: String, val localized: String, val speaker: String?)

private fun quoteKey(text: String) = text.lowercase(java.util.Locale.ROOT)
    .replace(Regex("[^\\p{L}\\p{N}]+"), "")

fun translatedRocketQuote(english: String, trainer: String, quotes: List<RocketQuote>): String? {
    val key = quoteKey(english)
    val matches = quotes.filter { quoteKey(it.english) == key }
    val speaker = when {
        trainer.contains("Female", true) -> "Female"
        trainer.contains("Male", true) -> "Male"
        else -> null
    }
    return (matches.firstOrNull { it.speaker == speaker } ?: matches.firstOrNull())?.localized
}
