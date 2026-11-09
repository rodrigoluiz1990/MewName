package com.mewname.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CostumeNamesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val names by lazy { CostumeNames(context) }

    @Test fun halloweenNamesMatchSpeciesInsteadOfOnlyEventCode() {
        assertEquals("Fantasia de Shedinja", names.label("pm1.fFALL_2019.icon.png", "", AppLanguage.PT_BR))
        assertEquals("Cubone costume", names.label("pm4.fFALL_2019.icon.png", "", AppLanguage.EN))
        assertEquals("Disfraz de Yamask", names.label("pm7.fFALL_2019.icon.png", "", AppLanguage.ES))
    }

    @Test fun everyDisplayedCostumeHasAllThreeTranslations() {
        val images = PersonalCollectionImages(context)
        var count = 0
        val missing = mutableListOf<String>()
        CollectionDexCatalog.load(context).forEach { species ->
            species.targets.filter { it.key in species.costumeKeys }.forEach { target ->
                images.url(target)?.takeIf(names::isCostume)?.let { url ->
                    AppLanguage.entries.forEach { language ->
                        val label = names.label(url, "MISSING", language)
                        if (label == "MISSING") missing.add("$url / $language")
                        assertFalse(label.contains("Noevolve", ignoreCase = true))
                    }
                    count++
                }
            }
        }
        assertTrue(missing.joinToString("\n"), missing.isEmpty())
        assertTrue(count > 200)
    }

    @Test fun unknownSpritesNeverInheritAnotherCostumeAndOrdinaryPlaceholdersAreExcluded() {
        assertEquals("Unknown", names.label("pm1.cUNKNOWN.icon.png", "Unknown Noevolve", AppLanguage.PT_BR))
        assertFalse(names.isCostume("https://example.com/pm150.cONE_YEAR_ANNIVERSARY.icon.png"))
        assertTrue(names.isCostume("https://example.com/pm1.fFALL_2019.icon.png"))
    }

    @Test fun correctedCostumesUseExactSpritesAndAllLanguages() {
        val file = "pm25.cmay_2019_noevolve.icon.png"
        assertEquals("Chap\u00e9u de palha",names.label(file,"",AppLanguage.PT_BR))
        assertEquals("Straw hat",names.label(file,"",AppLanguage.EN))
        assertEquals("Sombrero de paja",names.label(file,"",AppLanguage.ES))
        assertEquals("4th anniversary balloons",names.label("pm25.fcostume_2020.icon.png","",AppLanguage.EN))
        assertEquals("Bufanda",names.label("pm131.fcostume_2020.icon.png","",AppLanguage.ES))
    }
    @Test fun collectionTranslationsPreserveKeysAndCollectedIdentity() {
        val catalogs = AppLanguage.entries.map { CollectionDexCatalog.load(context,language=it) }
        assertEquals(catalogs[0].flatMap { it.targets }.map { it.key },catalogs[1].flatMap { it.targets }.map { it.key })
        assertEquals(catalogs[0].flatMap { it.targets }.map { it.key },catalogs[2].flatMap { it.targets }.map { it.key })
        for ((language,expected) in listOf(AppLanguage.PT_BR to "Cachecol",AppLanguage.EN to "Scarf",AppLanguage.ES to "Bufanda")) {
            val lapras=CollectionDexCatalog.load(context,language=language).single { it.dex==131 }
            assertTrue(lapras.targets.any { it.label=="Lapras \u00b7 $expected" })
        }
    }

    @Test fun filenamesAreCaseInsensitiveAndQueryDoesNotChangeIdentity() {
        assertEquals("Chapéu de safári", names.label("https://example.com/pm133.cMay_2023.icon.png?cache=1", "", AppLanguage.PT_BR))
    }
}
