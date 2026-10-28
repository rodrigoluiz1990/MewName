package com.mewname.app.domain

import com.mewname.app.domain.OcrTextGeometry.normalizedLinesInRegion
import com.mewname.app.domain.OcrTextGeometry.normalizedBitmapRect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import androidx.palette.graphics.Palette
import com.mewname.app.model.BackgroundDebugInfo
import com.mewname.app.ocr.OcrTextLine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Recognizes backgrounds and the favorite star, keeping visual caches local. */
internal class OcrVisualAttributeReader {
    @Volatile
    private var shadowTextureSignatures: List<IntArray>? = null

    // Whole words only: Marshadow and English moves (Shadow Ball, Shadow Claw...) are not shadow Pokémon.
    private val shadowWord = Regex(
        "(?<![A-Z])(?:SHADOW(?! (?:BALL|CLAW|SNEAK|PUNCH|BONE|FORCE))|SOMBROS[OA])(?![A-Z])"
    )

    private companion object {
        const val SPECIAL_REFERENCE_MAX_DISTANCE = 2.05
        const val SPECIAL_REFERENCE_MARGIN = 0.35
        const val OWN_TYPE_MIN_DISTANCE = 3.3
        // Type-background captures far from their own type never had a special reference under 2.96; missed special
        // cards (Regice, Moltres, Kyurem) sat at 2.68-2.80.
        const val OWN_TYPE_SPECIAL_MAX_DISTANCE = 2.85
        const val LOOSE_NEAR_MARGIN = 0.10
        const val LOOSE_FAR_MAX_DISTANCE = 2.5
        const val LOOSE_FAR_MARGIN = 0.30
        const val SHADOW_LOOSE_MAX_DISTANCE = 2.10
        const val SHADOW_BACKGROUND_MIN_DISTANCE = 5.0
        // Panning cards reach 1.99 from their own crops; other cards stayed above 2.17 from every crop.
        const val CALIBRATION_TYPE_MAX_DISTANCE = 2.0
        const val CALIBRATION_TYPE_FAR_MAX_DISTANCE = 2.7
        const val CALIBRATION_TYPE_FAR_MARGIN = 0.4
        const val STRONG_SPECIAL_MAX_DISTANCE = 1.6
        const val STRONG_SPECIAL_MARGIN = 0.8
    }

    private val specialBackgroundKeywords = setOf(
        "LONDON", "LONDRES", "NEW YORK", "NOVA YORK", "OSAKA", "MADRID", "SENDAI",
        "INCHEON", "JEJU", "GUAYAQUIL", "JAKARTA", "BALI",
        "DELIGHTFUL", "TRANSFORMATION", "TALES", "GO FEST", "GOFEST", "SAFARI", "TOUR"
    )

    private val backgroundReferenceMatcher = BackgroundReferenceMatcher()
    private fun analyzeColorsForSpecialBackground(bitmap: Bitmap): Boolean {
        return try {
            // Sample the visible battle/background area behind the Pokemon and
            // avoid UI chrome like the white favorite panel in the top-right.
            val region = normalizedBitmapRect(
                bitmap,
                minX = 0.05f,
                maxX = 0.80f,
                minY = 0.05f,
                maxY = 0.40f
            )
            val part = Bitmap.createBitmap(bitmap, region.left, region.top, region.width(), region.height())
            val palette = Palette.from(part).generate()
            val standardColors = listOf(
                Color.parseColor("#76c84d"),
                Color.parseColor("#509bf2"),
                Color.parseColor("#2c3e50")
            )
            val dominantColor = palette.getDominantColor(Color.TRANSPARENT)
            if (dominantColor == Color.TRANSPARENT) return false
            val hsv = FloatArray(3)
            Color.colorToHSV(dominantColor, hsv)
            val isSpecialPalette = hsv[1] >= 0.30f && hsv[2] <= 0.75f
            isSpecialPalette && standardColors.none { color -> calculateColorDistance(dominantColor, color) < 32.0 }
        } catch (_: Exception) {
            false
        }
    }

