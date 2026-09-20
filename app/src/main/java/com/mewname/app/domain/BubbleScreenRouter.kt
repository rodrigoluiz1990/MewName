package com.mewname.app.domain

import android.content.Context
import com.mewname.app.ocr.OcrResult

/** Routes one OCR result; readers keep their own parsing and rendering contracts. */
internal object BubbleScreenRouter {
    sealed interface Target {
        data object Trainer : Target
        data object Pokemon : Target
        data class Catalog(val capture: CatalogCapture) : Target
        data class Battle(val advice: BattleAdvice) : Target
        data class Filters(val screen: FilterScreen) : Target
    }
    fun route(context: Context, result: OcrResult): Target {
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
        return route(context, result.fullText, positionedLines, RaidLevelIconDetector.detect(result))
    }

    fun route(context: Context, text: String): Target = route(context, text, emptyList(), null)

    private fun route(context: Context, text: String, positionedLines: List<BattleOcrLine>, raidLevel: Int?): Target {
        if (TrainerProfileParser.isTrainerScreen(text)) return Target.Trainer
        // An appraisal is unambiguous: no Rocket translations or remote catalog are needed.
        if (AppraisalFooterReader.isAppraisal(text)) return Target.Pokemon
        CatalogScreenMatcher.detect(text)?.let { return Target.Catalog(it) }
        FilterScreenDetector.detect(text)?.let { return Target.Filters(it) }
        val quotes = AppLanguage.entries.flatMap { GameTextRepository.rocketQuotes(context, it) }
        CatalogScreenMatcher.detect(text, quotes)?.let { return Target.Catalog(it) }
        BattleAdvisor.adviceForRaw(context, text, positionedLines, raidLevel)?.let { return Target.Battle(it) }
        return Target.Pokemon
    }
}
