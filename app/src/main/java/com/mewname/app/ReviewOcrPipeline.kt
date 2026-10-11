package com.mewname.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.mewname.app.domain.OcrPokemonParser
import com.mewname.app.model.PokemonScreenData
import com.mewname.app.ocr.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout

data class ReviewOcrResult(
    val rawText: String,
    val bitmap: Bitmap?,
    val data: PokemonScreenData
)

interface ReviewOcrPipeline : AutoCloseable {
    override fun close() {}
    suspend fun process(
        context: Context,
        uri: Uri,
        onStatusChange: (String) -> Unit
    ): ReviewOcrResult
}

internal class AndroidReviewOcrPipeline(
    private val ocrEngine: OcrEngine = OcrEngine(),
    private val parser: OcrPokemonParser = OcrPokemonParser()
) : ReviewOcrPipeline {
    override fun close() = ocrEngine.close()
    override suspend fun process(
        context: Context,
        uri: Uri,
        onStatusChange: (String) -> Unit
    ): ReviewOcrResult {
        val ocrResult = withTimeout(20_000L) {
            ocrEngine.extract(context, uri)
        }
        val parsed = withTimeout(25_000L) {
            kotlinx.coroutines.runInterruptible(Dispatchers.Default) {
                parser.parse(context, ocrResult, onStatusChange)
            }
        }
        return ReviewOcrResult(
            rawText = ocrResult.fullText,
            bitmap = ocrResult.bitmap,
            data = parsed
        )
    }
}