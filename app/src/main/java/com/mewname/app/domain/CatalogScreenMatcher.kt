package com.mewname.app.domain

import java.text.Normalizer
import java.util.Locale

internal fun catalogText(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    .replace(Regex("[^a-z0-9]+"), " ").trim()

internal data class CatalogCapture(val section: LeekSection, val text: String)

/** Parameters must be numeric or known local game terms; arbitrary text is never substituted. */
data class ResearchTemplate(val english: String, val localized: String, val terms: Map<String, String> = emptyMap()) {
    private val placeholders = Regex("\\{(\\d+)\\}")
    private val ids = placeholders.findAll(english).map { it.groupValues[1] }.toList()
    private val pattern by lazy {
        val marked = placeholders.replace(english) { " qplaceholder${it.groupValues[1]}q " }
        val parts = researchSourceKey(marked).split(Regex("qplaceholder\\d+q"))
        Regex(parts.joinToString("([a-z0-9 ]+?)") { Regex.escape(it) })
    }
    fun translate(task: String): String? = translateKey(researchSourceKey(task))
    internal fun translateKey(key: String): String? {
        val match = pattern.matchEntire(key) ?: return null
        val values = mutableMapOf<String, String>()
        ids.forEachIndexed { index, id ->
            val raw = match.groupValues[index + 1]
            val translated = if (raw.all(Char::isDigit)) raw else terms[raw] ?: return null
            if (id in values && values[id] != translated) return null
            values[id] = translated
        }
        if (placeholders.findAll(localized).any { it.groupValues[1] !in values }) return null
        return placeholders.replace(localized) { values.getValue(it.groupValues[1]) }
    }
}

internal object CatalogScreenMatcher {
    private fun containsPhrase(text: String, phrase: String): Boolean {
        val key = catalogText(phrase)
        return key.length >= 5 && " ${catalogText(text)} ".contains(" $key ")
    }
    fun distances(text: String): Set<Int> = Regex("/\\s*(2|5|7|10|12)\\s*km\\b", RegexOption.IGNORE_CASE)
        .findAll(text).map { it.groupValues[1].toInt() }.toSet()

    fun detect(text: String, quotes: List<RocketQuote> = emptyList()): CatalogCapture? {
        val key = " ${catalogText(text)} "
        val research = listOf("pesquisa", "research", "investigacion").any { key.contains(" $it ") } &&
            listOf("hoje", "today", "hoy", "pesquisas de campo", "field research", "investigaciones de campo")
                .any { key.contains(" $it ") }
        if (research) return CatalogCapture(LeekSection.RESEARCH, text)
        val eggs = listOf("ovos", "eggs", "huevos").any { key.contains(" $it ") } &&
            listOf("tags", "etiquetas", "estoque bonus", "bonus storage", "almacenamiento adicional")
                .any { key.contains(" $it ") } && distances(text).isNotEmpty()
        if (eggs) return CatalogCapture(LeekSection.EGGS, text)
        if (quotes.any { containsPhrase(text, it.localized) || containsPhrase(text, it.english) })
            return CatalogCapture(LeekSection.ROCKET, text)
        return null
    }

    fun matching(capture: CatalogCapture, catalog: LeekCatalog, quotes: List<RocketQuote> = emptyList(),
        templates: List<ResearchTemplate> = emptyList(), now: Long = System.currentTimeMillis(),
        translations: ((String) -> List<String>)? = null): List<LeekEntry> {
        return when (capture.section) {
            LeekSection.EGGS -> {
                val distances = distances(capture.text)
                catalog.entries.filter { Regex("^(\\d+)\\s*km", RegexOption.IGNORE_CASE)
                    .find(it.title)?.groupValues?.get(1)?.toIntOrNull() in distances }
            }
            LeekSection.ROCKET -> catalog.entries.filter { entry ->
                containsPhrase(capture.text, entry.description) || quotes.any {
                    catalogText(it.english) == catalogText(entry.description) && containsPhrase(capture.text, it.localized)
                }
            }
            LeekSection.RESEARCH -> {
                // Keep line boundaries: a shorter task must not match a more specific task on that line.
                val lines = capture.text.lines().map(::catalogText).filter { it.isNotBlank() }
                val windows = lines.indices.flatMap { start -> (1..3).mapNotNull { count ->
                    if (start + count <= lines.size &&
                        (start + count == lines.size || !ResearchTaskComparison.continuesTask(lines[start + count])))
                        lines.subList(start, start + count).joinToString(" ") else null
                } }.toSet()
                val translate = translations ?: ResearchTranslationIndex(templates)::variants
                val preparedWindows = windows.map(ResearchTaskComparison::prepare).groupBy { it.size }
                catalog.entries.mapNotNull { entry ->
                    if (entry.expires != null && entry.expires <= now) return@mapNotNull null
                    val variants = listOf(entry.title) + translate(entry.title)
                    val matched = variants.firstOrNull { variant ->
                        val prepared = ResearchTaskComparison.prepare(variant)
                        preparedWindows[prepared.size].orEmpty().any { ResearchTaskComparison.matchesPrepared(prepared, it) }
                    } ?: return@mapNotNull null
                    entry.copy(title = matched)
                }
            }
            LeekSection.CODES -> emptyList()
        }
    }
}