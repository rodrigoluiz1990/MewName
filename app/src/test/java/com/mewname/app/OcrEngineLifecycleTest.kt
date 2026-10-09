package com.mewname.app

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Looper
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import com.mewname.app.ocr.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
class OcrEngineLifecycleTest {
    @org.junit.Before
    fun initializeMlKit() {
        com.google.mlkit.common.sdkinternal.MlKitContext.initializeIfNeeded(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>())
    }

    private class Client {
        val tasks = mutableListOf<TaskCompletionSource<Text>>()
        val images = mutableListOf<InputImage>()
        var closes = 0
        var creates = 0
        val recognizer = Proxy.newProxyInstance(TextRecognizer::class.java.classLoader,
            arrayOf(TextRecognizer::class.java)) { _, method, args ->
            when (method.name) {
                "process" -> TaskCompletionSource<Text>().also {
                    tasks += it
                    images += args!![0] as InputImage
                }.task
                "close" -> { closes++; null }
                else -> null
            }
        } as TextRecognizer
        fun engine() = OcrEngine { creates++; recognizer }
        fun complete(index: Int) {
            tasks[index].setResult(Text("", emptyList<Text.TextBlock>()))
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun `successive reads reuse client and close only at owner shutdown`() = runBlocking {
        val client = Client()
        val engine = client.engine()
        val bitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        repeat(2) { index ->
            val read = async(start = CoroutineStart.UNDISPATCHED) { engine.extractText(bitmap) }
            client.complete(index)
            assertSame(bitmap, read.await().bitmap)
        }
        assertEquals(1, client.creates)
        assertEquals(0, client.closes)
        engine.close()
        engine.close()
        assertEquals(1, client.closes)
        assertFalse(bitmap.isRecycled)
    }

    @Test
    fun `cancelled crop stays alive until native task completes and does not close other reads`() = runBlocking {
        val client = Client()
        val engine = client.engine()
        val bitmap = Bitmap.createBitmap(1000, 2000, Bitmap.Config.ARGB_8888)
        val line = OcrTextLine("XS", Rect(800, 1000, 850, 1030))
        val original = OcrResult("XS", bitmap, listOf(OcrTextBlock("XS", line.boundingBox, listOf(line))))
        val cropRead = launch(start = CoroutineStart.UNDISPATCHED) { engine.refineSizeBadges(original) }
        val crop = client.images[0].bitmapInternal!!
        cropRead.cancelAndJoin()
        assertFalse(crop.isRecycled)
        assertEquals(0, client.closes)
        val nextRead = async(start = CoroutineStart.UNDISPATCHED) { engine.extractText(bitmap) }
        engine.close()
        assertEquals(0, client.closes)
        client.complete(0)
        assertTrue(crop.isRecycled)
        assertFalse(bitmap.isRecycled)
        assertEquals(0, client.closes)
        client.complete(1)
        nextRead.await()
        assertEquals(1, client.creates)
        assertEquals(1, client.closes)
    }

    @Test
    fun `failed crop preserves successful screen OCR and releases temporary bitmap`() = runBlocking {
        val client = Client()
        val engine = client.engine()
        val bitmap = Bitmap.createBitmap(1000, 2000, Bitmap.Config.ARGB_8888)
        val line = OcrTextLine("XL", Rect(800, 1000, 850, 1030))
        val original = OcrResult("XL", bitmap, listOf(OcrTextBlock("XL", line.boundingBox, listOf(line))))
        val read = async(start = CoroutineStart.UNDISPATCHED) { engine.refineSizeBadges(original) }
        val crop = client.images[0].bitmapInternal!!
        client.tasks[0].setException(IllegalStateException("Test failure"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(original, read.await())
        assertTrue(crop.isRecycled)
        assertFalse(bitmap.isRecycled)
        engine.close()
        assertEquals(1, client.closes)
    }
}
