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

class OcrEngine {
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
        val original = extractRaw(bitmap)
        var result = original
        for (line in original.blocks.flatMap { it.lines }.filter {
            sizeBadgeCrop(it, bitmap.width, bitmap.height) != null
        }.take(2)) {
            val rect = sizeBadgeCrop(line, bitmap.width, bitmap.height) ?: continue
            try {
                val cropped = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
                val enlarged = Bitmap.createScaledBitmap(cropped, rect.width() * 4, rect.height() * 4, true)
                val detail = extractRaw(enlarged)
                val corrected = confirmedSizeBadge(line.text, detail.blocks.flatMap { it.lines }.map { it.text })
                    ?: continue
                val blocks = result.blocks.map { block ->
                    val lines = block.lines.map { if (it == line) it.copy(text = corrected) else it }
                    if (lines == block.lines) block else block.copy(text = lines.joinToString("\n") { it.text }, lines = lines)
                }
                result = result.copy(blocks = blocks, fullText = blocks.joinToString("\n") { it.text })
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed detail read must not discard the successful full-screen OCR.
            }
        }
        return result
    }

    private suspend fun extractRaw(bitmap: Bitmap): OcrResult = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        processImage(image, bitmap, cont)
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
        cont: kotlinx.coroutines.CancellableContinuation<OcrResult>
    ) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        cont.invokeOnCancellation {
            recognizer.close()
        }
        recognizer.process(image)
            .addOnSuccessListener { result ->
                if (!cont.isActive) {
                    recognizer.close()
                    return@addOnSuccessListener
                }
                val blocks = result.textBlocks.map { block ->
                    OcrTextBlock(
                        text = block.text.orEmpty(),
                        boundingBox = block.boundingBox,
                        lines = block.lines.map { line ->
                            OcrTextLine(
                                text = line.text.orEmpty(),
                                boundingBox = line.boundingBox
                            )
                        }
                    )
                }
                cont.resume(
                    OcrResult(
                        fullText = result.text.orEmpty(),
                        bitmap = bitmap,
                        blocks = blocks
                    )
                )
                recognizer.close()
            }
            .addOnFailureListener { error ->
                if (!cont.isActive) {
                    recognizer.close()
                    return@addOnFailureListener
                }
                cont.resumeWithException(error)
                recognizer.close()
            }
    }

    private fun loadBitmap(context: Context, imageUri: Uri): Bitmap? {
        val decoded = context.contentResolver.openInputStream(imageUri)?.use {
            BitmapFactory.decodeStream(it)
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
