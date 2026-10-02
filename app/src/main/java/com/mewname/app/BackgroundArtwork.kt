package com.mewname.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/** Static catalog previews assembled from the game's original textures; never OCR references. */
@Composable
internal fun BackgroundArtwork(assetPath: String, contentDescription: String?, modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit) {
    val context = LocalContext.current
    val composed = assetPath.substringAfterLast('/') in setOf("sb_Community_2026.png", "sb_10thAnniversary_Mewtwo.png", "sb_GoTour2026_mega.png")
    val bitmap by produceState<Bitmap?>(null, assetPath) {
        if (composed) value = withContext(Dispatchers.IO) { BackgroundPreviewRenderer.render(context, assetPath) }
    }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription, modifier, contentScale = contentScale) }
        ?: coil.compose.AsyncImage(model = "file:///android_asset/$assetPath", contentDescription = contentDescription,
            modifier = modifier, contentScale = contentScale)
}

internal object BackgroundPreviewRenderer {
    private val cache = android.util.LruCache<String, Bitmap>(3)
    @Synchronized fun render(context: Context, assetPath: String): Bitmap {
        cache.get(assetPath)?.let { return it }
        val sources = mutableListOf<Bitmap>()
        fun load(path: String): Bitmap = context.assets.open(path).use { requireNotNull(BitmapFactory.decodeStream(it)) }.also { sources.add(it) }
        fun layer(name: String) = load("background_layers/$name.png")
        val output = Bitmap.createBitmap(384, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        fun draw(bitmap: Bitmap, left: Float, top: Float, width: Float, height: Float = width) {
            canvas.drawBitmap(bitmap, null, RectF(left, top, left + width, top + height), paint)
        }
        try {
            canvas.drawColor(Color.BLACK)
            draw(load(assetPath), 0f, 0f, 384f, 480f)
            when (assetPath.substringAfterLast('/')) {
                "sb_Community_2026.png" -> draw(layer("tx_sb_community2026_pokeball"), 51f, 28f, 282f)
                "sb_10thAnniversary_Mewtwo.png" -> {
                    val globe = projectGlobe(layer("globe_day"))
                    draw(globe, 52f, 68f, 296f)
                    globe.recycle()
                    draw(layer("tx_globe_glow_rim"), 30f, 46f, 340f)
                    draw(layer("tx_logo_10thanni"), 12f, 16f, 110f)
                }
                "sb_GoTour2026_mega.png" -> {
                    val mask = layer("tx_sb_mega_icon_mask")
                    val rainbow = layer("mega_rainbow_gradient")
                    val colors = IntArray(mask.width * mask.height)
                    mask.getPixels(colors, 0, mask.width, 0, 0, mask.width, mask.height)
                    for (y in 0 until mask.height) for (x in 0 until mask.width) {
                        val i = y * mask.width + x
                        val color = rainbow.getPixel(x * (rainbow.width - 1) / (mask.width - 1), rainbow.height / 2)
                        colors[i] = (Color.alpha(colors[i]) shl 24) or (color and 0xffffff)
                    }
                    val symbol = Bitmap.createBitmap(colors, mask.width, mask.height, Bitmap.Config.ARGB_8888)
                    paint.alpha = 45
                    draw(layer("tx_sb_mega_icon_glow"), 76f, 72f, 232f)
                    paint.alpha = 255
                    draw(symbol, 82f, 78f, 220f)
                    draw(layer("tx_sb_mega_icon"), 82f, 78f, 220f)
                    symbol.recycle()
                    val random = java.util.Random(2026)
                    repeat(28) {
                        paint.color = Color.argb(130, 207, 162, 63)
                        canvas.drawCircle(random.nextFloat() * 384f, random.nextFloat() * 480f, 2f + random.nextFloat() * 5f, paint)
                    }
                }
            }
            cache.put(assetPath, output)
            return output
        } catch (error: Exception) {
            output.recycle()
            throw error
        } finally { sources.forEach { it.recycle() } }
    }

    private fun projectGlobe(texture: Bitmap): Bitmap {
        val size = 296
        val pixels = IntArray(size * size)
        val source = IntArray(texture.width * texture.height)
        texture.getPixels(source, 0, texture.width, 0, 0, texture.width, texture.height)
        for (y in 0 until size) for (x in 0 until size) {
            val nx = (x + .5 - size / 2.0) / (size / 2.0)
            val ny = (y + .5 - size / 2.0) / (size / 2.0)
            val r = nx * nx + ny * ny
            if (r > 1) continue
            val nz = sqrt(1 - r)
            val u = ((atan2(nx, nz) / (2 * PI) + .5) * (texture.width - 1)).toInt()
            val v = ((asin(ny) / PI + .5) * (texture.height - 1)).toInt()
            val color = source[v * texture.width + u]
            val light = (.20 + .80 * (-.4 * nx - .3 * ny + .85 * nz).coerceIn(0.0, 1.0))
            pixels[y * size + x] = Color.rgb((Color.red(color) * light).toInt(), (Color.green(color) * light).toInt(), (Color.blue(color) * light).toInt())
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
