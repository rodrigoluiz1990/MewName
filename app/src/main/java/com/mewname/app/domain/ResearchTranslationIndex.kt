package com.mewname.app.domain

/** Normalize each source once; skip unrelated verbs and reuse translations across scans. */
internal class ResearchTranslationIndex(templates: List<ResearchTemplate>) {
    private val groups = templates.groupBy {
        if (it.english.trimStart().startsWith("{")) "" else researchSourceKey(it.english).substringBefore(' ')
    }
    private val cache = object : LinkedHashMap<String, List<String>>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?) = size > 512
    }
    @Synchronized
    fun variants(task: String): List<String> = cache.getOrPut(task) {
        val key = researchSourceKey(task)
        (groups[key.substringBefore(' ')].orEmpty() + groups[""].orEmpty())
            .mapNotNull { it.translateKey(key) }.distinct()
    }
}