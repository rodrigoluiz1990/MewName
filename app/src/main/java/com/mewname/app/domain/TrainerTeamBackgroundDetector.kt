package com.mewname.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal object TrainerTeamBackgroundDetector {
    private data class TeamReference(val team: String, val asset: String)
    private data class HueSample(val hue: Float, val saturation: Float, val value: Float)
    private data class ZoneScore(val score: Double, val samples: Int)

    private val references = listOf(
        TeamReference("Instinct", "Teams/raid_leaderboard_generic_instinct.png"),
        TeamReference("Mystic", "Teams/raid_leaderboard_generic_mystic.png"),
        TeamReference("Valor", "Teams/raid_leaderboard_generic_valor.png")
    )

    @Volatile
    private var cachedPalettes: Map<String, HueSample>? = null

    fun detect(context: Context, bitmap: Bitmap): String? {
        if (bitmap.width < 120 || bitmap.height < 200) return null
        val palettes = cachedPalettes ?: synchronized(this) {
            cachedPalettes ?: loadPalettes(context).also { cachedPalettes = it }
        }
        if (palettes.size != references.size) return null

        val scores = palettes.mapValues { (_, palette) ->
            val rails = scoreZone(bitmap, palette, 0f, 1f, 0.03f, 0.72f, railsOnly = true)
            val symbolField = scoreZone(bitmap, palette, 0.12f, 0.98f, 0.01f, 0.43f, railsOnly = false)
            if (rails.samples < 20 || symbolField.samples < 80) Double.NEGATIVE_INFINITY
            else rails.score * 0.68 + symbolField.score * 0.32
        }.entries.sortedByDescending { it.value }

        val best = scores.getOrNull(0) ?: return null
        val second = scores.getOrNull(1) ?: return null
        return best.key.takeIf {
            best.value >= 0.54 && best.value - second.value >= 0.075
        }
    }

    private fun loadPalettes(context: Context): Map<String, HueSample> = buildMap {
        references.forEach { reference ->
            val bitmap = runCatching {
                context.assets.open(reference.asset).use(BitmapFactory::decodeStream)
            }.getOrNull() ?: return@forEach
            try {
                dominantHue(bitmap)?.let { put(reference.team, it) }
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun dominantHue(bitmap: Bitmap): HueSample? {
        val bins = Array(36) { mutableListOf<HueSample>() }
        val step = max(1, minOf(bitmap.width, bitmap.height) / 80)
        val hsv = FloatArray(3)
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val color = bitmap.getPixel(x, y)
                if (Color.alpha(color) < 180) continue
                Color.colorToHSV(color, hsv)
                if (hsv[1] < 0.28f || hsv[2] < 0.28f) continue
                bins[(hsv[0] / 10f).toInt().coerceIn(0, 35)] += HueSample(hsv[0], hsv[1], hsv[2])
            }
        }
        val dominant = bins.maxByOrNull { it.size }.orEmpty()
        if (dominant.isEmpty()) return null
        val radians = dominant.map { Math.toRadians(it.hue.toDouble()) }
        val hue = Math.toDegrees(kotlin.math.atan2(radians.sumOf(::sin), radians.sumOf(::cos)))
            .let { if (it < 0) it + 360.0 else it }.toFloat()
        return HueSample(hue, dominant.map { it.saturation }.average().toFloat(), dominant.map { it.value }.average().toFloat())
    }

    private fun scoreZone(
        bitmap: Bitmap,
        palette: HueSample,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        railsOnly: Boolean
    ): ZoneScore {
        val x0 = (bitmap.width * left).toInt().coerceIn(0, bitmap.width - 1)
        val x1 = (bitmap.width * right).toInt().coerceIn(x0 + 1, bitmap.width)
        val y0 = (bitmap.height * top).toInt().coerceIn(0, bitmap.height - 1)
        val y1 = (bitmap.height * bottom).toInt().coerceIn(y0 + 1, bitmap.height)
        val step = max(2, minOf(bitmap.width, bitmap.height) / 150)
        val hsv = FloatArray(3)
        var total = 0.0
        var count = 0
        for (y in y0 until y1 step step) {
            for (x in x0 until x1 step step) {
                if (railsOnly && x > bitmap.width * 0.055f && x < bitmap.width * 0.945f) continue
                val color = bitmap.getPixel(x, y)
                Color.colorToHSV(color, hsv)
                val minimumSaturation = if (railsOnly) 0.32f else 0.075f
                val minimumValue = if (railsOnly) 0.30f else 0.58f
                if (hsv[1] < minimumSaturation || hsv[2] < minimumValue) continue
                val hueDistance = circularHueDistance(hsv[0], palette.hue)
                val hueScore = (1.0 - hueDistance / 82.0).coerceIn(0.0, 1.0)
                val saturationPenalty = if (railsOnly) abs(hsv[1] - palette.saturation) * 0.12 else 0.0
                total += (hueScore - saturationPenalty).coerceIn(0.0, 1.0)
                count++
            }
        }
        return ZoneScore(if (count == 0) 0.0 else total / count, count)
    }

    private fun circularHueDistance(first: Float, second: Float): Float {
        val direct = abs(first - second)
        return minOf(direct, 360f - direct)
    }
}
