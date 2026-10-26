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
        detectedTypes: List<String>
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
            .any { normalized ->
                normalized.contains("SHADOW") ||
                    normalized.contains("SOMBROSO")
            }
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
        val luckyMatch = luckyTextMatch || luckyVisualMatch
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
        val detected = (
            referenceDebug?.specialType != null || (!luckyMatch &&
                (textMatch || topRegionMatch || eventBadgeSpecialMatch || referenceSpecialMatch || colorOverrideSpecial))
            )
        return detected to BackgroundDebugInfo(
            detectedSpecialType = referenceDebug?.specialType.takeIf { detected },
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
            var y = region.top
            while (y < region.bottom) {
                var x = region.left
                while (x < region.right) {
                    val color = bitmap.getPixel(x, y)
                    Color.colorToHSV(color, hsv)
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
                    x += 2
                }
                y += 2
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
            var y = region.top
            while (y < region.bottom) {
                var x = region.left
                while (x < region.right) {
                    val color = bitmap.getPixel(x, y)
                    Color.colorToHSV(color, hsv)
                    sampled++
                    val hue = hsv[0]
                    val sat = hsv[1]
                    val value = hsv[2]
                    val warmGold = ((hue in 28f..62f) || hue < 16f) && sat >= 0.22f && value >= 0.72f
                    if (warmGold) goldenBright++
                    x += 6
                }
                y += 6
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
            var y = region.top
            while (y < region.bottom) {
                var x = region.left
                while (x < region.right) {
                    if (x < bitmap.width * 0.86f || y > bitmap.height * 0.10f) {
                        val color = bitmap.getPixel(x, y)
                        Color.colorToHSV(color, hsv)
                        sampled++
                        val nearWhite = hsv[1] <= 0.12f && hsv[2] >= 0.92f
                        if (nearWhite) brightWhite++
                    }
                    x += 8
                }
                y += 8
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
                var y = region.top
                while (y < region.bottom) {
                    var x = region.left
                    while (x < region.right) {
                        val color = bitmap.getPixel(x, y)
                        Color.colorToHSV(color, hsv)
                        sampled++
                        val hue = hsv[0]
                        val sat = hsv[1]
                        val value = hsv[2]
                        val shadowLike = (hue in 250f..330f) && sat >= 0.18f && value in 0.14f..0.78f
                        if (shadowLike) purpleDark++
                        x += 5
                    }
                    y += 5
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
        var y = rect.top
        while (y < rect.bottom) {
            var x = rect.left
            while (x < rect.right) {
                val color = bitmap.getPixel(x, y)
                Color.colorToHSV(color, hsv)
                sampled++
                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]
                val isFilledYellow = hue in 35f..66f && sat >= 0.45f && value >= 0.70f
                val isChromaPixel = sat >= 0.14f || value <= 0.72f
                if (isFilledYellow) yellow++
                if (isChromaPixel) chromaSampled++
                x += 4
            }
            y += 4
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
