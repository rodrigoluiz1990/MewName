package com.mewname.app.domain

/** The appraisal describes the actual species, even when the title is an evolution nickname. */
internal object AppraisalFooterReader {
    // Unicode case folding is required: Java's (?i) alone does not equate É and é.
    // Text follows appraise_v2_caught in the bundled PT/EN/ES game catalogs.
    private val patterns = listOf(
        Regex("""(?iu)O\s+POK[ÉE]MON\s+([\p{L}' .-]+?)\s+FOI\s+(?:PEGO|CAPTURADO)\s+EM"""),
        Regex("""(?iu)(?:THIS|THE)\s+(?:POK[ÉE]MON\s+)?([\p{L}' .-]+?)\s+WAS\s+CAUGHT"""),
        Regex("""(?iu)ESTE\s+([\p{L}' .-]+?)\s+FUE\s+ATRAPADO""")
    )
    fun candidate(text: String): String? {
        val joined = text.replace(Regex("\\s+"), " ")
        return patterns.firstNotNullOfOrNull { it.find(joined)?.groupValues?.get(1)?.trim() }
    }
    fun isAppraisal(text: String): Boolean = candidate(text) != null ||
        (Regex("(?i)\\b(?:ATAQUE|ATTACK)\\b").containsMatchIn(text) &&
            Regex("(?i)\\b(?:DEFESA|DEFENSE|DEFENSA)\\b").containsMatchIn(text) &&
            Regex("(?i)\\b(?:CP|PC)\\s*\\d+").containsMatchIn(text))
}