package com.mewname.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import com.mewname.app.model.UniqueFormDebugInfo
import kotlin.math.abs
import kotlin.math.roundToInt

class UniquePokemonReferenceMatcher {
    private data class ReferenceSignature(
        val fileName: String,
        val label: String,
        val pixels: IntArray,
        val mask: BooleanArray
    )

    @Volatile
    private var cache: MutableMap<String, List<ReferenceSignature>> = mutableMapOf()

    fun detect(context: Context, bitmap: Bitmap?, pokemonName: String?): Pair<String?, UniqueFormDebugInfo?> {
        val spec = UniquePokemonCatalog.specFor(pokemonName) ?: return null to null
        val screenshot = bitmap ?: return null to UniqueFormDebugInfo(
            category = spec.assetFolder,
            notes = "sem bitmap para comparar com referencias"
        )
        val references = loadReferences(context, spec)
        if (references.isEmpty()) {
            return null to UniqueFormDebugInfo(
                category = spec.assetFolder,
                notes = "nenhuma referencia carregada"
            )
        }

        val crops = candidateRects(screenshot).map { rect ->
            Bitmap.createBitmap(screenshot, rect.left, rect.top, rect.width(), rect.height())
        }
        val ranked = references.map { ref ->
            ref to (crops.minOfOrNull { crop -> compareCropToReference(crop, ref) } ?: Double.MAX_VALUE)
        }.sortedBy { it.second }
        crops.forEach { it.recycle() }

        val best = ranked.firstOrNull()
        val accepted = best != null && best.second <= UNIQUE_MATCH_THRESHOLD
        val debug = UniqueFormDebugInfo(
            category = spec.assetFolder,
            bestReferenceName = best?.first?.fileName,
            bestLabel = best?.first?.label,
            bestDistance = best?.second,
            accepted = accepted,
            notes = buildString {
                append("categoria=${spec.assetFolder}; referencias=${references.size}")
                best?.let {
                    append("; melhor=${it.first.label}")
                    append("; distancia=${"%.3f".format(it.second)}")
                }
                if (!accepted) append("; acima do limiar")
            }
        )
        return if (accepted) best?.first?.label to debug else null to debug
    }

