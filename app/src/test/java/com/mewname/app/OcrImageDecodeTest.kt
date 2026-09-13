package com.mewname.app

import android.content.Context
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.mewname.app.ocr.OcrEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
class OcrImageDecodeTest {
    @Test fun rotatesImportedBitmapSoVisualAnalysisAndOcrUseTheSameCoordinates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val image = File(context.cacheDir, "ocr-rotated-test.jpg")
        val source = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        try {
            image.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            ExifInterface(image.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val decoded = ReflectionHelpers.callInstanceMethod<Bitmap>(OcrEngine(), "loadBitmap",
                ReflectionHelpers.ClassParameter.from(Context::class.java, context),
                ReflectionHelpers.ClassParameter.from(Uri::class.java, Uri.fromFile(image)))
            assertEquals(20, decoded.width)
            assertEquals(40, decoded.height)
            decoded.recycle()
        } finally {
            source.recycle()
            image.delete()
        }
    }
}