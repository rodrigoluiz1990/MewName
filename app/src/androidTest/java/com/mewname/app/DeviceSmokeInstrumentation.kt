package com.mewname.app

import android.app.Instrumentation
import android.app.Activity
import android.os.Bundle
import android.net.Uri
import android.media.ExifInterface
import com.mewname.app.ocr.OcrEngine
import com.mewname.app.domain.OcrPokemonParser
import kotlinx.coroutines.runBlocking
import java.io.File

/** Device-only diagnostics; run with -PtestRunner=com.mewname.app.DeviceSmokeInstrumentation. */
class DeviceSmokeInstrumentation : Instrumentation() {
    private var options = Bundle()
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        options = arguments ?: Bundle()
        start()
    }

    override fun onStart() {
        val output = StringBuilder()
        val success = runCatching {
            runBlocking {
                OcrEngine().use { engine ->
                    val parser = OcrPokemonParser()
                    val file = File(targetContext.cacheDir, options.getString("fixture", "device-pony-exif.jpg"))
                    val exif = ExifInterface(file.absolutePath)
                    output.appendLine("EXIF orientation=" + exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
                    val result = engine.extract(targetContext, Uri.fromFile(file))
                    val lines = org.json.JSONArray()
                    result.blocks.flatMap { it.lines }.forEach { line ->
                        lines.put(org.json.JSONObject().put("text", line.text).put("rect", line.boundingBox?.let {
                            org.json.JSONArray(listOf(it.left, it.top, it.right, it.bottom))
                        }))
                    }
                    File(targetContext.cacheDir, "device-ocr-lines.json").writeText(lines.toString())
                    output.appendLine("OCR=" + result.fullText.replace("\n", " | "))
                    val bitmap = requireNotNull(result.bitmap)
                    try {
                        output.appendLine("Decoded=" + bitmap.width + "x" + bitmap.height)
                        val data = parser.parse(targetContext, result)
                        output.appendLine("Name=" + data.pokemonName + "; CP=" + data.cp + "; IV=" + data.attIv + "/" + data.defIv + "/" + data.staIv + "; favorite=" + data.isFavorite + "; lucky=" + data.isLucky)
                        output.appendLine("Bars=" + data.ivDebugInfo)
                        check(data.pokemonName == "Ponyta")
                        check(data.cp == 1521)
                        check(data.attIv == 12 && data.defIv == 14 && data.staIv == 15)
                        check(data.isFavorite && data.isLucky)
                    } finally { bitmap.recycle() }
                }
            }
        }
        success.exceptionOrNull()?.let { output.appendLine(it.stackTraceToString()) }
        finish(if (success.isSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("stream", output.toString()) })
    }
}
