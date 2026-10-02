package com.mewname.app

import android.app.Instrumentation
import android.os.Bundle
import android.graphics.Bitmap
import com.mewname.app.domain.SpecialBackgroundCatalog
import java.io.File

/** Exercises the actual Android renderer with packaged original textures. */
class BackgroundPreviewInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            val files = SpecialBackgroundCatalog.files(targetContext)
            for (name in listOf("sb_Community_2026.png", "sb_10thAnniversary_Mewtwo.png", "sb_GoTour2026_mega.png")) {
                val path = files.single { it.endsWith("/$name") }
                val bitmap = BackgroundPreviewRenderer.render(targetContext, path)
                check(bitmap.width == 384 && bitmap.height == 480)
                check(BackgroundPreviewRenderer.render(targetContext, path) === bitmap)
                File(targetContext.filesDir, "preview-$name").outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            }
            result.putString("stream", "PASS: three background previews rendered and cached.\n")
            finish(-1, result)
        } catch (error: Exception) {
            result.putString("stream", error.stackTraceToString())
            finish(0, result)
        }
    }
}
