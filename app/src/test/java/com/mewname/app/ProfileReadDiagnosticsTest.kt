package com.mewname.app

import android.graphics.Bitmap
import android.graphics.Rect
import com.mewname.app.domain.TrainerProfileDraft
import com.mewname.app.ocr.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileReadDiagnosticsTest {
    @Test fun noMatchesStillExportsTextAndCoordinates() {
        val image = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        try {
            val read = OcrResult("RodWhite001\n72", image,
                listOf(OcrTextBlock("RodWhite001", null,
                    listOf(OcrTextLine("RodWhite001", Rect(10, 20, 70, 40))))))
            val log = profileReadDiagnostics(read, "sem_campos", 120, "PT_BR",
                TrainerProfileDraft(), TrainerProfileDraft(name = "Anterior"))
            assertTrue(log.contains("RodWhite001\n72"))
            assertTrue(log.contains("0.1,0.1,0.7,0.2"))
            assertTrue(log.contains("nome: — -> Anterior"))
            assertTrue(log.contains("etapa=sem_campos"))
        } finally { image.recycle() }
    }
    @Test fun failuresBeforeOcrAreExportable() {
        val log = profileReadDiagnostics(null, "decodificar_e_ler_ocr", 20000, "PT_BR",
            error = IllegalStateException("decode failed"))
        assertTrue(log.contains("decode failed"))
        assertTrue(log.contains("OCR nao concluido"))
        assertTrue(log.contains("duracao_ms=20000"))
    }
}