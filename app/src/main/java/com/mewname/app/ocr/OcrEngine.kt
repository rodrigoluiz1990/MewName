package com.mewname.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrEngine {
    suspend fun extract(context: Context, imageUri: Uri): OcrResult = suspendCancellableCoroutine { cont ->
        val image = runCatching { InputImage.fromFilePath(context, imageUri) }
            .getOrElse {
                cont.resumeWithException(it)
                return@suspendCancellableCoroutine
            }

        processImage(image, loadBitmap(context, imageUri), cont)
    }

    suspend fun extract(bitmap: Bitmap): OcrResult = suspendCancellableCoroutine { cont ->
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
        return runCatching {
            context.contentResolver.openInputStream(imageUri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
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
