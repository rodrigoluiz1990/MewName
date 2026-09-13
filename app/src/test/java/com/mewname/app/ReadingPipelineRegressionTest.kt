package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import com.mewname.app.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReadingPipelineRegressionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun appraisalSpeciesOverridesEvolutionNicknameWithoutOcrCoordinates() {
        val text = "PC476\n93Ursaluna TM\nAtaque\nDefesa\nPS\nO Pokémon Teddiursa foi pego em\n01/04/2026 perto de Paraná, Brasil."
        assertEquals("Teddiursa", AppraisalFooterReader.candidate(text))
        assertEquals("Teddiursa", AppraisalFooterReader.candidate("This Teddiursa was caught on 01/04/2026"))
        assertEquals("Teddiursa", AppraisalFooterReader.candidate("Este Teddiursa fue atrapado el 01/04/2026"))
        assertEquals(BubbleScreenRouter.Target.Pokemon, BubbleScreenRouter.route(context, text))
        val stages = mutableListOf<String>()
        val result = OcrPokemonParser().parse(context, text, onAnalysisStep = { stages += it })
        assertEquals("Teddiursa", result.pokemonName)
        assertTrue(stages.contains("Identificando gênero"))
        assertEquals("Baltoy", OcrPokemonParser().parse(context,
            "PC290\n#100Baltoy\nAtaque\nDefesa\nPS\nO Pokémon Baltoy foi pego em\n04/10/2025").pokemonName)
    }
    @Test fun otherScreenRoutesRemainSeparate() {
        assertTrue(BubbleScreenRouter.route(context, "TAGS\nPOKÉMON\nOVOS\n0 / 5 km") is BubbleScreenRouter.Target.Catalog)
        assertTrue(BubbleScreenRouter.route(context, "PESQUISA\nHOJE\nChocar 1 Ovo.") is BubbleScreenRouter.Target.Catalog)
        assertTrue(BubbleScreenRouter.route(context, "TAGS\nPOKÉMON\nOVOS\n5934/8000\nPC290") is BubbleScreenRouter.Target.Filters)
        assertTrue(BubbleScreenRouter.route(context, "Normal e fraco são duas coisas bem diferentes.") is BubbleScreenRouter.Target.Catalog)
    }
    @Test fun masterBadgeUsesPurifiedIvsAndClearsOldResultOutsideScope() {
        val catalog = MasterIvBadgeCatalog()
        val family = listOf("Baltoy", "Claydol")
        val scanned = PokemonScreenData(pokemonName = "Baltoy", attIv = 13, defIv = 13, staIv = 13,
            isShadow = true, masterIvBadgeMatch = false)
        val purified = scanned.withReviewIvValues(ReviewIvMode.PURIFIED, 13, 13, 13, false)
        val expected = catalog.resolve(context, family, purified.ivPercent, 15, 15, 15)
        val actual = buildMasterIvReviewData(context, purified, family, catalog)
        assertEquals(100, purified.ivPercent)
        assertEquals(expected.isBestMatch, actual.masterIvBadgeMatch)
        assertNull(actual.masterIvBadgeMatch) // 100% remains outside the existing IV Master table.
        val lowIv = scanned.withReviewIvValues(ReviewIvMode.SHADOW, 1, 1, 1, false).copy(masterIvBadgeMatch = true)
        assertNull(buildMasterIvReviewData(context, lowIv, family, catalog).masterIvBadgeMatch)
        val restored = scanned.withReviewIvValues(ReviewIvMode.SHADOW, 13, 13, 13, false)
        assertEquals(catalog.resolve(context, family, restored.ivPercent, 13, 13, 13).isBestMatch,
            buildMasterIvReviewData(context, restored, family, catalog).masterIvBadgeMatch)
    }
    @Test fun supportedPurifiedCombinationReplacesCapturedFalseBadge() {
        val original = PokemonScreenData(pokemonName = "Abomasnow", attIv = 12, defIv = 13, staIv = 13,
            isShadow = true, masterIvBadgeMatch = false)
        val data = original.withReviewIvValues(ReviewIvMode.PURIFIED, 12, 13, 13, false)
        assertEquals(98, data.ivPercent)
        val result = buildMasterIvReviewData(context, data, listOf("Abomasnow"), MasterIvBadgeCatalog())
        assertEquals(true, result.masterIvBadgeMatch)
        assertEquals(14, result.masterIvBadgeDebugInfo?.expectedAttack)
        assertEquals(15, result.masterIvBadgeDebugInfo?.expectedDefense)
    }
}