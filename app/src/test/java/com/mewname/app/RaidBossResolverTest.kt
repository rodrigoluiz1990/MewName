package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RaidBossResolverTest {
    private val meta get() = RaidCounterRepository(ApplicationProvider.getApplicationContext<Context>()).metadata()
    private val voltorb = RaidChoice("VOLTORB_HISUIAN_FORM", "RAID_LEVEL_1")
    private val voltorbCatalog get() = listOf(voltorb, RaidChoice("BELDUM", "RAID_LEVEL_1"),
        RaidChoice("NINCADA", "RAID_LEVEL_1"), RaidChoice("SLOWPOKE_GALARIAN_FORM", "RAID_LEVEL_1"))

    @Test fun plainVoltorbSelectsUniqueCurrentRegionalForm() {
        assertEquals(voltorb, RaidBossResolver.resolve("Voltorb", "BATALHA", meta, voltorbCatalog, 1).selected)
        assertEquals(voltorb, RaidBossResolver.resolve("Voltorb", "BATALHA", meta, voltorbCatalog).selected)
    }

    @Test fun twoCurrentRegionalFormsRequireManualChoice() {
        val choices = voltorbCatalog + RaidChoice("VOLTORB", "RAID_LEVEL_1")
        assertNull(RaidBossResolver.resolve("Voltorb", "BATALHA", meta, choices, 1).selected)
        assertEquals(voltorb, RaidBossResolver.resolve("VOLTORB_HISUIAN_FORM", "", meta, choices, 1).selected)
    }

    @Test fun historicalRegionalFormDoesNotBecomeCurrent() {
        val result = RaidBossResolver.resolve("Voltorb", "", meta, emptyList(), 1, listOf(voltorb))
        assertNull(result.selected)
        assertTrue(result.alternatives.isEmpty())
    }

    @Test fun damagedVoltorbNameResolvesOnlyInsideBossBandAndCorrectTier() {
        listOf("Voforb", "Vforb", "Voltorb").forEach { reading ->
            val line = BattleOcrLine(reading, .25f, .25f, .75f, .34f)
            assertEquals(reading, voltorb, RaidBossResolver.resolveOccludedName(listOf(line), meta, voltorbCatalog, 1))
            assertNull(RaidBossResolver.resolveOccludedName(listOf(line), meta, voltorbCatalog, 3))
            assertNull(RaidBossResolver.resolveOccludedName(listOf(line), meta, voltorbCatalog, null))
            val gym = BattleOcrLine(reading, .25f, .05f, .75f, .12f)
            assertNull(RaidBossResolver.resolveOccludedName(listOf(gym), meta, voltorbCatalog, 1))
        }
    }

    @Test fun damagedNameCannotChooseBetweenRegionalForms() {
        val line = BattleOcrLine("Vforb", .25f, .25f, .75f, .34f)
        assertNull(RaidBossResolver.resolveOccludedName(listOf(line), meta,
            voltorbCatalog + RaidChoice("VOLTORB", "RAID_LEVEL_1"), 1))
        listOf("Vxxxx", "forb", "BATALHA").forEach {
            assertNull(RaidBossResolver.resolveOccludedName(
                listOf(BattleOcrLine(it, .25f, .25f, .75f, .34f)), meta, voltorbCatalog, 1))
        }
    }

    @Test fun identifiedCurrentUltraBeastKeepsItsCategoryDespiteFourSymbols() {
        val r = RaidBossResolver.resolve("Buzzwole", "Buzzwole BATALHA", meta,
            listOf(RaidChoice("BUZZWOLE", "RAID_LEVEL_ULTRA_BEAST")), 4,
            listOf(RaidChoice("BUZZWOLE", "RAID_LEVEL_4")))
        assertEquals(RaidChoice("BUZZWOLE", "RAID_LEVEL_ULTRA_BEAST"), r.selected)
        assertEquals(listOf(r.selected), r.alternatives)
    }
    @Test fun savedBossDoesNotLeakIntoCurrentChoices() {
        val r = RaidBossResolver.resolve("Buzzwole", "Buzzwole BATALHA", meta,
            listOf(RaidChoice("LUGIA", "RAID_LEVEL_5")), 4,
            listOf(RaidChoice("BUZZWOLE", "RAID_LEVEL_ULTRA_BEAST")))
        assertNull(r.selected)
        assertTrue(r.alternatives.isEmpty())
    }
    @Test fun currentYveltalWinsOverAllObservedIncorrectSymbolCounts() {
        val yveltal = RaidChoice("YVELTAL", "RAID_LEVEL_5")
        val catalog = listOf(yveltal, yveltal, RaidChoice("PIKACHU", "RAID_LEVEL_1"),
            RaidChoice("QUAQUAVAL", "RAID_LEVEL_3"))
        listOf(1, 3, 4, 5).forEach { level ->
            val result = RaidBossResolver.resolve("Yveltal", "PC45899 Yveltal BATALHA", meta, catalog, level)
            assertEquals(yveltal, result.selected)
            assertEquals(listOf(yveltal), result.alternatives)
            assertTrue(result.fromCatalog)
        }
    }

    @Test fun ambiguousCurrentTiersStillUseDetectedLevel() {
        val three = RaidChoice("QUAQUAVAL", "RAID_LEVEL_3")
        val result = RaidBossResolver.resolve("Quaquaval", "BATALHA", meta,
            listOf(RaidChoice("QUAQUAVAL", "RAID_LEVEL_2"), three), 3)
        assertEquals(three, result.selected)
        assertEquals(listOf(three), result.alternatives)
    }

    @Test fun ambiguousMegaFormsDoNotOverrideSymbolCount() {
        val result = RaidBossResolver.resolve("Charizard", "Mega Charizard BATALHA", meta,
            listOf(RaidChoice("CHARIZARD_MEGA_X", "RAID_LEVEL_MEGA"),
                RaidChoice("CHARIZARD_MEGA_Y", "RAID_LEVEL_MEGA")), 3)
        assertNull(result.selected)
        assertTrue(result.alternatives.isEmpty())
    }

    @Test fun maxOnlyMatchCannotOverrideRaidSymbols() {
        val result = RaidBossResolver.resolve("Yveltal", "BATALHA", meta,
            listOf(RaidChoice("YVELTAL", "RAID_LEVEL_5_MAX")), 1)
        assertNull(result.selected)
        assertTrue(result.alternatives.isEmpty())
    }

    @Test fun detectedLevelListsAllCurrentBossesNormalBeforeShadowWithoutInventedForms() {
        val normal = RaidChoice("PIKACHU", "RAID_LEVEL_1")
        val shadow = RaidChoice("BAGON_SHADOW_FORM", "RAID_LEVEL_1_SHADOW")
        val catalog = listOf(shadow, RaidChoice("MEWTWO", "RAID_LEVEL_5"), normal, normal)
        val result = RaidBossResolver.resolve("Pikachu", "", meta, catalog, 1)
        assertEquals(normal, result.selected)
        assertEquals(listOf(normal, shadow), result.alternatives)
        val unknown = RaidBossResolver.resolve("", "", meta, catalog, 1)
        assertNull(unknown.selected)
        assertEquals(listOf(normal, shadow), unknown.alternatives)
    }

    @Test fun singleFiveSymbolBossLoadsEvenWithoutAReadableName() {
        val boss = RaidChoice("BUZZWOLE", "RAID_LEVEL_ULTRA_BEAST")
        val result = RaidBossResolver.resolve("", "", meta,
            listOf(boss, RaidChoice("PIKACHU", "RAID_LEVEL_1")), 5)
        assertEquals(boss, result.selected)
        assertEquals(listOf(boss), result.alternatives)
    }

    @Test fun bareNameUsesSavedShadowRaidAndCorrectTier() {
        val r=RaidBossResolver.resolve("Bagon","Bagon PC 3000 BATALHA",meta,listOf(RaidChoice("BAGON_SHADOW_FORM","RAID_LEVEL_1_SHADOW")))
        assertEquals(RaidChoice("BAGON_SHADOW_FORM","RAID_LEVEL_1_SHADOW"),r.selected)
        assertTrue(r.fromCatalog)
        assertTrue(r.alternatives.any { it.id=="BAGON" })
    }
    @Test fun shadowEvidenceOverridesNormalCatalogInThreeLanguages() {
        listOf("Reide sombrosa", "Purified Gems", "Incursión oscura").forEach { text ->
            val r=RaidBossResolver.resolve("Mewtwo",text,meta,listOf(RaidChoice("MEWTWO","RAID_LEVEL_5")))
            assertEquals("MEWTWO_SHADOW_FORM",r.selected?.id)
            assertFalse(r.fromCatalog)
        }
    }
    @Test fun megaAndItsTypesArePreserved() {
        val r=RaidBossResolver.resolve("Charizard","Mega Charizard X PC 53260",meta,emptyList())
        assertEquals("CHARIZARD_MEGA_X",r.selected?.id)
        assertEquals(listOf("Fire","Dragon"),meta.pokemon[r.selected!!.id]!!.types)
        assertNull(RaidBossResolver.resolve("Charizard","Mega Charizard",meta,emptyList()).selected)
    }
    @Test fun namedMegaAndRealThundurusScreenResolveOffline() {
        assertEquals("CHARIZARD_MEGA_Y",RaidBossResolver.resolve("Mega Charizard Y","",meta,emptyList()).selected?.id)
        val r=RaidBossResolver.resolve("Thundurus","PC46044\nThundurus\nBATALHA\nGrupo privado",meta,
            listOf(RaidChoice("THUNDURUS_SHADOW_FORM","RAID_LEVEL_5_SHADOW")))
        assertEquals("THUNDURUS_SHADOW_FORM",r.selected?.id)
    }
    @Test fun ambiguousCatalogAndGymNameDoNotForceShadow() {
        val r=RaidBossResolver.resolve("Mewtwo","Shadow Park Mewtwo BATALHA",meta,listOf(
            RaidChoice("MEWTWO","RAID_LEVEL_5"),RaidChoice("MEWTWO_SHADOW_FORM","RAID_LEVEL_5_SHADOW")))
        assertEquals("MEWTWO",r.selected?.id)
        assertFalse(r.fromCatalog)
    }
    @Test fun formsDoNotMixRegionalSpeciesOrOtherTransformations() {
        val r=RaidBossResolver.resolve("Raichu","Raichu",meta,emptyList())
        assertFalse(r.alternatives.any { "ALOLA" in it.id })
        assertTrue(RaidBossResolver.resolve("Missing", "", meta, emptyList()).alternatives.isEmpty())
    }
    @Test fun screenTextSurvivesBattleRouting() {
        val text="Mewtwo\nReide sombrosa\nBATALHA\nGRUPO PRIVADO"
        val advice=BattleAdvisor.adviceForRaw(ApplicationProvider.getApplicationContext(),text)
        assertNotNull(advice)
        assertEquals(text,advice!!.raidScreenText)
    }
    @Test fun positionedBossBandWinsOverPokemonNameInGymTitle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val zamazenta = BattleAdvisor.adviceForRaw(
            context,
            "Academia Pikachu\nPC 52195\nZamazenta\nBATALHA\nGrupo privado",
            listOf(
                BattleOcrLine("Academia Pikachu", 0.20f, 0.05f, 0.88f, 0.12f),
                BattleOcrLine("PC 52195", 0.28f, 0.17f, 0.72f, 0.24f),
                BattleOcrLine("Zamazenta", 0.17f, 0.25f, 0.83f, 0.34f)
            )
        )
        assertEquals("Zamazenta", zamazenta?.bossName)

        val pikachu = BattleAdvisor.adviceForRaw(
            context,
            "Ginásio Zamazenta\nPC 3277\nPikachu\nBATALHA\nGrupo privado",
            listOf(
                BattleOcrLine("Ginásio Zamazenta", 0.18f, 0.05f, 0.90f, 0.13f),
                BattleOcrLine("PC 3277", 0.30f, 0.17f, 0.70f, 0.24f),
                BattleOcrLine("Pikachu", 0.20f, 0.25f, 0.80f, 0.34f)
            )
        )
        assertEquals("Pikachu", pikachu?.bossName)
    }
    @Test fun raidLevelFiltersCurrentChoicesWhenBossNameIsMissing() {
        val catalog = listOf(
            RaidChoice("SKELEDIRGE", "RAID_LEVEL_3"),
            RaidChoice("ZAMAZENTA_HERO_FORM", "RAID_LEVEL_5"),
            RaidChoice("PIKACHU", "RAID_LEVEL_1")
        )
        val result = RaidBossResolver.resolve("", "PC 18504 BATALHA", meta, catalog, detectedLevel = 3)
        assertEquals(RaidChoice("SKELEDIRGE", "RAID_LEVEL_3"), result.selected)
        assertEquals(listOf(result.selected), result.alternatives)
    }

    @Test fun partialBossNameAndDetectedLevelResolveTheCurrentRaid() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val advice = BattleAdvisor.adviceForRaw(
            context,
            "PC 18504\nSkeledir\nBATALHA\nGrupo privado",
            listOf(BattleOcrLine("Skeledir", 0.25f, 0.25f, 0.75f, 0.34f)),
            detectedRaidLevel = 3
        )
        assertEquals("Skeledirge", advice?.bossName)
        assertEquals(3, advice?.raidLevel)
        val result = RaidBossResolver.resolve(
            advice!!.bossName.orEmpty(), advice.raidScreenText, meta,
            listOf(RaidChoice("SKELEDIRGE", "RAID_LEVEL_3"), RaidChoice("ZAMAZENTA_HERO_FORM", "RAID_LEVEL_5")),
            advice.raidLevel
        )
        assertEquals(RaidChoice("SKELEDIRGE", "RAID_LEVEL_3"), result.selected)
    }
}
