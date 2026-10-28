package com.mewname.app

import android.graphics.BitmapFactory
import com.mewname.app.domain.OcrVisualAttributeReader
import com.mewname.app.model.SpecialBackgroundType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * POCO X7 Pro detail screens from 2026-10-27. Special backgrounds alternate with the type background in game,
 * so each Pokémon was captured in both states; the special cards were previously all read as normal.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpecialBackgroundCaptureTest {
    private val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun detect(name: String, types: List<String> = emptyList(), shadow: Boolean = false) =
        javaClass.getResourceAsStream("/ocr/backgrounds/$name")!!.use { BitmapFactory.decodeStream(it) }.let { bitmap ->
            try {
                // Shadow Pokémon can never be lucky, so shadow evidence also rules the lucky look out.
                OcrVisualAttributeReader().detectSpecialBackground(context, "", emptyList(), bitmap, null, types, shadow, shadow)
            } finally {
                bitmap.recycle()
            }
        }

    @Test fun specialCardsAreDetected() {
        for (name in listOf("mewtwo-special-a.jpg", "mewtwo-special-b.jpg", "mewtwo-special-earth.jpg")) {
            assertEquals(name, true, detect(name).first)
        }
    }

    @Test fun teamLeaderCardKeepsItsCategory() {
        val (special, info) = detect("zapdos-team-leader.jpg")
        assertEquals(true, special)
        assertEquals(SpecialBackgroundType.TEAM_LEADER, info.detectedSpecialType)
    }

    @Test fun redTeamLeaderCardOutweighsTheGoldenLuckyLook() {
        // The red card passes the golden lucky test and Ponyta's 4,000/3 power-up fits a lucky one too (2026-10-28).
        val (special, info) = detect("ponyta-team-leader.jpg", listOf("Fire"))
        assertEquals(true, special)
        assertEquals(SpecialBackgroundType.TEAM_LEADER, info.detectedSpecialType)
        assertEquals(false, detect("ponyta-normal.jpg", listOf("Fire")).first)
    }

    @Test fun cardsFarFromTheOwnTypeBackgroundAreDetected() {
        // Neither card is in the catalog, but both are far from the Normal and Dragon type backgrounds.
        assertEquals(true, detect("chansey-community-day.jpg", listOf("Normal")).first)
        assertEquals(true, detect("kyurem-special.jpg", listOf("Dragon", "Ice")).first)
        assertEquals(false, detect("kyurem-normal.jpg", listOf("Dragon", "Ice")).first)
    }

    @Test fun purpleFlameBackgroundWithShadowEvidenceStaysNormal() {
        // Despite the file name this Mewtwo has a regular 2,500/2 power-up and no Purify button; read without shadow
        // evidence the own-type rule still calls its purple flame background special (open question).
        assertEquals(false, detect("mewtwo-shadow.jpg", listOf("Psychic"), shadow = true).first)
    }

    @Test fun shadowBackgroundIsNormalForShadowPokemon() {
        // Shadow Pokémon do not use their type background.
        for (name in listOf("hooh-shadow-normal.jpg", "hooh-shadow-bg-b.jpg", "hooh-shadow-bg-c.jpg", "hooh-shadow-bg-d.jpg")) {
            assertEquals(name, false, detect(name, listOf("Fire", "Flying"), shadow = true).first)
        }
    }

    @Test fun shadowPokemonCanStillShowASpecialCard() {
        // The shadow Ho-Oh alternates a panning golden card with the shadow background; its red wings also pass
        // the golden lucky test in "lucky-look", both are closer to the Ground background, and "fade" is caught
        // mid-transition with the card already showing (calibration/capture_hooh_gold_a.png).
        for (name in listOf("hooh-shadow-special.jpg", "hooh-shadow-gold-lucky-look.jpg", "hooh-shadow-gold-b.jpg",
            "hooh-shadow-fade.jpg")) {
            assertEquals(name, true, detect(name, listOf("Fire", "Flying"), shadow = true).first)
        }
        // The golden card is Road to Kalos' Pokémon Gold background (Johto day, 2026-02-24).
        assertEquals(SpecialBackgroundType.GO_TOUR,
            detect("hooh-shadow-gold-b.jpg", listOf("Fire", "Flying"), shadow = true).second.detectedSpecialType)
    }

    @Test fun darkWildAreaCardIsDetectedOnShadowPokemon() {
        // Another shadow Ho-Oh shows GO Wild Area 2025 Global's purple jungle, ~3 from the shadow background, so
        // neither shadow rule catches it without its capture crops (calibration/wild_area, from other frames).
        for (name in listOf("hooh-shadow-wildarea.jpg", "hooh-shadow-wildarea-b.jpg")) {
            val (special, info) = detect(name, listOf("Fire", "Flying"), shadow = true)
            assertEquals(name, true, special)
            assertEquals(name, SpecialBackgroundType.WILD_AREA, info.detectedSpecialType)
        }
    }

    @Test fun regiRuinsCardIsDetected() {
        // The ruins card sits closer to the Fire background than to any collection card; matched by a capture crop
        // (calibration/capture_regi_ruins_a.png, from another frame). Regidrago alternates it with the Dragon background.
        for (name in listOf("regidrago-ruins.jpg", "regidrago-ruins-b.jpg")) {
            val (special, info) = detect(name, listOf("Dragon"))
            assertEquals(name, true, special)
            // Ancients Recovered (June 2025), filed under eventos_especiais.
            assertEquals(name, SpecialBackgroundType.SPECIAL, info.detectedSpecialType)
        }
        assertEquals(false, detect("regidrago-normal.jpg", listOf("Dragon")).first)
    }

    @Test fun typeBackgroundsStayNormal() {
        for (name in listOf("mewtwo-normal.jpg", "mewtwo-normal-b.jpg", "mamoswine-normal.jpg")) {
            assertEquals(name, false, detect(name).first)
        }
    }
}
