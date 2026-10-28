package com.mewname.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.roundToInt

class BackgroundReferenceMatcher {
    data class DebugResult(
        val isSpecial: Boolean?,
        val bestReferenceName: String?,
        val bestDistance: Double?,
        val referenceCount: Int,
        val bestNormalReferenceName: String? = null,
        val bestNormalDistance: Double? = null,
        val bestSpecialReferenceName: String? = null,
        val bestSpecialDistance: Double? = null,
        val specialType: com.mewname.app.model.SpecialBackgroundType? = null,
        /** Best distance to each type background, keyed by lowercase English type ("psychic"). */
        val typeDistances: Map<String, Double> = emptyMap(),
        /** Best distance to a special reference of another category than the closest one. */
        val otherCategoryDistance: Double? = null
    )

    private data class SignatureSample(
        val pixels: IntArray,
        val mask: BooleanArray
    )

    private data class ReferenceSignature(
        val sourceName: String,
        val signature: SignatureSample
    )

    private data class ReferenceVariantSpec(
        val suffix: String,
        val cropLeft: Float,
        val cropTop: Float,
        val cropRight: Float,
        val cropBottom: Float,
        val brightnessMultiplier: Float = 1f,
        val saturationMultiplier: Float = 1f
    )

    fun isSpecialBackground(context: Context, bitmap: Bitmap): Boolean? {
        return debugSpecialBackground(context, bitmap).isSpecial
    }

    fun debugSpecialBackground(context: Context, bitmap: Bitmap): DebugResult {
        val normalReferences = loadNormalReferences(context)

        if (normalReferences.isEmpty()) {
            return DebugResult(
                isSpecial = null,
                bestReferenceName = null,
                bestDistance = null,
                referenceCount = 0
            )
        }

        val candidateSignatures = cropBackgroundCandidates(bitmap).map(::createScreenshotSignature)
        val rankedAllNormal = rankReferences(normalReferences, candidateSignatures)
        // The shadow background is normal only for shadow Pokémon; the caller weighs it with its own evidence.
        val rankedNormal = rankedAllNormal.filterNot { (reference, _) -> SHADOW_REFERENCE_MARKER in reference.sourceName }
        val bestNormal = rankedNormal.firstOrNull()
        val bestNormalDistance = bestNormal?.second
        val specialReferences = loadSpecialReferences(context)
        val rankedSpecial = rankReferences(specialReferences, candidateSignatures)
        val bestSpecial = rankedSpecial.firstOrNull()
        val bestSpecialType = bestSpecial?.let { SpecialBackgroundCatalog.type(it.first.sourceName) }
        val competing = rankedSpecial.firstOrNull { SpecialBackgroundCatalog.type(it.first.sourceName) != bestSpecialType }?.second
        val specialType = bestSpecial?.let { best ->
            // Conservative gate: a named category needs positive evidence, not only dissimilarity to normal.
            bestSpecialType.takeIf {
                best.second <= 1.8 && best.second + 0.8 < (bestNormalDistance ?: 0.0) && best.second + 0.8 < (competing ?: Double.MAX_VALUE)
            }
        }
        val isSpecial = when {
            specialType != null -> true
            bestNormalDistance == null -> null
            bestNormalDistance <= NORMAL_CONFIDENCE_THRESHOLD -> false
            bestNormalDistance >= SPECIAL_OUTLIER_THRESHOLD -> true
            else -> null
        }
        val bestReference = bestNormal?.first?.sourceName
        val bestDistance = bestNormalDistance
        return DebugResult(
            isSpecial = isSpecial,
            bestReferenceName = bestReference,
            bestDistance = bestDistance,
            referenceCount = normalReferences.asSequence().map { it.sourceName }.distinct().count(),
            bestNormalReferenceName = bestNormal?.first?.sourceName,
            bestNormalDistance = bestNormalDistance,
            bestSpecialReferenceName = bestSpecial?.first?.sourceName,
            bestSpecialDistance = bestSpecial?.second,
            specialType = specialType,
            typeDistances = rankedAllNormal
                .groupBy { (reference, _) -> reference.sourceName.substringAfterLast("details_type_bg_").substringBefore('.') }
                .mapValues { (_, ranked) -> ranked.minOf { it.second } },
            otherCategoryDistance = competing
        )
    }

    private fun rankReferences(
        references: List<ReferenceSignature>,
        candidates: List<SignatureSample>
    ): List<Pair<ReferenceSignature, Double>> {
        return references.map { reference ->
            reference to (candidates.minOfOrNull { candidate ->
                signatureDistance(candidate, reference.signature)
            } ?: Double.MAX_VALUE)
        }.sortedBy { it.second }
    }

