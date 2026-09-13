package com.mewname.app.domain

/** Source spelling variants, also applied to the English templates before binding parameters. */
internal fun researchSourceKey(text: String): String = catalogText(text)
    .replace(Regex("\\b(?:a|an)\\b"), "1")
    .replace(Regex("\\bthree star\\b"), "3 star")
    .replace(Regex("\\bwhile in a party\\b"), "while in 1 party")

/** Tolerates grammar and small OCR errors, never changes a task's numbers or conditions. */
internal object ResearchTaskComparison {
    private val alternatives = mapOf(
        "capture" to "capturar", "captura" to "capturar", "pegue" to "capturar", "pegar" to "capturar",
        "choque" to "chocar", "eclodir" to "chocar", "ecloda" to "chocar",
        "fortaleca" to "fortalecer", "fortalece" to "fortalecer",
        "faca" to "fazer", "realize" to "fazer", "realizar" to "fazer",
        "gire" to "girar", "gira" to "girar", "envie" to "enviar", "manda" to "enviar",
        "evolua" to "evoluir", "troque" to "trocar", "derrote" to "derrotar",
        "ganhe" to "ganhar", "venca" to "vencer", "batalhe" to "batalhar",
        "pokemons" to "pokemon", "ovo" to "ovos", "vez" to "vezes",
        "arremesso" to "arremessos", "lancamento" to "arremessos", "lancamentos" to "arremessos",
        "consecutivos" to "seguidos", "consecutivo" to "seguidos", "seguidas" to "seguidos",
        "consecutivas" to "seguidos", "seguida" to "seguidos",
        "reide" to "raid", "reides" to "raid", "raids" to "raid"
    )
    // Only grammatical words may disappear. Keep with/without, or/and and every restriction.
    private val grammar = setOf("os", "a", "as", "seu", "seus", "sua", "suas", "de", "do", "da", "dos", "das", "tipo", "tipos")
    private val typoWords = setOf("capturar", "fortalecer", "pokemon", "chocar", "arremessos",
        "presentes", "pokestops", "pokeparadas", "evoluir", "derrotar", "batalhar",
        "catch", "pokemon", "throws", "evolve", "power", "captura", "eclosiona", "lanzamientos")
    private fun tokens(text: String): List<String> = catalogText(text)
        .replace(Regex("\\b(?:um|uma|un|una|a|an) (?=(?:ovo|egg|huevo|batalha|battle|combate|raid|reide|pokemon|arremesso|throw|snapshot)\\b)"), "1 ")
        .replace("em sequencia", "seguidos").replace("em seguida", "seguidos")
        .split(' ').filter { it.isNotBlank() && it !in grammar }
        .map { alternatives[it] ?: it }

    fun continuesTask(line: String): Boolean = Regex(
        "^(?:com|sem|with|without|con|sin|em|in|en|while|enquanto|durante|of|de|do|da|or|ou|e|and|y|o|seguidos|consecutivos|consecutivas|tipo|tipos|weather|clima|boost|boosted)\\b"
    ).containsMatchIn(catalogText(line))
    fun prepare(text: String): List<String> = tokens(text)
    fun matches(expected: String, observed: String): Boolean = matchesPrepared(prepare(expected), prepare(observed))
    fun matchesPrepared(left: List<String>, right: List<String>): Boolean {
        if (left.size != right.size || left.isEmpty()) return false
        // Check counts independently; no correction of 1/7, 3/5 or missing digits.
        if (left.filter { it.all(Char::isDigit) } !=
            right.filter { it.all(Char::isDigit) }) return false
        var corrections = 0
        for (i in left.indices) {
            if (left[i] == right[i]) continue
            if (left[i] !in typoWords || left[i].length < 5 || ++corrections > 1 || !oneEdit(left[i], right[i].replace("rn", "m"))) return false
        }
        return true
    }
    private fun oneEdit(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0; var j = 0; var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            when { a.length > b.length -> i++; b.length > a.length -> j++; else -> { i++; j++ } }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }
}