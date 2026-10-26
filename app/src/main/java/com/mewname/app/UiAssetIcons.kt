package com.mewname.app

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Process-wide cache of decoded asset icons. Lists and the Home recompose the same icons
 * on every navigation, so decoding them per composable cost frames on the main thread.
 */
internal object AssetBitmapCache {
    private val cache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun get(context: Context, assetPath: String): ImageBitmap? = cache.get(assetPath)
        ?: runCatching {
            context.assets.open(assetPath).use(BitmapFactory::decodeStream)?.asImageBitmap()
        }.getOrNull()?.also { cache.put(assetPath, it) }
}

@Composable
fun AssetImageIcon(
    assetPath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallbackSize: Dp = 18.dp,
    tint: androidx.compose.ui.graphics.Color? = null,
    colorFilter: androidx.compose.ui.graphics.ColorFilter? = null
) {
    val context = LocalContext.current
    val bitmap = remember(assetPath) { AssetBitmapCache.get(context, assetPath) }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            colorFilter = colorFilter ?: tint?.let { androidx.compose.ui.graphics.ColorFilter.tint(it) },
            modifier = modifier
        )
    } else {
        Icon(
            painter = painterResource(android.R.drawable.ic_menu_report_image),
            contentDescription = contentDescription,
            modifier = modifier.size(fallbackSize),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun HelpIcon(
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Ajuda"
) {
    Icon(
        painter = painterResource(android.R.drawable.ic_menu_help),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    )
}
