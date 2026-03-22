package com.mewname.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class OcrEngine {
    suspend fun extract(context: Context, imageUri: Uri): OcrResult = suspendCoroutine { cont ->
        val image = runCatching { InputImage.fromFilePath(context, imageUri) }
            .getOrElse {
                cont.resumeWithException(it)
                return@suspendCoroutine
            }

        processImage(image, loadBitmap(context, imageUri), cont)
    }

    suspend fun extract(bitmap: Bitmap): OcrResult = suspendCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        processImage(image, bitmap, cont)
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
        cont: kotlin.coroutines.Continuation<OcrResult>
    ) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
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
}
