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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OcrImageDecodeTest {
    @Test fun downsamplesLargeImagesBeforeApplyingExifRotation() {
        assertDecodedSize(4800, 2400, ExifInterface.ORIENTATION_ROTATE_90, 1200, 2400)
    }

    @Test fun limitsPixelCountEvenWhenBothDimensionsFitTheDimensionLimit() {
        assertDecodedSize(3000, 3000, ExifInterface.ORIENTATION_NORMAL, 1500, 1500)
    }

    @Test fun preservesTypicalPhoneScreenshotResolution() {
        assertDecodedSize(1080, 2400, ExifInterface.ORIENTATION_NORMAL, 1080, 2400)
    }

    @Test fun rejectsUndecodableImages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "ocr-invalid.jpg")
        try {
            file.writeText("not an image")
            assertNull(ReflectionHelpers.callInstanceMethod<Bitmap?>(OcrEngine(), "loadBitmap",
                ReflectionHelpers.ClassParameter.from(Context::class.java, context),
                ReflectionHelpers.ClassParameter.from(Uri::class.java, Uri.fromFile(file))))
        } finally {
            file.delete()
        }
    }

    private fun assertDecodedSize(width: Int, height: Int, orientation: Int, expectedWidth: Int, expectedHeight: Int) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "ocr-size-test.jpg")
        val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            source.recycle()
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
            val decoded = ReflectionHelpers.callInstanceMethod<Bitmap>(OcrEngine(), "loadBitmap",
                ReflectionHelpers.ClassParameter.from(Context::class.java, context),
                ReflectionHelpers.ClassParameter.from(Uri::class.java, Uri.fromFile(file)))
            try {
                assertEquals(expectedWidth, decoded.width)
                assertEquals(expectedHeight, decoded.height)
            } finally {
                decoded.recycle()
            }
        } finally {
            if (!source.isRecycled) source.recycle()
            file.delete()
        }
    }

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