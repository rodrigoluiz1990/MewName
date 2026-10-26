package com.mewname.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.nio.ByteBuffer

/** Owns MediaProjection, its callback, the display and the frame reader. */
internal class OverlayCaptureSession(
    private val context: Context,
    private val onUnavailable: (String) -> Unit
) : AutoCloseable {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var callback: MediaProjection.Callback? = null
    var imageReader: ImageReader? = null
        private set

    fun initialize(data: Intent) {
        if (projection != null && display != null && imageReader != null) return
        close()
        try {
            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val session = checkNotNull(manager.getMediaProjection(Activity.RESULT_OK, data))
            projection = session
            val stopCallback = object : MediaProjection.Callback() {
                override fun onStop() {
                    // Ignore callbacks already queued for a session we have closed.
                    if (projection === session) {
                        close()
                        onUnavailable("Autorizacao de captura encerrada pelo Android")
                    }
                }
            }
            callback = stopCallback
            session.registerCallback(stopCallback, Handler(Looper.getMainLooper()))
            val metrics = context.resources.displayMetrics
            val reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
            imageReader = reader
            display = checkNotNull(session.createVirtualDisplay(
                "CaptureSession", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null
            ))
        } catch (error: Exception) {
            Log.e("OverlayCaptureSession", "Falha ao iniciar sessao de MediaProjection", error)
            close()
            onUnavailable("Falha ao iniciar a captura")
        }
    }

    fun drainPendingFrames(reader: ImageReader) {
        while (true) {
            val stale = try { reader.acquireLatestImage() } catch (_: Exception) { null } ?: return
            stale.close()
        }
    }

    /**
     * Delivers the first frame composed after [afterNanos] (System.nanoTime base).
     * When none arrives within [timeoutMs], delivers the latest frame seen, as the old fixed delay did.
     */
    fun acquireFrameAfter(reader: ImageReader, afterNanos: Long, timeoutMs: Long, handler: Handler, onResult: (Image?) -> Unit) {
        var pending: Image? = null
        var done = false
        // Frames are taken one at a time while the newest one is held, which stays within maxImages.
        fun takeNext(): Image? = try { reader.acquireNextImage() } catch (_: Exception) { null }
        val timeout = Runnable {
            if (done) return@Runnable
            done = true
            reader.setOnImageAvailableListener(null, null)
            while (true) {
                val next = takeNext() ?: break
                pending?.close()
                pending = next
            }
            onResult(pending)
        }
        reader.setOnImageAvailableListener({ source ->
            while (!done) {
                val image = takeNext() ?: break
                pending?.close()
                pending = image
                if (image.timestamp > afterNanos) {
                    done = true
                    handler.removeCallbacks(timeout)
                    source.setOnImageAvailableListener(null, null)
                    onResult(image)
                }
            }
        }, handler)
        handler.postDelayed(timeout, timeoutMs)
    }

    fun copyBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        return copyCaptureBitmap(image.width, image.height, plane.pixelStride, plane.rowStride, plane.buffer)
    }

    override fun close() {
        val oldDisplay = display.also { display = null }
        val oldReader = imageReader.also { imageReader = null }
        val oldProjection = projection.also { projection = null }
        val oldCallback = callback.also { callback = null }
        runCatching { oldDisplay?.release() }
        runCatching { oldReader?.setOnImageAvailableListener(null, null) }
        runCatching { oldReader?.close() }
        // A deliberate close must not be reported as a revoked permission.
        runCatching { if (oldCallback != null) oldProjection?.unregisterCallback(oldCallback) }
        runCatching { oldProjection?.stop() }
    }
}

internal fun copyCaptureBitmap(width: Int, height: Int, pixelStride: Int, rowStride: Int, buffer: ByteBuffer): Bitmap {
    require(width > 0 && height > 0 && pixelStride == 4)
    val rowPadding = rowStride - pixelStride * width
    require(rowPadding >= 0 && rowPadding % pixelStride == 0)
    val raw = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
    try {
        raw.copyPixelsFromBuffer(buffer)
        if (rowPadding == 0) return raw
        val cropped = Bitmap.createBitmap(raw, 0, 0, width, height)
        if (cropped !== raw) raw.recycle()
        return cropped
    } catch (error: Throwable) {
        raw.recycle()
        throw error
    }
}