    fun detectSpecialBackground(
        context: Context,
        normalizedRaw: String,
        lines: List<OcrTextLine>,
        bitmap: Bitmap?,
        referenceBounds: Rect?,
        detectedTypes: List<String>,
        shadowEvidence: Boolean = false,
        luckyExcluded: Boolean = false
    ): Pair<Boolean, BackgroundDebugInfo> {
        val topVisualLines = normalizedLinesInRegion(lines, 0.0f, 1.0f, 0.00f, 0.22f, referenceBounds)
        val bottomMetaLines = normalizedLinesInRegion(lines, 0.0f, 1.0f, 0.86f, 1.00f, referenceBounds)
        val midInfoLines = normalizedLinesInRegion(lines, 0.18f, 0.82f, 0.30f, 0.58f, referenceBounds)

        val textMatch = specialBackgroundKeywords.any { normalizedRaw.contains(it) }
        val topRegionMatch = (topVisualLines + bottomMetaLines)
            .any { normalized -> specialBackgroundKeywords.any { normalized.contains(it) } }
        var lapStart = android.os.SystemClock.elapsedRealtime()
        // Debug-only split of the background check, to locate which detector dominates its time.
        fun lap(label: String) {
            if (!com.mewname.app.BuildConfig.DEBUG) return
            val now = android.os.SystemClock.elapsedRealtime()
            android.util.Log.d("MewNameReadTiming", "    fundo especial > $label: ${now - lapStart}ms")
            lapStart = now
        }
        val eventBadgeVisualMatch = bitmap?.let(::detectEventBadgeVisual) ?: false
        lap("selo de evento")
        val shadowTextMatch = sequenceOf(normalizedRaw)
            .plus(midInfoLines.asSequence())
            .plus(bottomMetaLines.asSequence())
            .any { normalized -> shadowWord.containsMatchIn(normalized) }
        val luckyTextMatch = sequenceOf(normalizedRaw)
            .plus(midInfoLines.asSequence())
            .plus(bottomMetaLines.asSequence())
            .any { normalized ->
                normalized.contains("SORTUDO") ||
                    normalized.contains("POKEMON SORTUDO") ||
                    normalized.contains("LUCKY POKEMON") ||
                    normalized.contains("LUCKY")
            }
        val luckyVisualMatch = bitmap?.let(::detectLuckyBackgroundVisual) ?: false
        lap("sortudo")
        val shinyParticleMatch = bitmap?.let(::detectShinyParticles) ?: false
        lap("brilhante")
        val shadowParticleMatch = bitmap?.let(::detectShadowParticles) ?: false
        lap("partículas de sombra")
        val shadowTextureMatch = bitmap?.let { detectShadowTexture(context, it) } ?: false
        lap("textura de sombra")
        val referenceDebug = bitmap?.let { backgroundReferenceMatcher.debugSpecialBackground(context, it) }
        lap("referências de fundo")
        val referenceMatch = referenceDebug?.isSpecial
        val bestNormalDistance = referenceDebug?.bestNormalDistance ?: Double.MAX_VALUE
        // A special reference this close (Ponyta on the red Team Leader card: 1.43 vs Fire 2.78) outweighs the
        // golden test; lucky backgrounds stayed at 1.94-2.09 from their closest one (Ho-Oh, Groudon, 2026-10-28).
        val strongSpecialReference = referenceDebug?.bestSpecialDistance?.let { special ->
            special <= STRONG_SPECIAL_MAX_DISTANCE && special + STRONG_SPECIAL_MARGIN < bestNormalDistance
        } ?: false
        // The golden test also counts red/orange, so a red Pokémon (shadow Ho-Oh's spread wings) or card can trip it;
        // the power-up cost or shadow evidence rule lucky out.
        val luckyMatch = luckyTextMatch || (luckyVisualMatch && !luckyExcluded && !strongSpecialReference)
        val typeNormalReferenceMatch = referenceDebug?.bestNormalReferenceName
            ?.let { name -> matchesDetectedTypeBackground(name, detectedTypes) }
            ?: false
        val typeNormalReferenceStrongEnough = typeNormalReferenceMatch &&
            bestNormalDistance.isFinite() &&
            bestNormalDistance <= 5.80
        val normalReferenceClearlyBetter = referenceMatch == false &&
            bestNormalDistance.isFinite() &&
            bestNormalDistance <= 2.60
        val normalReferenceCompetitive = referenceMatch == false &&
            bestNormalDistance.isFinite() &&
            bestNormalDistance <= 2.78
        val strongNormalReference = referenceMatch == false &&
            normalReferenceClearlyBetter
        val colorMatch = bitmap?.let { analyzeColorsForSpecialBackground(it) } ?: false
        lap("paleta de cores")
        val referenceUnknownOrSpecial = referenceMatch != false
        val referenceVeryDifferentFromNormal = bestNormalDistance.isFinite() &&
            bestNormalDistance >= 4.25
        val referenceSpecialMatch = referenceMatch == true && !typeNormalReferenceStrongEnough
        val colorOverrideSpecial = colorMatch &&
            referenceUnknownOrSpecial &&
            referenceVeryDifferentFromNormal &&
            !typeNormalReferenceStrongEnough
        val normalReferenceBlocksWeakVisual = typeNormalReferenceStrongEnough ||
            normalReferenceClearlyBetter ||
            normalReferenceCompetitive
        val eventBadgeSpecialMatch = eventBadgeVisualMatch && !normalReferenceBlocksWeakVisual
        // Special cards also sit ~2.2 from some type background, inside the normal threshold. POCO X7 Pro
        // captures (2026-10-27) separate them by being clearly closer to a special reference instead.
        val bestSpecialDistance = referenceDebug?.bestSpecialDistance ?: Double.MAX_VALUE
        val typeDistances = referenceDebug?.typeDistances.orEmpty()
        // Shadow Pokémon show the shadow background (details_type_bg_shadow_*) instead of their type one, and it
        // varies more, so its special cards need a closer reference (Ho-Oh 2.04 vs Regirock 2.13, Mewtwo 2.31).
        val shadowLike = shadowEvidence || shadowTextMatch
        val shadowBackgroundDistance = typeDistances.filterKeys { it.startsWith("shadow") }.values.minOrNull()
        val comparableNormalDistance = if (shadowLike && shadowBackgroundDistance != null) {
            minOf(bestNormalDistance, shadowBackgroundDistance)
        } else bestNormalDistance
        val specialReferenceClearlyCloser = bestSpecialDistance <= SPECIAL_REFERENCE_MAX_DISTANCE &&
            bestSpecialDistance + SPECIAL_REFERENCE_MARGIN < comparableNormalDistance
        // The game draws the Pokémon's own type background; a special card can still resemble another type's.
        val ownTypeDistance = detectedTypes.mapNotNull { typeDistances[it.lowercase()] }.minOrNull()
        val farFromOwnTypeBackground = !shadowLike && ownTypeDistance != null && ownTypeDistance >= OWN_TYPE_MIN_DISTANCE &&
            bestSpecialDistance <= OWN_TYPE_SPECIAL_MAX_DISTANCE
        val strictDetected = (
            referenceDebug?.specialType != null || (!luckyMatch &&
                (textMatch || topRegionMatch || eventBadgeSpecialMatch || referenceSpecialMatch || colorOverrideSpecial ||
                    specialReferenceClearlyCloser))
            )
        // 27 live reads with special cards on screen (2026-10-27) were half missed by the margins above; in 29
        // type-background captures a special reference under 2.05 was never closer than the normal one.
        val looseReferenceMatch = !luckyMatch && if (shadowLike) {
            bestSpecialDistance <= SHADOW_LOOSE_MAX_DISTANCE && bestSpecialDistance + LOOSE_NEAR_MARGIN < comparableNormalDistance
        } else {
            (bestSpecialDistance <= SPECIAL_REFERENCE_MAX_DISTANCE && bestSpecialDistance + LOOSE_NEAR_MARGIN < bestNormalDistance) ||
                (bestSpecialDistance <= LOOSE_FAR_MAX_DISTANCE && bestSpecialDistance + LOOSE_FAR_MARGIN < bestNormalDistance)
        }
        // The shadow Ho-Oh's golden card pans, so most frames sit ~1.9 from the Ground background and 2.1-2.4 from
        // the special references; its distance to the shadow background (5.4-6.6 vs 2.0-2.8, ~4 mid-fade) is what
        // tells them apart. Only for proven shadows: the purple Psychic background is ~5 from it too.
        val farFromShadowBackground = shadowEvidence && shadowBackgroundDistance != null &&
            shadowBackgroundDistance >= SHADOW_BACKGROUND_MIN_DISTANCE && bestSpecialDistance <= OWN_TYPE_SPECIAL_MAX_DISTANCE
        // Lucky backgrounds stay near the type one (Groudon 2.5-2.9), while golden and orange special cards (Moltres,
        // Articuno, Tauros, 2026-10-28) trip the visual lucky test; only the lucky text rules this check out.
        val detected = strictDetected || (!luckyTextMatch && farFromOwnTypeBackground) ||
            (!luckyMatch && farFromShadowBackground) || looseReferenceMatch
        // Capture crops in background_refs/calibration share the screen's framing and tone, so the closest one names
        // the category even without the strict margin (Regidrago's ruins card reaches 1.34 with the Fire one at 1.83).
        // Each crop holds its own Pokémon over a panning card, so another Pokémon on the same card sits farther
        // (learned GO Fest crops: Squirtle 2.32 and Wailmer 2.11, with the closest type background at 3.04 and 3.17).
        val closeReferenceType = referenceDebug?.bestSpecialReferenceName
            ?.takeIf {
                "/calibration/" in it && (bestSpecialDistance <= CALIBRATION_TYPE_MAX_DISTANCE ||
                    (bestSpecialDistance <= CALIBRATION_TYPE_FAR_MAX_DISTANCE &&
                        bestSpecialDistance + CALIBRATION_TYPE_FAR_MARGIN < bestNormalDistance))
            }
            ?.let(SpecialBackgroundCatalog::type)
        return detected to BackgroundDebugInfo(
            detectedSpecialType = (referenceDebug?.specialType ?: closeReferenceType).takeIf { detected },
            textMatch = textMatch,
            topRegionMatch = topRegionMatch,
            eventBadgeVisualMatch = eventBadgeVisualMatch,
            luckyTextMatch = luckyTextMatch,
            luckyVisualMatch = luckyVisualMatch,
            shinyParticleMatch = shinyParticleMatch,
            shadowTextMatch = shadowTextMatch,
            shadowParticleMatch = shadowParticleMatch,
            shadowTextureMatch = shadowTextureMatch,
            referenceDecision = referenceMatch,
            referenceName = referenceDebug?.bestReferenceName,
            referenceDistance = referenceDebug?.bestDistance,
            specialReferenceName = referenceDebug?.bestSpecialReferenceName,
            specialReferenceDistance = referenceDebug?.bestSpecialDistance,
            ownTypeDistance = ownTypeDistance,
            shadowBackgroundDistance = shadowBackgroundDistance,
            colorFallbackMatch = colorMatch,
            topRegionLines = topVisualLines,
            bottomRegionLines = bottomMetaLines,
            notes = buildString {
                append("decisao final: ${if (detected) "especial" else "normal"}")
                append("; ")
                if (referenceDebug?.referenceCount == 0) append("nenhuma referencia normal carregada; ")
                if (textMatch) append("texto especial encontrado; ")
                if (topRegionMatch) append("topo/meta com marcador especial; ")
                if (eventBadgeVisualMatch) append("selo visual de evento encontrado; ")
                if (referenceMatch == true) append("matcher de referencia marcou especial; ")
                if (specialReferenceClearlyCloser) append("referencia especial claramente mais proxima que a normal; ")
                if (farFromOwnTypeBackground) append("fundo distante do fundo do proprio tipo (${"%.2f".format(ownTypeDistance)}); ")
                if (referenceMatch == false) append("matcher de referencia marcou normal; ")
                if (referenceMatch == null && referenceDebug?.bestDistance != null) append("matcher de referencia ficou inconclusivo; ")
                if (!referenceDebug?.bestSpecialReferenceName.isNullOrBlank()) append("melhor special=${referenceDebug?.bestSpecialReferenceName}; ")
                if (colorMatch) append("fallback de cor marcou especial; ")
                if (colorOverrideSpecial) append("fallback de cor sobrepos referencia normal; ")
                if (eventBadgeVisualMatch && normalReferenceBlocksWeakVisual) append("referencia normal bloqueou selo visual; ")
                if (typeNormalReferenceStrongEnough) append("referencia normal do tipo detectado bloqueou especial; ")
                if (luckyTextMatch) append("texto de sortudo encontrado; ")
                if (luckyVisualMatch) append("visual dourado de sortudo encontrado; ")
                if (luckyMatch) append("sortudo tratado como fundo normal; ")
                if (shinyParticleMatch) append("particulas brilhantes de shiny encontradas; ")
                if (shadowTextMatch) append("texto de sombrio encontrado; ")
                if (shadowParticleMatch) append("particulas de sombra encontradas; ")
                if (shadowTextureMatch) append("textura de sombra encontrada; ")
                if (normalReferenceClearlyBetter) append("referencia normal forte encontrada; ")
                if (normalReferenceCompetitive && !normalReferenceClearlyBetter) append("referencia normal competitiva encontrada; ")
                if (strongNormalReference) append("referencia normal forte bloqueou o fallback de cor; ")
                if (referenceMatch == null && bitmap == null) append("sem bitmap para matcher de referencia; ")
            }.trim()
        )
    }

