package com.mewname.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import com.mewname.app.model.NormalizedDebugRect
import com.mewname.app.model.UniqueFormDebugInfo
import kotlin.math.abs
import kotlin.math.roundToInt

class UniquePokemonReferenceMatcher {
    private enum class ReferenceKind {
        SPRITE,
        FALLBACK_ICON
    }

    private data class ReferenceSignature(
        val fileName: String,
        val label: String,
        val kind: ReferenceKind,
        val pixels: IntArray,
        val mask: BooleanArray
    )

    @Volatile
    private var cache: MutableMap<String, List<ReferenceSignature>> = mutableMapOf()

    fun detect(
        context: Context,
        bitmap: Bitmap?,
        pokemonName: String?,
        allowedLabels: Set<String> = emptySet()
    ): Pair<String?, UniqueFormDebugInfo?> {
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
        val normalizedAllowedLabels = allowedLabels
            .mapNotNull { label -> label.trim().takeIf { it.isNotEmpty() }?.lowercase() }
            .toSet()
        val candidateReferences = if (normalizedAllowedLabels.isEmpty()) {
            references
        } else {
            references.filter { ref -> ref.label.lowercase() in normalizedAllowedLabels }
                .ifEmpty { references }
        }

        val rects = candidateRects(screenshot, spec)
        val crops = rects.map { rect ->
            Bitmap.createBitmap(screenshot, rect.left, rect.top, rect.width(), rect.height())
        }
        val spriteRefs = candidateReferences.filter { it.kind == ReferenceKind.SPRITE }
        val fallbackRefs = candidateReferences.filter { it.kind == ReferenceKind.FALLBACK_ICON }
        val spriteRanked = rankReferences(spriteRefs, crops, rects)
        val fallbackRanked = rankReferences(fallbackRefs, crops, rects)
        crops.forEach { it.recycle() }

        val spriteBest = spriteRanked.firstOrNull()
        val matchThreshold = matchThresholdFor(spec)
        val spriteAccepted = spriteBest != null && spriteBest.second <= matchThreshold
        val fallbackBest = fallbackRanked.firstOrNull()
        val fallbackAccepted = fallbackBest != null && fallbackBest.second <= matchThreshold
        val best = when {
            spriteAccepted -> spriteBest
            fallbackAccepted -> fallbackBest
            spriteBest != null -> spriteBest
            else -> fallbackBest
        }
        val accepted = when {
            spriteAccepted -> true
            fallbackAccepted -> true
            else -> false
        }
        val sourceLabel = when (best?.first?.kind) {
            ReferenceKind.SPRITE -> "sprite"
            ReferenceKind.FALLBACK_ICON -> "fallback_icon"
            null -> "-"
        }
        val debug = UniqueFormDebugInfo(
            category = spec.assetFolder,
            bestReferenceName = best?.first?.fileName,
            bestLabel = best?.first?.label,
            bestDistance = best?.second,
            accepted = accepted,
            bestCandidateRect = best?.third?.let { normalizeRect(screenshot, it) },
            candidateRects = rects.map { normalizeRect(screenshot, it) },
            notes = buildString {
                append("categoria=${spec.assetFolder}; referencias=${references.size}; referenciasAtivas=${candidateReferences.size}; sprites=${spriteRefs.size}; fallbacks=${fallbackRefs.size}; origem=${sourceLabel}; threshold=${"%.3f".format(matchThreshold)}")
                if (normalizedAllowedLabels.isNotEmpty()) {
                    append("; filtro=${allowedLabels.joinToString("/")}")
                }
                best?.let {
                    append("; melhor=${it.first.label}")
                    append("; distancia=${"%.3f".format(it.second)}")
                }
                if (!spriteAccepted && fallbackAccepted) append("; fallback acionado")
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
                                kind = referenceKindForFile(file),
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
                "fan" in normalized || "ventilador" in normalized -> "Ventilador"
                "frost" in normalized || "congelante" in normalized -> "Congelante"
                "heat" in normalized || "calor" in normalized -> "Calor"
                "mow" in normalized || "corte" in normalized -> "Corte"
                "wash" in normalized || "lavagem" in normalized -> "Lavagem"
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
            "unown" -> when {
                "qm" in normalized || "qu" in normalized || "question" in normalized -> "?"
                "em" in normalized || "ex" in normalized || "exclamation" in normalized -> "!"
                else -> Regex("""(?:^|[-_])([a-z])$""").find(normalized)?.groupValues?.getOrNull(1)?.uppercase()
            }
            else -> null
        }
    }

    private fun referenceKindForFile(fileName: String): ReferenceKind {
        val normalized = fileName.substringBeforeLast('.').lowercase()
        return when {
            normalized.startsWith("ic_") -> ReferenceKind.FALLBACK_ICON
            normalized.endsWith("_icon") -> ReferenceKind.FALLBACK_ICON
            normalized.contains("icon") -> ReferenceKind.FALLBACK_ICON
            else -> ReferenceKind.SPRITE
        }
    }

    private fun rankReferences(
        references: List<ReferenceSignature>,
        crops: List<Bitmap>,
        rects: List<Rect>
    ): List<Triple<ReferenceSignature, Double, Rect?>> {
        return references.map { ref ->
            val bestCropIndex = crops.indices.minByOrNull { index -> compareCropToReference(crops[index], ref) }
            val bestDistance = bestCropIndex?.let { compareCropToReference(crops[it], ref) } ?: Double.MAX_VALUE
            Triple(ref, bestDistance, bestCropIndex?.let { rects[it] })
        }.sortedBy { it.second }
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

    private fun candidateRects(bitmap: Bitmap, spec: UniquePokemonSpec): List<Rect> {
        val normalizedCandidates = when (spec.assetFolder) {
            "furfrou" -> listOf(
                floatArrayOf(0.28f, 0.60f, 0.10f, 0.40f),
                floatArrayOf(0.30f, 0.58f, 0.12f, 0.38f),
                floatArrayOf(0.26f, 0.62f, 0.11f, 0.42f)
            )
            "genesect" -> listOf(
                floatArrayOf(0.24f, 0.64f, 0.10f, 0.42f),
                floatArrayOf(0.26f, 0.62f, 0.12f, 0.40f),
                floatArrayOf(0.22f, 0.66f, 0.11f, 0.44f)
            )
            "rotom" -> listOf(
                floatArrayOf(0.30f, 0.62f, 0.10f, 0.36f),
                floatArrayOf(0.32f, 0.60f, 0.12f, 0.34f),
                floatArrayOf(0.28f, 0.64f, 0.11f, 0.38f)
            )
            "spinda" -> listOf(
                floatArrayOf(0.32f, 0.58f, 0.12f, 0.32f),
                floatArrayOf(0.30f, 0.60f, 0.11f, 0.34f),
                floatArrayOf(0.34f, 0.56f, 0.13f, 0.31f)
            )
            "unown" -> unownCandidateBounds(bitmap)
            else -> listOf(
                floatArrayOf(0.26f, 0.62f, 0.10f, 0.40f),
                floatArrayOf(0.28f, 0.60f, 0.12f, 0.38f),
                floatArrayOf(0.24f, 0.64f, 0.11f, 0.42f)
            )
        }
        return normalizedCandidates.map { bounds ->
            normalizedRect(bitmap, bounds[0], bounds[1], bounds[2], bounds[3])
        }.distinctBy { listOf(it.left, it.top, it.right, it.bottom) }
    }

    private fun normalizedRect(bitmap: Bitmap, minX: Float, maxX: Float, minY: Float, maxY: Float): Rect {
        val left = (bitmap.width * minX).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (bitmap.height * minY).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = (bitmap.width * maxX).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (bitmap.height * maxY).roundToInt().coerceIn(top + 1, bitmap.height)
        return Rect(left, top, right, bottom)
    }

    private fun unownCandidateBounds(bitmap: Bitmap): List<FloatArray> {
        val dynamic = detectUnownBodyRect(bitmap)
        val dynamicBounds = dynamic?.let { rect ->
            listOf(
                rectToBounds(bitmap, expandRect(bitmap, rect, 0.28f, 0.22f)),
                rectToBounds(bitmap, expandRect(bitmap, rect, 0.18f, 0.12f)),
                rectToBounds(bitmap, expandRect(bitmap, rect, 0.36f, 0.28f))
            )
        }.orEmpty()
        val fallback = listOf(
            floatArrayOf(0.42f, 0.58f, 0.08f, 0.22f),
            floatArrayOf(0.40f, 0.60f, 0.07f, 0.24f),
            floatArrayOf(0.44f, 0.56f, 0.09f, 0.21f),
            floatArrayOf(0.39f, 0.61f, 0.08f, 0.25f),
            floatArrayOf(0.41f, 0.59f, 0.10f, 0.24f)
        )
        return (dynamicBounds + fallback).distinctBy { bounds -> bounds.joinToString(",") }
    }

    private fun detectUnownBodyRect(bitmap: Bitmap): Rect? {
        val searchRect = normalizedRect(bitmap, 0.30f, 0.70f, 0.07f, 0.30f)
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var hits = 0
        val hsv = FloatArray(3)
        var y = searchRect.top
        while (y < searchRect.bottom) {
            var x = searchRect.left
            while (x < searchRect.right) {
                val color = bitmap.getPixel(x, y)
                Color.colorToHSV(color, hsv)
                val maxChannel = maxOf(Color.red(color), Color.green(color), Color.blue(color))
                val isDarkBody = maxChannel <= 72 || (hsv[2] <= 0.24f && hsv[1] <= 0.38f)
                if (isDarkBody) {
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                    hits++
                }
                x += 2
            }
            y += 2
        }
        if (hits < 80 || minX == Int.MAX_VALUE || minY == Int.MAX_VALUE) return null
        return Rect(
            minX.coerceIn(0, bitmap.width - 2),
            minY.coerceIn(0, bitmap.height - 2),
            (maxX + 1).coerceIn(minX + 1, bitmap.width),
            (maxY + 1).coerceIn(minY + 1, bitmap.height)
        )
    }

    private fun expandRect(bitmap: Bitmap, rect: Rect, horizontalPaddingRatio: Float, verticalPaddingRatio: Float): Rect {
        val padX = (rect.width() * horizontalPaddingRatio).roundToInt().coerceAtLeast(8)
        val padY = (rect.height() * verticalPaddingRatio).roundToInt().coerceAtLeast(8)
        val left = (rect.left - padX).coerceIn(0, bitmap.width - 1)
        val top = (rect.top - padY).coerceIn(0, bitmap.height - 1)
        val right = (rect.right + padX).coerceIn(left + 1, bitmap.width)
        val bottom = (rect.bottom + padY).coerceIn(top + 1, bitmap.height)
        return Rect(left, top, right, bottom)
    }

    private fun rectToBounds(bitmap: Bitmap, rect: Rect): FloatArray {
        return floatArrayOf(
            rect.left.toFloat() / bitmap.width,
            rect.right.toFloat() / bitmap.width,
            rect.top.toFloat() / bitmap.height,
            rect.bottom.toFloat() / bitmap.height
        )
    }

    private fun normalizeRect(bitmap: Bitmap, rect: Rect): NormalizedDebugRect {
        return NormalizedDebugRect(
            left = rect.left.toFloat() / bitmap.width,
            top = rect.top.toFloat() / bitmap.height,
            right = rect.right.toFloat() / bitmap.width,
            bottom = rect.bottom.toFloat() / bitmap.height
        )
    }

    private companion object {
        const val SIGNATURE_SIZE = 40
        const val UNIQUE_MATCH_THRESHOLD = 4.2
    }

    private fun matchThresholdFor(spec: UniquePokemonSpec): Double {
        return when (spec.assetFolder) {
            "unown" -> 3.85
            else -> UNIQUE_MATCH_THRESHOLD
        }
    }
}
