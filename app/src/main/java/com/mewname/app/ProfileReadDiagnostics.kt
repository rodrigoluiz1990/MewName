package com.mewname.app

import com.mewname.app.domain.TrainerProfileDraft
import com.mewname.app.ocr.OcrResult

internal fun profileReadDiagnostics(
    read: OcrResult?, stage: String, elapsed: Long, language: String,
    draft: TrainerProfileDraft? = null, shown: TrainerProfileDraft? = null,
    error: Throwable? = null
): String = buildString {
    appendLine("MewName — Leitura de perfil | formato=1")
    appendLine("versao=" + BuildConfig.VERSION_NAME + "; build=" + BuildConfig.VERSION_CODE + "; idioma=" + language)
    appendLine("captura=" + java.time.Instant.now() + "; etapa=" + stage + "; duracao_ms=" + elapsed)
    appendLine("imagem=" + read?.bitmap?.let { "" + it.width + "x" + it.height })
    appendLine("blocos=" + read?.blocks?.size + "; linhas=" + read?.blocks?.sumOf { it.lines.size })
    appendLine("DETECCAO -> CARTAO (— = sem evidencia suficiente)")
    fun field(label: String, value: String?, displayed: String?) {
        appendLine(label + ": " + (value ?: "—") + " -> " + (displayed ?: "—"))
    }
    field("nome", draft?.name, shown?.name)
    field("nivel", draft?.level, shown?.level)
    field("equipe", draft?.team, shown?.team)
    field("codigo_amizade", draft?.friendCode, shown?.friendCode)
    appendLine("REGRAS: nome regional x<0.50/y=0.10..0.25; nivel regional x<0.25/y=0.35..0.70.")
    appendLine("Inferencia regional exige marcadores de perfil; equipe exige texto; codigo exige 12 digitos sem ambiguidade.")
    if (error != null) appendLine("ERRO\n" + error.stackTraceToString())
    appendLine("TEXTO OCR INTEGRAL")
    appendLine(read?.fullText ?: "(OCR nao concluido)")
    appendLine("LINHAS: texto | retangulo em pixels | retangulo normalizado")
    read?.blocks?.forEachIndexed { blockIndex, block ->
        block.lines.forEachIndexed { lineIndex, line ->
            val box = line.boundingBox
            val bitmap = read.bitmap
            val region = if (box != null && bitmap != null) {
                listOf(box.left.toFloat() / bitmap.width, box.top.toFloat() / bitmap.height,
                    box.right.toFloat() / bitmap.width, box.bottom.toFloat() / bitmap.height).joinToString(",")
            } else "indisponivel"
            appendLine("" + blockIndex + "." + lineIndex + ": " + line.text + " | " + box + " | " + region)
        }
    }
}