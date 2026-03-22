package com.mewname.app.ocr

import android.graphics.Bitmap
import android.graphics.Rect

data class OcrResult(
    val fullText: String,
    val bitmap: Bitmap?,
    val blocks: List<OcrTextBlock>
)

data class OcrTextBlock(
    val text: String,
    val boundingBox: Rect?,
    val lines: List<OcrTextLine>
)

data class OcrTextLine(
    val text: String,
    val boundingBox: Rect?
)
