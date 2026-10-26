package com.mewname.app.domain

import android.content.Context
import com.mewname.app.ocr.OcrResult

/** Routes one OCR result; readers keep their own parsing and rendering contracts. */
internal object BubbleScreenRouter {
    sealed interface Target {
        data object Trainer : Target
        data class Encounter(val reading: EncounterReading) : Target
        data object Pokemon : Target
        data class Catalog(val capture: CatalogCapture) : Target
        data class Battle(val advice: BattleAdvice) : Target
        data class Filters(val screen: FilterScreen) : Target
    }
    fun route(context: Context, result: OcrResult, onTiming: (String, Long) -> Unit = { _, _ -> }): Target {
        val bitmap = result.bitmap
        val positionedLines = if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
            emptyList()
        } else {
            result.blocks.flatMap { block -> block.lines }.mapNotNull { line ->
                line.boundingBox?.let { box ->
                    BattleOcrLine(
                        text = line.text,
                        left = box.left.toFloat() / bitmap.width,
                        top = box.top.toFloat() / bitmap.height,
                        right = box.right.toFloat() / bitmap.width,
                        bottom = box.bottom.toFloat() / bitmap.height
                    )
                }
            }
        }
        return route(context, result.fullText, positionedLines, { RaidLevelIconDetector.detect(result) }, onTiming)
    }

    fun route(context: Context, text: String): Target = route(context, text, emptyList(), { null })

    private fun route(context: Context, text: String, positionedLines: List<BattleOcrLine>,
        raidLevel: () -> Int?, onTiming: (String, Long) -> Unit = { _, _ -> }): Target {
        fun <T> measured(label: String, action: () -> T): T {
            val start = android.os.SystemClock.elapsedRealtime()
            try { return action() } finally { onTiming(label, android.os.SystemClock.elapsedRealtime() - start) }
        }
        if (measured("perfil") { TrainerProfileParser.isTrainerScreen(text) }) return Target.Trainer
        if (measured("avaliacao") { AppraisalFooterReader.isAppraisal(text) }) return Target.Pokemon
        measured("catalogo") { CatalogScreenMatcher.detect(text) }?.let { return Target.Catalog(it) }
        measured("filtros") { FilterScreenDetector.detect(text, positionedLines) }?.let { return Target.Filters(it) }
        measured("captura") { EncounterScreenReader.detect(positionedLines, text) }?.let { return Target.Encounter(it) }
        // Defer pixel analysis until the text actually describes a battle.
        val advice = measured("identificacao batalha") {
            BattleAdvisor.adviceForRaw(context, text, positionedLines, identityOnly = true)
        }
        if (advice == null) {
            val quotes = measured("falas Rocket") { AppLanguage.entries.flatMap { GameTextRepository.rocketQuotes(context, it) } }
            measured("catalogo Rocket") { CatalogScreenMatcher.detect(text, quotes) }?.let { return Target.Catalog(it) }
            return Target.Pokemon
        }
        if (com.mewname.app.BuildConfig.DEBUG && advice.mode == BattleMode.RAID) {
            val band = positionedLines.filter { (it.top + it.bottom) / 2f in .16f.. .39f }
            android.util.Log.d("RaidReadTiming", "nameBand=" + band.joinToString(" | ") { it.text })
        }
        return Target.Battle(if (advice.mode == BattleMode.RAID) {
            val level = measured("simbolos nivel") { raidLevel() }
            if (com.mewname.app.BuildConfig.DEBUG)
                android.util.Log.d("RaidReadTiming", "detectedLevel=$level name=${advice.bossName}")
            var cpCandidates = emptyList<RaidChoice>()
            val recovered = if (advice.bossName == null) measured("nome parcial catalogo") {
                val repository = RaidCounterRepository(context.applicationContext)
                val metadata = repository.metadata()
                val catalog = runCatching { repository.catalog() }.getOrDefault(emptyList())
                cpCandidates = RaidBossResolver.bossCpCandidates(positionedLines, text, metadata, catalog, level,
                    RaidBossCpIndex.load(context))
                cpCandidates.singleOrNull()
                    ?: RaidBossResolver.resolveOccludedName(positionedLines, metadata,
                        cpCandidates.ifEmpty { catalog }, level)?.takeIf { cpCandidates.isEmpty() || it in cpCandidates }?.also {
                        if (com.mewname.app.BuildConfig.DEBUG)
                            android.util.Log.d("RaidReadTiming", "bossCp recovered=$it")
                    }
            } else null
            advice.copy(bossName = advice.bossName ?: recovered?.id, raidLevel = level,
                raidBossCandidates = cpCandidates.map { it.id to it.tier })
        } else advice)
    }
}
