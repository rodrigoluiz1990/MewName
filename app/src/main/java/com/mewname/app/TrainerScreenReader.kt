package com.mewname.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.AtomicFile
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.mewname.app.domain.TrainerProfileDraft
import com.mewname.app.domain.TrainerProfileLine
import com.mewname.app.domain.TrainerProfileParser
import com.mewname.app.domain.TrainerTeamBackgroundDetector
import com.mewname.app.ocr.OcrResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.io.File

internal object TrainerScreenReader {
    fun parse(context: Context, read: OcrResult): TrainerProfileDraft {
        val image = read.bitmap
        val regions = if (image == null) emptyList() else read.blocks.flatMap { it.lines }.mapNotNull { line ->
            line.boundingBox?.let { TrainerProfileLine(line.text, it.left.toFloat() / image.width, it.top.toFloat() / image.height) }
        }
        val parsed = TrainerProfileParser.parse(read.fullText, regions)
        val visualTeam = if (parsed.team == null && image != null) {
            TrainerTeamBackgroundDetector.detect(context, image)
        } else {
            null
        }
        return if (visualTeam == null) parsed else parsed.copy(team = visualTeam)
    }

    fun save(context: Context, draft: TrainerProfileDraft) {
        val prefs = context.getSharedPreferences("trainer_profile", 0)
        prefs.edit().apply {
            draft.name?.let { putString("name", it) }
            draft.level?.let { putString("level", it) }
            draft.team?.let { putString("team", it) }
            draft.friendCode?.let { putString("code", it) }
        }.apply()
    }

    suspend fun saveQr(context: Context, read: OcrResult): Boolean {
        val bitmap = read.bitmap ?: return false
        val rect = withTimeoutOrNull(6000L) {
            suspendCancellableCoroutine<Rect?> { continuation ->
                val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
                scanner.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { codes ->
                        if (continuation.isActive) continuation.resume(codes.singleOrNull()?.boundingBox)
                    }.addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
                    .addOnCompleteListener { scanner.close() }
            }
        } ?: return false
        return withContext(Dispatchers.IO) {
            val margin = (maxOf(rect.width(), rect.height()) * .12f).toInt()
            val left = (rect.left - margin).coerceAtLeast(0)
            val top = (rect.top - margin).coerceAtLeast(0)
            val right = (rect.right + margin).coerceAtMost(bitmap.width)
            val bottom = (rect.bottom + margin).coerceAtMost(bitmap.height)
            if (right <= left || bottom <= top) return@withContext false
            val crop = Bitmap.createBitmap(bitmap, left, top, right-left, bottom-top)
            val file = AtomicFile(File(context.filesDir, "profile-qr.png"))
            try {
                val output = file.startWrite()
                try { check(crop.compress(Bitmap.CompressFormat.PNG, 100, output)); file.finishWrite(output) }
                catch (error: Exception) { file.failWrite(output); throw error }
            } finally { if (crop !== bitmap) crop.recycle() }
            true
        }
    }
}