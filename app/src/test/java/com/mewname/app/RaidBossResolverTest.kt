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
        assertNull(result.selected)
        assertEquals(listOf(RaidChoice("SKELEDIRGE", "RAID_LEVEL_3")), result.alternatives)
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
