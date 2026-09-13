package com.mewname.app

import android.app.Instrumentation
import android.os.Bundle
import com.mewname.app.domain.*

/** Runs on Android's regex implementation, not the host JVM used by local unit tests. */
class ResearchRuntimeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            val started = android.os.SystemClock.elapsedRealtime()
            sendStatus(1, Bundle().apply { putString("stream", "Loading local templates…\n") })
            val templates = GameTextRepository.researchTemplates(targetContext, AppLanguage.PT_BR)
            sendStatus(1, Bundle().apply { putString("stream", "Templates loaded in ${android.os.SystemClock.elapsedRealtime() - started}ms\n") })
            val titles = listOf("Catch 10 Bug- or Flying-type Pokémon", "Catch 10 Flamigo", "Power up Pokémon 5 times", "Hatch an Egg")
            val translated = titles.associateWith { title -> templates.firstNotNullOfOrNull { it.translate(title) } }
            result.putString("stream", "Templates=${templates.size}; translations=$translated\n")
            check(translated.values.none { it == null }) { "Missing Android translations: $translated" }
            val data = LeekDuckRepository(targetContext).cached(LeekSection.RESEARCH)
                ?: error("No saved research catalog on test device")
            val capture = CatalogCapture(LeekSection.RESEARCH,
                "PASSE GO\nPESQUISA\nHOJE\nESPECIAL\nFortalecer seus Pokémon 5 vezes.\n0\nChocar 1 Ovo.\nEVENTO\nVisite Poképaradas para encontrar mais Pesquisas de Campo!")
            val matchStarted = android.os.SystemClock.elapsedRealtime()
            val indexes = AppLanguage.entries.map { GameTextRepository.researchIndex(targetContext, it) }
            val matches = CatalogScreenMatcher.matching(capture, data, translations = { title -> indexes.flatMap { it.variants(title) } })
            sendStatus(1, Bundle().apply { putString("stream", "Full matching in ${android.os.SystemClock.elapsedRealtime() - matchStarted}ms; total=${android.os.SystemClock.elapsedRealtime() - started}ms\n") })
            val warmStarted = android.os.SystemClock.elapsedRealtime()
            check(CatalogScreenMatcher.matching(capture, data, translations = { title -> indexes.flatMap { it.variants(title) } }) == matches)
            sendStatus(1, Bundle().apply { putString("stream", "Warm matching in ${android.os.SystemClock.elapsedRealtime() - warmStarted}ms\n") })
            check(matches.any { it.title.contains("5 vezes") }) { "Power up task not matched" }
            check(matches.any { it.title.contains("1 Ovo") }) { "Hatch task not matched" }
            result.putString("stream", result.getString("stream") + "Matched=${matches.map { it.title }}\nPASS\n")
            finish(RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("stream", result.getString("stream").orEmpty() + failure.stackTraceToString())
            finish(RESULT_CANCELED, result)
        }
    }
    companion object { const val RESULT_OK = -1; const val RESULT_CANCELED = 0 }
}