    private fun loadReferences(context: Context, spec: UniquePokemonSpec): List<ReferenceSignature> {
        cache[spec.assetFolder]?.let { return it }
        synchronized(this) {
            cache[spec.assetFolder]?.let { return it }
            val path = "unique_pokemon_refs/${spec.assetFolder}"
            val loaded = runCatching {
                context.assets.list(path)
                    ?.filter { file ->
                        val lower = file.lowercase()
                        (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")) &&
                            !lower.contains("shiny") &&
                            !lower.endsWith("_bg.png")
                    }
                    ?.mapNotNull { file ->
                        context.assets.open("$path/$file").use { input ->
                            val bitmap = BitmapFactory.decodeStream(input) ?: return@mapNotNull null
                            val label = labelForFile(spec, file) ?: return@mapNotNull null
                            val signature = createReferenceSignature(bitmap)
                            bitmap.recycle()
                            ReferenceSignature(
                                fileName = file,
                                label = label,
                                pixels = signature.first,
                                mask = signature.second
                            )
                        }
                    } ?: emptyList()
            }.getOrElse { emptyList() }
            cache[spec.assetFolder] = loaded.toMutableList()
            return loaded
        }
    }

    private fun labelForFile(spec: UniquePokemonSpec, fileName: String): String? {
        val normalized = fileName.substringBeforeLast('.').lowercase()
        return spec.options.firstOrNull { option ->
            normalized.contains(option.label.lowercase().replace(" ", "_")) ||
                normalized.contains(option.label.lowercase().replace(" ", "")) ||
                normalized.contains(option.code.lowercase())
        }?.label ?: when (spec.assetFolder) {
            "genesect" -> when {
                "normal" in normalized -> "Normal"
                "burn" in normalized -> "Burn"
                "chill" in normalized -> "Chill"
                "douse" in normalized -> "Douse"
                "shock" in normalized -> "Shock"
                else -> null
            }
            "rotom" -> when {
                "normal" in normalized -> "Normal"
                "fan" in normalized -> "Fan"
                "frost" in normalized -> "Frost"
                "heat" in normalized -> "Heat"
                "mow" in normalized -> "Mow"
                "wash" in normalized -> "Wash"
                else -> null
            }
            "furfrou" -> spec.options.firstOrNull { normalized.contains(it.label.lowercase().replace(" ", "_")) }?.label
            "spinda" -> when {
                "line1" in normalized -> "Spinda #1"
                "heart" in normalized && "pachiaa" in normalized -> "Spinda #3"
                "pachiaa" in normalized -> "Spinda #2"
                "pachiab" in normalized -> "Spinda #4"
                "pachibc" in normalized -> "Spinda #5"
                "pachibd" in normalized -> "Spinda #6"
                else -> null
            }
            else -> null
        }
    }

    private fun createReferenceSignature(bitmap: Bitmap): Pair<IntArray, BooleanArray> {
        val scaled = Bitmap.createScaledBitmap(bitmap, SIGNATURE_SIZE, SIGNATURE_SIZE, true)
        val pixels = IntArray(SIGNATURE_SIZE * SIGNATURE_SIZE * 3)
        val mask = BooleanArray(SIGNATURE_SIZE * SIGNATURE_SIZE)
        var pixelIndex = 0
        var colorIndex = 0
        for (y in 0 until SIGNATURE_SIZE) {
            for (x in 0 until SIGNATURE_SIZE) {
                val color = scaled.getPixel(x, y)
                val alpha = Color.alpha(color)
                mask[pixelIndex++] = alpha >= 48
                pixels[colorIndex++] = (Color.red(color) / 16f).roundToInt().coerceIn(0, 15)
                pixels[colorIndex++] = (Color.green(color) / 16f).roundToInt().coerceIn(0, 15)
                pixels[colorIndex++] = (Color.blue(color) / 16f).roundToInt().coerceIn(0, 15)
            }
        }
        if (scaled != bitmap) scaled.recycle()
        return pixels to mask
    }

    private fun compareCropToReference(crop: Bitmap, reference: ReferenceSignature): Double {
        val scaled = Bitmap.createScaledBitmap(crop, SIGNATURE_SIZE, SIGNATURE_SIZE, true)
        var total = 0.0
        var count = 0
        var pixelIndex = 0
        var colorIndex = 0
        for (y in 0 until SIGNATURE_SIZE) {
            for (x in 0 until SIGNATURE_SIZE) {
                val color = scaled.getPixel(x, y)
                if (reference.mask[pixelIndex++]) {
                    total += abs((Color.red(color) / 16f).roundToInt() - reference.pixels[colorIndex]).toDouble()
                    total += abs((Color.green(color) / 16f).roundToInt() - reference.pixels[colorIndex + 1]).toDouble()
                    total += abs((Color.blue(color) / 16f).roundToInt() - reference.pixels[colorIndex + 2]).toDouble()
                    count += 3
                }
                colorIndex += 3
            }
        }
        if (scaled != crop) scaled.recycle()
        return if (count == 0) Double.MAX_VALUE else total / count.toDouble()
    }

    private fun candidateRects(bitmap: Bitmap): List<Rect> {
        return listOf(
            normalizedRect(bitmap, 0.16f, 0.82f, 0.10f, 0.54f),
            normalizedRect(bitmap, 0.20f, 0.78f, 0.08f, 0.50f),
            normalizedRect(bitmap, 0.14f, 0.86f, 0.12f, 0.58f)
        ).distinctBy { listOf(it.left, it.top, it.right, it.bottom) }
    }

    private fun normalizedRect(bitmap: Bitmap, minX: Float, maxX: Float, minY: Float, maxY: Float): Rect {
        val left = (bitmap.width * minX).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (bitmap.height * minY).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = (bitmap.width * maxX).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (bitmap.height * maxY).roundToInt().coerceIn(top + 1, bitmap.height)
        return Rect(left, top, right, bottom)
    }

    private companion object {
        const val SIGNATURE_SIZE = 40
        const val UNIQUE_MATCH_THRESHOLD = 4.2
    }
}
