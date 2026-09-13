package com.mewname.app.domain

import android.content.Context

/** Routes one OCR result; readers keep their own parsing and rendering contracts. */
internal object BubbleScreenRouter {
    sealed interface Target {
        data object Trainer : Target
        data object Pokemon : Target
        data class Catalog(val capture: CatalogCapture) : Target
        data class Battle(val advice: BattleAdvice) : Target
        data class Filters(val screen: FilterScreen) : Target
    }
    fun route(context: Context, text: String): Target {
        if (TrainerProfileParser.isTrainerScreen(text)) return Target.Trainer
        // An appraisal is unambiguous: no Rocket translations or remote catalog are needed.
        if (AppraisalFooterReader.isAppraisal(text)) return Target.Pokemon
        CatalogScreenMatcher.detect(text)?.let { return Target.Catalog(it) }
        FilterScreenDetector.detect(text)?.let { return Target.Filters(it) }
        val quotes = AppLanguage.entries.flatMap { GameTextRepository.rocketQuotes(context, it) }
        CatalogScreenMatcher.detect(text, quotes)?.let { return Target.Catalog(it) }
        BattleAdvisor.adviceForRaw(context, text)?.let { return Target.Battle(it) }
        return Target.Pokemon
    }
}