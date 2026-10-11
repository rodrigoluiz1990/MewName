package com.mewname.app

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverlayCaptureBitmapTest {
    @Test fun frameWithoutPaddingRemainsUsableAfterCopy() = verifyFrame(2)
    @Test fun paddedFramePreservesRowsAndRemovesExtraColumns() = verifyFrame(3)

    private fun verifyFrame(strideWidth: Int) {
        val original = Bitmap.createBitmap(strideWidth, 2, Bitmap.Config.ARGB_8888)
        original.eraseColor(Color.MAGENTA)
        original.setPixel(0, 0, Color.RED)
        original.setPixel(1, 0, Color.GREEN)
        original.setPixel(0, 1, Color.BLUE)
        original.setPixel(1, 1, Color.WHITE)
        val bytes = ByteBuffer.allocate(original.byteCount)
        original.copyPixelsToBuffer(bytes)
        original.recycle()
        bytes.rewind()
        val result = copyCaptureBitmap(2, 2, 4, strideWidth * 4, bytes)
        try {
            assertFalse(result.isRecycled)
            assertEquals(2, result.width)
            assertEquals(2, result.height)
            assertEquals(Color.RED, result.getPixel(0, 0))
            assertEquals(Color.GREEN, result.getPixel(1, 0))
            assertEquals(Color.BLUE, result.getPixel(0, 1))
            assertEquals(Color.WHITE, result.getPixel(1, 1))
        } finally { result.recycle() }
    }
}