    private fun detectEventBadgeVisual(bitmap: Bitmap): Boolean {
        return try {
            val region = normalizedBitmapRect(bitmap, 0.02f, 0.34f, 0.03f, 0.17f)
            val hsv = FloatArray(3)
            var sampled = 0
            var saturated = 0
            var darkOutline = 0
            val hueBuckets = mutableSetOf<Int>()
            bitmap.forEachSampledPixel(region, 2) { _, _, color ->
                colorToHsv(color, hsv)
                val sat = hsv[1]
                val value = hsv[2]
                val brightness = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3f
                sampled++
                if (sat >= 0.22f && value in 0.18f..0.95f) {
                    saturated++
                    hueBuckets += (hsv[0] / 24f).toInt()
                }
                if (brightness <= 96f && value <= 0.50f) {
                    darkOutline++
                }
            }
            if (sampled == 0) return false
            val saturatedRatio = saturated.toDouble() / sampled.toDouble()
            saturatedRatio >= 0.16 &&
                darkOutline >= 45 &&
                hueBuckets.size >= 4
        } catch (_: Exception) {
            false
        }
    }

    private fun matchesDetectedTypeBackground(referenceName: String, detectedTypes: List<String>): Boolean {
        if (detectedTypes.isEmpty()) return false
        val normalizedName = referenceName.lowercase()
        return detectedTypes.any { type ->
            val normalizedType = type.lowercase()
            normalizedName.contains("details_type_bg_$normalizedType") ||
                normalizedName.contains("_$normalizedType.")
        }
    }

