package com.mewname.app.domain

/** Source: combat_*quote resource IDs in bundled text_en/text_ptbr/text_es game catalogs. */
data class RocketQuote(val english: String, val localized: String, val speaker: String?) {
    // Screen detection compares every quote on each read; normalize each phrase once.
    internal val englishKey: String by lazy(LazyThreadSafetyMode.PUBLICATION) { catalogText(english) }
    internal val localizedKey: String by lazy(LazyThreadSafetyMode.PUBLICATION) { catalogText(localized) }
}

private val QUOTE_KEY_SEPARATORS = Regex("[^\\p{L}\\p{N}]+")

private fun quoteKey(text: String) = text.lowercase(java.util.Locale.ROOT)
    .replace(QUOTE_KEY_SEPARATORS, "")

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