    private fun loadNormalReferences(context: Context): List<ReferenceSignature> =
        cachedReferences(context, NORMAL_REFS_PATH)

    private fun loadSpecialReferences(context: Context): List<ReferenceSignature> =
        cachedReferences(context, SpecialBackgroundCatalog.PATH) + cachedReferences(context, CALIBRATION_REFS_PATH) +
            learnedReferences(context)

    /** Crops this device learned from copied nicknames; reloaded whenever the store saves a new one. */
    private fun learnedReferences(context: Context): List<ReferenceSignature> {
        val version = LearnedBackgroundStore.version
        learnedCache?.takeIf { it.first == version }?.let { return it.second }
        val loaded = LearnedBackgroundStore.files(context).flatMap { (name, file) ->
            val bitmap = BitmapFactory.decodeFile(file.path) ?: return@flatMap emptyList()
            try {
                REFERENCE_VARIANTS.map { variant -> ReferenceSignature(name, createReferenceSignature(bitmap, variant)) }
            } finally {
                bitmap.recycle()
            }
        }
        learnedCache = version to loaded
        return loaded
    }

    /** Loads every reference set ahead of the first read, which otherwise pays for decoding ~180 images. */
    fun warmUp(context: Context) {
        loadNormalReferences(context)
        loadSpecialReferences(context)
    }

    // Signatures depend only on the bundled assets, so they are shared by every matcher in the
    // process and stored on disk per app version; a restart then skips decoding the images.
    private fun cachedReferences(context: Context, path: String): List<ReferenceSignature> {
        referenceCache[path]?.let { return it }
        synchronized(referenceCache) {
            referenceCache[path]?.let { return it }
            val loaded = readStoredReferences(context, path)
                ?: loadReferenceSet(context, path).also { storeReferences(context, path, it) }
            referenceCache[path] = loaded
            return loaded
        }
    }

    private fun storedReferencesFile(context: Context, path: String): java.io.File =
        java.io.File(context.filesDir, "$SIGNATURE_STORE_DIR/${com.mewname.app.BuildConfig.VERSION_CODE}/${path.replace('/', '_')}.bin")

    private fun readStoredReferences(context: Context, path: String): List<ReferenceSignature>? = runCatching {
        val file = storedReferencesFile(context, path)
        if (!file.isFile) return null
        java.io.DataInputStream(file.inputStream().buffered()).use { input ->
            check(input.readInt() == SIGNATURE_STORE_FORMAT)
            List(input.readInt()) {
                val name = input.readUTF()
                val pixels = IntArray(input.readInt()) { input.readByte().toInt() }
                val mask = BooleanArray(input.readInt()) { input.readBoolean() }
                ReferenceSignature(name, SignatureSample(pixels, mask))
            }
        }
    }.getOrNull()

    private fun storeReferences(context: Context, path: String, references: List<ReferenceSignature>) {
        // An empty set usually means a failed load; keep retrying it instead of persisting it.
        if (references.isEmpty()) return
        runCatching {
            val file = storedReferencesFile(context, path)
            file.parentFile?.parentFile?.listFiles()?.filter { it != file.parentFile }?.forEach { it.deleteRecursively() }
            file.parentFile?.mkdirs()
            val temp = java.io.File(file.path + ".tmp")
            java.io.DataOutputStream(temp.outputStream().buffered()).use { output ->
                output.writeInt(SIGNATURE_STORE_FORMAT)
                output.writeInt(references.size)
                references.forEach { reference ->
                    output.writeUTF(reference.sourceName)
                    output.writeInt(reference.signature.pixels.size)
                    reference.signature.pixels.forEach { output.writeByte(it) }
                    output.writeInt(reference.signature.mask.size)
                    reference.signature.mask.forEach { output.writeBoolean(it) }
                }
            }
            check(temp.renameTo(file))
        }
    }