    private fun detectLuckyBackgroundVisual(bitmap: Bitmap): Boolean {
        return try {
            val region = normalizedBitmapRect(bitmap, 0.08f, 0.82f, 0.03f, 0.34f)
            val hsv = FloatArray(3)
            var goldenBright = 0
            var sampled = 0
            bitmap.forEachSampledPixel(region, 6) { _, _, color ->
                colorToHsv(color, hsv)
                sampled++
                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]
                val warmGold = ((hue in 28f..62f) || hue < 16f) && sat >= 0.22f && value >= 0.72f
                if (warmGold) goldenBright++
            }
            sampled > 0 && (goldenBright.toDouble() / sampled.toDouble()) >= 0.20
        } catch (_: Exception) {
            false
        }
    }

    private fun detectShinyParticles(bitmap: Bitmap): Boolean {
        return try {
            val region = normalizedBitmapRect(bitmap, 0.06f, 0.90f, 0.03f, 0.34f)
            val hsv = FloatArray(3)
            var brightWhite = 0
            var sampled = 0
            bitmap.forEachSampledPixel(region, 8) { x, y, color ->
                if (x < bitmap.width * 0.86f || y > bitmap.height * 0.10f) {
                    colorToHsv(color, hsv)
                    sampled++
                    val nearWhite = hsv[1] <= 0.12f && hsv[2] >= 0.92f
                    if (nearWhite) brightWhite++
                }
            }
            sampled > 0 && brightWhite in 12..180
        } catch (_: Exception) {
            false
        }
    }

    private fun detectShadowParticles(bitmap: Bitmap): Boolean {
        return try {
            val candidateRegions = listOf(
                normalizedBitmapRect(bitmap, 0.06f, 0.90f, 0.03f, 0.38f),
                normalizedBitmapRect(bitmap, 0.18f, 0.82f, 0.05f, 0.34f),
                normalizedBitmapRect(bitmap, 0.22f, 0.78f, 0.08f, 0.30f)
            )
            val hsv = FloatArray(3)
            candidateRegions.any { region ->
                var purpleDark = 0
                var sampled = 0
                bitmap.forEachSampledPixel(region, 5) { _, _, color ->
                    colorToHsv(color, hsv)
                    sampled++
                    val hue = hsv[0]
                    val sat = hsv[1]
                    val value = hsv[2]
                    val shadowLike = (hue in 250f..330f) && sat >= 0.18f && value in 0.14f..0.78f
                    if (shadowLike) purpleDark++
                }
                sampled > 0 && (purpleDark.toDouble() / sampled.toDouble()) >= 0.07
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun detectShadowTexture(context: Context, bitmap: Bitmap): Boolean {
        return try {
            val textureSignatures = loadShadowTextureSignatures(context)
            if (textureSignatures.isEmpty()) return false
            val candidates = listOf(
                normalizedBitmapRect(bitmap, 0.06f, 0.90f, 0.03f, 0.38f),
                normalizedBitmapRect(bitmap, 0.10f, 0.86f, 0.05f, 0.34f),
                normalizedBitmapRect(bitmap, 0.12f, 0.82f, 0.07f, 0.32f),
                normalizedBitmapRect(bitmap, 0.18f, 0.82f, 0.08f, 0.30f),
                normalizedBitmapRect(bitmap, 0.22f, 0.78f, 0.10f, 0.28f)
            )
            val bestDistance = candidates.minOfOrNull { rect ->
                val crop = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
                try {
                    val cropSignature = createMiniSignature(crop)
                    textureSignatures.minOfOrNull { signatureDistance(cropSignature, it) } ?: Double.MAX_VALUE
                } finally {
                    if (!crop.isRecycled) crop.recycle()
                }
            } ?: Double.MAX_VALUE
            bestDistance <= 2.95
        } catch (_: Exception) {
            false
        }
    }

    private fun loadShadowTextureSignatures(context: Context): List<IntArray> {
        shadowTextureSignatures?.let { return it }
        synchronized(this) {
            shadowTextureSignatures?.let { return it }
            val loaded = buildList {
                listOf("tx_sombroso.png", "tx_sombroso_alt.png").forEach { assetName ->
                    runCatching {
                        context.assets.open(assetName).use { input ->
                            val bitmap = BitmapFactory.decodeStream(input) ?: return@use
                            try {
                                add(createMiniSignature(bitmap))
                            } finally {
                                if (!bitmap.isRecycled) bitmap.recycle()
                            }
                        }
                    }.getOrNull()
                }
            }
            shadowTextureSignatures = loaded
            return loaded
        }
    }

    private fun createMiniSignature(bitmap: Bitmap): IntArray {
        val size = 8
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val signature = IntArray(size * size * 3)
        var index = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                val color = scaled.getPixel(x, y)
                signature[index++] = ((Color.red(color) / 16f).roundToInt()).coerceIn(0, 15)
                signature[index++] = ((Color.green(color) / 16f).roundToInt()).coerceIn(0, 15)
                signature[index++] = ((Color.blue(color) / 16f).roundToInt()).coerceIn(0, 15)
            }
        }
        if (scaled != bitmap && !scaled.isRecycled) {
            scaled.recycle()
        }
        return signature
    }

    private fun signatureDistance(a: IntArray, b: IntArray): Double {
        if (a.size != b.size) return Double.MAX_VALUE
        var total = 0.0
        for (i in a.indices) {
            total += abs(a[i] - b[i]).toDouble()
        }
        return total / a.size.toDouble()
    }

    fun detectFavoriteStarFilled(bitmap: Bitmap): Boolean {
        val ratio = measureFavoriteStarYellowRatio(bitmap) ?: return false
        return ratio >= 0.16
    }

    fun measureFavoriteStarYellowRatio(bitmap: Bitmap): Double? {
        return try {
            val candidateRects = listOf(
                normalizedBitmapRect(bitmap, 0.84f, 0.96f, 0.03f, 0.13f),
                normalizedBitmapRect(bitmap, 0.85f, 0.97f, 0.05f, 0.16f),
                normalizedBitmapRect(bitmap, 0.86f, 0.98f, 0.06f, 0.18f),
                normalizedBitmapRect(bitmap, 0.82f, 0.98f, 0.00f, 0.18f)
            )
            candidateRects.maxOfOrNull { rect ->
                measureFavoriteYellowRatio(bitmap, rect)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun measureFavoriteYellowRatio(bitmap: Bitmap, rect: Rect): Double {
        val hsv = FloatArray(3)
        var yellow = 0
        var chromaSampled = 0
        var sampled = 0
        bitmap.forEachSampledPixel(rect, 4) { _, _, color ->
            colorToHsv(color, hsv)
            sampled++
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]
            val isFilledYellow = hue in 35f..66f && sat >= 0.45f && value >= 0.70f
            val isChromaPixel = sat >= 0.14f || value <= 0.72f
            if (isFilledYellow) yellow++
            if (isChromaPixel) chromaSampled++
        }
        if (sampled == 0) return 0.0
        val ratioAll = yellow.toDouble() / sampled.toDouble()
        val ratioChroma = if (chromaSampled == 0) 0.0 else yellow.toDouble() / chromaSampled.toDouble()
        return max(ratioAll, ratioChroma)
    }

    private fun calculateColorDistance(c1: Int, c2: Int): Double {
        val r = (Color.red(c1) - Color.red(c2)).toDouble().pow(2.0)
        val g = (Color.green(c1) - Color.green(c2)).toDouble().pow(2.0)
        val b = (Color.blue(c1) - Color.blue(c2)).toDouble().pow(2.0)
        return sqrt(r + g + b)
    }

}
