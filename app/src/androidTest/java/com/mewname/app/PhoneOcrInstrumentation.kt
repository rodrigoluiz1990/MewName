package com.mewname.app

import android.app.Instrumentation
import android.os.Bundle
import android.graphics.BitmapFactory
import com.mewname.app.ocr.OcrEngine
import com.mewname.app.domain.OcrPokemonParser
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

class PhoneOcrInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            val bitmap = requireNotNull(BitmapFactory.decodeFile(targetContext.filesDir.resolve("capture.png").path))
            val ocr = runBlocking { OcrEngine().extract(bitmap) }
            val lines = JSONArray()
            ocr.blocks.flatMap { it.lines }.forEach { line ->
                lines.put(JSONObject().put("text", line.text).put("rect", line.boundingBox?.let {
                    JSONArray(listOf(it.left, it.top, it.right, it.bottom))
                }))
            }
            targetContext.filesDir.resolve("ocr.json").writeText(lines.toString(2))
            val data = OcrPokemonParser().parse(targetContext, ocr)
            result.putString("stream", "IV=" + data.attIv + "/" + data.defIv + "/" + data.staIv +
                "\nSIZE=" + data.size + "\nIV_DEBUG=" + data.ivDebugInfo +
                "\nSIZE_DEBUG=" + data.sizeDebugInfo + "\n")
            finish(-1, result)
        } catch (error: Throwable) {
            result.putString("stream", error.stackTraceToString())
            finish(0, result)
        }
    }
}