    private fun loadReferenceSet(context: Context, path: String): List<ReferenceSignature> {
        return runCatching {
            SpecialBackgroundCatalog.files(context, path)
                .filter { fileName ->
                    fileName.endsWith(".png", ignoreCase = true) ||
                        fileName.endsWith(".jpg", ignoreCase = true) ||
                        fileName.endsWith(".jpeg", ignoreCase = true) ||
                        fileName.endsWith(".webp", ignoreCase = true)
                }
                .flatMap { fileName ->
                    context.assets.open(fileName).use { input ->
                        val bitmap = BitmapFactory.decodeStream(input) ?: return@flatMap emptyList()
                        try {
                            REFERENCE_VARIANTS.map { variant ->
                                ReferenceSignature(
                                    sourceName = fileName,
                                    signature = createReferenceSignature(bitmap, variant)
                                )
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
        }.getOrElse { emptyList() }
    }

    private fun cropBackgroundCandidates(bitmap: Bitmap): List<Bitmap> {
        val rects = listOf(
            normalizedRect(bitmap, 0.06f, 0.80f, 0.06f, 0.33f),
            normalizedRect(bitmap, 0.10f, 0.76f, 0.08f, 0.31f),
            normalizedRect(bitmap, 0.08f, 0.78f, 0.07f, 0.29f)
        )
        return rects.distinctBy { listOf(it.left, it.top, it.right, it.bottom) }
            .map { rect -> Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height()) }
    }

    private fun createReferenceSignature(bitmap: Bitmap, variant: ReferenceVariantSpec): SignatureSample {
        val cropped = createVariantBitmap(bitmap, variant)
        return try {
            createMaskedSignature(
                bitmap = cropped,
                ignoreBrightUiArtifacts = false,
                normalizeTone = true
            )
        } finally {
            if (cropped != bitmap) {
                cropped.recycle()
            }
        }
    }

    private fun createScreenshotSignature(bitmap: Bitmap): SignatureSample {
        return try {
            createMaskedSignature(
                bitmap = bitmap,
                ignoreBrightUiArtifacts = true,
                normalizeTone = false
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun createMaskedSignature(
        bitmap: Bitmap,
        ignoreBrightUiArtifacts: Boolean,
        normalizeTone: Boolean
    ): SignatureSample {
        val scaled = Bitmap.createScaledBitmap(bitmap, SIGNATURE_SIZE, SIGNATURE_SIZE, true)
        val pixels = IntArray(SIGNATURE_SIZE * SIGNATURE_SIZE * 3)
        val mask = BooleanArray(SIGNATURE_SIZE * SIGNATURE_SIZE)
        val hsv = FloatArray(3)
        var maskIndex = 0
        var colorIndex = 0

        for (y in 0 until SIGNATURE_SIZE) {
            for (x in 0 until SIGNATURE_SIZE) {
                val color = scaled.getPixel(x, y)
                val nx = x.toFloat() / (SIGNATURE_SIZE - 1).coerceAtLeast(1)
                val ny = y.toFloat() / (SIGNATURE_SIZE - 1).coerceAtLeast(1)
                Color.colorToHSV(color, hsv)
                val include = !isMaskedLayoutPixel(nx, ny) &&
                    !shouldIgnorePixel(color, hsv, ignoreBrightUiArtifacts)
                mask[maskIndex++] = include

                val adjusted = if (normalizeTone) {
                    adjustReferenceColor(hsv)
                } else {
                    color
                }
                pixels[colorIndex++] = ((Color.red(adjusted) / 16f).roundToInt()).coerceIn(0, 15)
                pixels[colorIndex++] = ((Color.green(adjusted) / 16f).roundToInt()).coerceIn(0, 15)
                pixels[colorIndex++] = ((Color.blue(adjusted) / 16f).roundToInt()).coerceIn(0, 15)
            }
        }

        if (scaled != bitmap) {
            scaled.recycle()
        }
        return SignatureSample(pixels = pixels, mask = mask)
    }

    private fun shouldIgnorePixel(color: Int, hsv: FloatArray, ignoreBrightUiArtifacts: Boolean): Boolean {
        if (Color.alpha(color) <= 20) return true
        if (!ignoreBrightUiArtifacts) return false
        val isNearWhiteOverlay = hsv[1] <= 0.12f && hsv[2] >= 0.84f
        val isVeryBrightUi = ((Color.red(color) + Color.green(color) + Color.blue(color)) / 3f) >= 242f
        return isNearWhiteOverlay || isVeryBrightUi
    }

    private fun isMaskedLayoutPixel(nx: Float, ny: Float): Boolean {
        if (ny <= 0.12f) return true
        if (nx <= 0.22f && ny <= 0.22f) return true
        if (nx >= 0.74f && ny <= 0.44f) return true
        if (nx >= 0.80f && ny in 0.18f..0.62f) return true
        if (nx in 0.18f..0.82f && ny >= 0.78f) return true

        val centerDx = (nx - 0.50f) / 0.22f
        val centerDy = (ny - 0.52f) / 0.36f
        if ((centerDx * centerDx) + (centerDy * centerDy) <= 1.0f) return true

        return false
    }

    private fun adjustReferenceColor(hsv: FloatArray): Int {
        val adjusted = hsv.copyOf()
        adjusted[1] = (adjusted[1] * 0.90f).coerceIn(0f, 1f)
        adjusted[2] = (adjusted[2] * 0.88f).coerceIn(0f, 1f)
        return Color.HSVToColor(adjusted)
    }

    private fun createVariantBitmap(bitmap: Bitmap, variant: ReferenceVariantSpec): Bitmap {
        val cropRect = Rect(
            (bitmap.width * variant.cropLeft).roundToInt().coerceIn(0, bitmap.width - 1),
            (bitmap.height * variant.cropTop).roundToInt().coerceIn(0, bitmap.height - 1),
            (bitmap.width * variant.cropRight).roundToInt().coerceIn(1, bitmap.width),
            (bitmap.height * variant.cropBottom).roundToInt().coerceIn(1, bitmap.height)
        )
        val safeRect = Rect(
            cropRect.left,
            cropRect.top,
            cropRect.right.coerceAtLeast(cropRect.left + 1),
            cropRect.bottom.coerceAtLeast(cropRect.top + 1)
        )
        val cropped = Bitmap.createBitmap(bitmap, safeRect.left, safeRect.top, safeRect.width(), safeRect.height())
        if (variant.brightnessMultiplier == 1f && variant.saturationMultiplier == 1f) {
            return cropped
        }

        val adjusted = cropped.copy(Bitmap.Config.ARGB_8888, true)
        cropped.recycle()
        val hsv = FloatArray(3)
        for (y in 0 until adjusted.height) {
            for (x in 0 until adjusted.width) {
                val color = adjusted.getPixel(x, y)
                Color.colorToHSV(color, hsv)
                hsv[1] = (hsv[1] * variant.saturationMultiplier).coerceIn(0f, 1f)
                hsv[2] = (hsv[2] * variant.brightnessMultiplier).coerceIn(0f, 1f)
                adjusted.setPixel(x, y, Color.HSVToColor(Color.alpha(color), hsv))
            }
        }
        return adjusted
    }

    private fun signatureDistance(a: SignatureSample, b: SignatureSample): Double {
        if (a.pixels.size != b.pixels.size || a.mask.size != b.mask.size) return Double.MAX_VALUE
        var total = 0.0
        var count = 0
        var colorIndex = 0
        for (index in a.mask.indices) {
            if (a.mask[index] && b.mask[index]) {
                total += abs(a.pixels[colorIndex] - b.pixels[colorIndex]).toDouble()
                total += abs(a.pixels[colorIndex + 1] - b.pixels[colorIndex + 1]).toDouble()
                total += abs(a.pixels[colorIndex + 2] - b.pixels[colorIndex + 2]).toDouble()
                count += 3
            }
            colorIndex += 3
        }
        return if (count == 0) Double.MAX_VALUE else total / count.toDouble()
    }

    private fun normalizedRect(
        bitmap: Bitmap,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float
    ): Rect {
        val left = (bitmap.width * minX).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (bitmap.height * minY).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = (bitmap.width * maxX).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (bitmap.height * maxY).roundToInt().coerceIn(top + 1, bitmap.height)
        return Rect(left, top, right, bottom)
    }

    private companion object {
        const val NORMAL_REFS_PATH = "background_refs/normal"
        const val CALIBRATION_REFS_PATH = "background_refs/calibration"
        const val SHADOW_REFERENCE_MARKER = "details_type_bg_shadow"
        const val SIGNATURE_STORE_DIR = "background-signatures"
        const val SIGNATURE_STORE_FORMAT = 1
        val referenceCache = java.util.concurrent.ConcurrentHashMap<String, List<ReferenceSignature>>()
        @Volatile var learnedCache: Pair<Int, List<ReferenceSignature>>? = null
        const val SIGNATURE_SIZE = 14
        const val NORMAL_CONFIDENCE_THRESHOLD = 3.25
        const val SPECIAL_OUTLIER_THRESHOLD = 4.25

        val REFERENCE_VARIANTS = listOf(
            ReferenceVariantSpec(
                suffix = "base",
                cropLeft = 0.00f,
                cropTop = 0.00f,
                cropRight = 1.00f,
                cropBottom = 1.00f
            ),
            ReferenceVariantSpec(
                suffix = "inset",
                cropLeft = 0.05f,
                cropTop = 0.05f,
                cropRight = 0.95f,
                cropBottom = 0.95f
            ),
            ReferenceVariantSpec(
                suffix = "lower",
                cropLeft = 0.03f,
                cropTop = 0.10f,
                cropRight = 0.97f,
                cropBottom = 1.00f
            )
        )
    }
}
