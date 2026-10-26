package com.mewname.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrEngine internal constructor(
    private val recognizerFactory: () -> com.google.mlkit.vision.text.TextRecognizer
) : AutoCloseable {
    constructor() : this({ TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) })
    private val recognizerLock = Any()
    private var recognizer: com.google.mlkit.vision.text.TextRecognizer? = null
    private var activeReads = 0
    private var closed = false

    override fun close() = synchronized(recognizerLock) {
        closed = true
        if (activeReads == 0) {
            recognizer?.close()
            recognizer = null
        }
    }

    private fun acquireRecognizer() = synchronized(recognizerLock) {
        check(!closed) { "OCR engine is closed" }
        val client = recognizer ?: recognizerFactory().also { recognizer = it }
        activeReads++
        client
    }

    private fun releaseRecognizer() = synchronized(recognizerLock) {
        activeReads--
        if (closed && activeReads == 0) {
            recognizer?.close()
            recognizer = null
        }
    }

    suspend fun extract(context: Context, imageUri: Uri): OcrResult {
        // Decode off the UI thread, once. OCR coordinates and visual analysis share
        // the same correctly oriented bitmap, including imported rotated images.
        val bitmap = withContext(Dispatchers.IO) {
            loadBitmap(context, imageUri)
                ?: throw IllegalArgumentException("Could not decode image")
        }
        return extract(bitmap)
    }
    suspend fun extract(bitmap: Bitmap): OcrResult {
        return refineSizeBadges(refinePokemonCp(extractText(bitmap)))
    }

    /** Full-resolution OCR without Pokemon-only size retries, for screen routing. */
    suspend fun extractText(bitmap: Bitmap): OcrResult = extractRaw(bitmap)

    suspend fun refineSizeBadges(original: OcrResult, onDetailRead: () -> Unit = {}): OcrResult {
        val bitmap = original.bitmap ?: return original
        var result = original
        for (line in original.blocks.flatMap { it.lines }.filter {
            sizeBadgeCrop(it, bitmap.width, bitmap.height) != null
        }.take(2)) {
            val rect = sizeBadgeCrop(line, bitmap.width, bitmap.height) ?: continue
            onDetailRead()
            val detail = readSizeDetail(bitmap, rect) ?: continue
            val corrected = confirmedSizeBadge(line.text, detail.blocks.flatMap { it.lines }.map { it.text })
                ?: continue
            val blocks = result.blocks.map { block ->
                val lines = block.lines.map { if (it == line) it.copy(text = corrected) else it }
                if (lines == block.lines) block else block.copy(text = lines.joinToString("\n") { it.text }, lines = lines)
            }
            result = result.copy(blocks = blocks, fullText = blocks.joinToString("\n") { it.text })
        }
        val missingBadgeRect = missingSizeBadgeCrop(result.blocks.flatMap { it.lines }, bitmap.width, bitmap.height)
        if (missingBadgeRect != null) {
            onDetailRead()
            val detail = readSizeDetail(bitmap, missingBadgeRect)
            val lines = detail?.blocks?.flatMap { it.lines }.orEmpty()
            val badge = unambiguousSizeBadge(lines.map { it.text })
            val bounds = lines.firstOrNull { it.text.trim().equals(badge, ignoreCase = true) }?.boundingBox
            if (badge != null && bounds != null) {
                val mapped = android.graphics.Rect(
                    missingBadgeRect.left + bounds.left / 4,
                    missingBadgeRect.top + bounds.top / 4,
                    missingBadgeRect.left + (bounds.right + 3) / 4,
                    missingBadgeRect.top + (bounds.bottom + 3) / 4
                )
                val block = OcrTextBlock(badge, mapped, listOf(OcrTextLine(badge, mapped)))
                val blocks = result.blocks + block
                result = result.copy(blocks = blocks, fullText = blocks.joinToString("\n") { it.text })
            }
        }
        return result
    }

    /** Retry only the detail CP header, requiring a complete labelled reading. */
    suspend fun refinePokemonCp(original: OcrResult): OcrResult {
        val bitmap = original.bitmap ?: return original
        val bounds = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
        val reader = com.mewname.app.domain.OcrNumericReader
        if (reader.extractCp(original.fullText, original.blocks.flatMap { it.lines }, bounds) != null) return original
        val rect = android.graphics.Rect((bitmap.width * .25f).toInt(), (bitmap.height * .035f).toInt(),
            (bitmap.width * .72f).toInt(), (bitmap.height * .13f).toInt())
        val detail = readSizeDetail(bitmap, rect) ?: return original
        val candidates = detail.blocks.flatMap { it.lines }.mapNotNull { line ->
            val match = reader.cpRegex.matchEntire(line.text.trim()) ?: return@mapNotNull null
            val cp = match.groupValues[1].toIntOrNull()?.takeIf { it in 10..10000 } ?: return@mapNotNull null
            val box = line.boundingBox ?: return@mapNotNull null
            OcrTextLine("PC $cp", android.graphics.Rect(rect.left + box.left / 4, rect.top + box.top / 4,
                rect.left + (box.right + 3) / 4, rect.top + (box.bottom + 3) / 4))
        }
        if (com.mewname.app.BuildConfig.DEBUG) android.util.Log.d("PokemonCpRead", "header=${detail.blocks.flatMap { it.lines }.map { it.text }}; accepted=${candidates.map { it.text }}")
        if (candidates.map { it.text }.distinct().size != 1) {
            val fragments = detail.blocks.flatMap { it.lines }.mapNotNull { line ->
                if (reader.partialCpDigits(line.text) == null) return@mapNotNull null
                val box = line.boundingBox ?: return@mapNotNull null
                val mapped = android.graphics.Rect(rect.left + box.left / 4, rect.top + box.top / 4,
                    rect.left + (box.right + 3) / 4, rect.top + (box.bottom + 3) / 4)
                OcrTextBlock(line.text, mapped, listOf(OcrTextLine(line.text, mapped)))
            }
            // Preserve the observed fragment as evidence; never label it as a complete PC.
            return original.copy(blocks = original.blocks + fragments)
        }
        val line = candidates.first()
        val blocks = original.blocks + OcrTextBlock(line.text, line.boundingBox, listOf(line))
        return original.copy(blocks = blocks, fullText = blocks.joinToString("\n") { it.text })
    }

    /** A bounded second pass on the boss digits, only requested after an uncertain raid reading. */
    suspend fun refineRaidCp(original: OcrResult): OcrResult {
        val bitmap = original.bitmap ?: return original
        val rect = android.graphics.Rect((bitmap.width*.15f).toInt(), (bitmap.height*.16f).toInt(),
            (bitmap.width*.88f).toInt(), (bitmap.height*.28f).toInt())
        val detail = readSizeDetail(bitmap, rect) ?: return original
        val candidates = detail.blocks.flatMap { it.lines }.mapNotNull { line ->
            val bounds = line.boundingBox ?: return@mapNotNull null
            val cp = com.mewname.app.domain.RaidBossResolver.parseBossCp(line.text) ?: return@mapNotNull null
            OcrTextLine(cp.toString(), android.graphics.Rect(rect.left+bounds.left/4, rect.top+bounds.top/4,
                rect.left+(bounds.right+3)/4, rect.top+(bounds.bottom+3)/4))
        }
        if (candidates.map { it.text }.distinct().size != 1) return original
        val blocks = original.blocks.mapNotNull { block ->
            val lines = block.lines.filterNot { line ->
                val box = line.boundingBox
                box != null && box.centerY() in rect.top..rect.bottom &&
                    com.mewname.app.domain.RaidBossResolver.parseBossCp(line.text) != null
            }
            if (lines.isEmpty()) null else block.copy(text = lines.joinToString("\n") { it.text }, lines = lines)
        } + OcrTextBlock(candidates.joinToString("\n") { it.text }, rect, candidates)
        if (com.mewname.app.BuildConfig.DEBUG) android.util.Log.d("RaidReadTiming", "cpDetail readings=${candidates.map { it.text }} crop=$rect")
        return original.copy(blocks = blocks, fullText = blocks.joinToString("\n") { it.text })
    }

    /** Retry the name independently of the digits; preserve original evidence on failure. */
    suspend fun refineRaidName(original: OcrResult): OcrResult {
        val bitmap = original.bitmap ?: return original
        val rect = android.graphics.Rect((bitmap.width*.18f).toInt(), (bitmap.height*.26f).toInt(),
            (bitmap.width*.82f).toInt(), (bitmap.height*.39f).toInt())
        val detail = readSizeDetail(bitmap, rect) ?: return original
        val lines = detail.blocks.flatMap { it.lines }.mapNotNull { line ->
            val bounds = line.boundingBox ?: return@mapNotNull null
            if (line.text.count { it.isLetter() } < 3) return@mapNotNull null
            OcrTextLine(line.text, android.graphics.Rect(rect.left+bounds.left/4, rect.top+bounds.top/4,
                rect.left+(bounds.right+3)/4, rect.top+(bounds.bottom+3)/4))
        }
        if (com.mewname.app.BuildConfig.DEBUG) android.util.Log.d("RaidReadTiming", "nameDetail lines=${lines.map { it.text }}")
        if (lines.isEmpty()) return original
        val blocks = original.blocks + OcrTextBlock(lines.joinToString("\n") { it.text }, rect, lines)
        return original.copy(blocks=blocks, fullText=blocks.joinToString("\n") { it.text })
    }

    private suspend fun readSizeDetail(bitmap: Bitmap, rect: android.graphics.Rect): OcrResult? {
        var cropped: Bitmap? = null
        var enlarged: Bitmap? = null
        var handedToRecognizer = false
        fun recycleDetail() {
            if (enlarged !== cropped && enlarged !== bitmap) enlarged?.recycle()
            if (cropped !== bitmap) cropped?.recycle()
        }
        return try {
            cropped = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
            enlarged = Bitmap.createScaledBitmap(cropped, rect.width() * 4, rect.height() * 4, true)
            handedToRecognizer = true
            extractRaw(enlarged, ::recycleDetail).copy(bitmap = null)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A failed detail read must not discard the successful full-screen OCR.
            null
        } finally {
            // ML Kit may still be reading after coroutine cancellation. The task's
            // completion callback owns these bitmaps once submitted.
            if (!handedToRecognizer) recycleDetail()
        }
    }
    private suspend fun extractRaw(bitmap: Bitmap, onFinished: () -> Unit = {}): OcrResult = suspendCancellableCoroutine { cont ->
        val image = try {
            InputImage.fromBitmap(bitmap, 0)
        } catch (error: Exception) {
            onFinished()
            cont.resumeWithException(error)
            return@suspendCancellableCoroutine
        }
        processImage(image, bitmap, cont, onFinished)
    }

    suspend fun extractPreview(bitmap: Bitmap, maxLongSide: Int = 1280): OcrResult = suspendCancellableCoroutine { cont ->
        val preview = scaledForPreview(bitmap, maxLongSide)
        val image = InputImage.fromBitmap(preview, 0)
        processImage(image, preview, cont)
    }

    suspend fun extractBattlePreview(bitmap: Bitmap): OcrResult = suspendCancellableCoroutine { cont ->
        val cropped = cropBattleTextRegion(bitmap)
        val preview = scaledForPreview(cropped, maxLongSide = 720)
        val image = InputImage.fromBitmap(preview, 0)
        processImage(image, preview, cont)
    }

    suspend fun loadBitmapFromAsset(context: Context, assetPath: String): Bitmap? {
        return runCatching {
            context.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }

    private fun processImage(
        image: InputImage,
        bitmap: Bitmap?,
        cont: kotlinx.coroutines.CancellableContinuation<OcrResult>,
        onFinished: () -> Unit = {}
    ) {
        val client = try {
            acquireRecognizer()
        } catch (error: Exception) {
            onFinished()
            if (cont.isActive) cont.resumeWithException(error)
            return
        }
        val task = try {
            client.process(image)
        } catch (error: Exception) {
            releaseRecognizer()
            onFinished()
            if (cont.isActive) cont.resumeWithException(error)
            return
        }
        // Cancelling one read must not close a client shared with later reads.
        // Keep its image alive until ML Kit actually finishes, even after timeout.
        task.addOnCompleteListener { completed ->
            try {
                if (cont.isActive) {
                    if (completed.isSuccessful) {
                        val result = completed.result
                        val blocks = result.textBlocks.map { block ->
                            OcrTextBlock(block.text.orEmpty(), block.boundingBox,
                                block.lines.map { line -> OcrTextLine(line.text.orEmpty(), line.boundingBox) })
                        }
                        cont.resume(OcrResult(result.text.orEmpty(), bitmap, blocks))
                    } else {
                        cont.resumeWithException(completed.exception ?: IllegalStateException("OCR task cancelled"))
                    }
                }
            } finally {
                onFinished()
                releaseRecognizer()
            }
        }
    }

    private fun loadBitmap(context: Context, imageUri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(imageUri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        // Keep typical phone screenshots intact, but bound very long and square images.
        // Rotation may temporarily require a second bitmap of the same size.
        var sample = 1
        while (true) {
            val width = (bounds.outWidth.toLong() + sample - 1) / sample
            val height = (bounds.outHeight.toLong() + sample - 1) / sample
            if (maxOf(width, height) <= 4096 && width * height <= 8_000_000L) break
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(imageUri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        val orientation = runCatching {
            context.contentResolver.openInputStream(imageUri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull()
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        if (matrix.isIdentity) return decoded
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }
    private fun scaledForPreview(bitmap: Bitmap, maxLongSide: Int): Bitmap {
        val longSide = maxOf(bitmap.width, bitmap.height)
        if (longSide <= maxLongSide) return bitmap
        val scale = maxLongSide.toFloat() / longSide.toFloat()
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun cropBattleTextRegion(bitmap: Bitmap): Bitmap {
        val top = (bitmap.height * 0.16f).toInt().coerceIn(0, bitmap.height - 1)
        val bottom = (bitmap.height * 0.96f).toInt().coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, 0, top, bitmap.width, bottom - top)
    }
}
