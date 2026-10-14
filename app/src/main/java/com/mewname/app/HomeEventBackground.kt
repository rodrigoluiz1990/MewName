package com.mewname.app

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.background
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mewname.app.domain.*
import kotlinx.coroutines.*
import java.time.LocalDate

@Composable
internal fun HomeEventBackground(carousel: HomeEventCarouselState, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val language = appLanguage()
    val locale = when (language) { AppLanguage.PT_BR -> "pt-BR"; AppLanguage.ES -> "es"; else -> "en" }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val repository = remember(context) { HomeEventArtRepository(context) }
    val events by produceState<List<HomeEventArt>>(emptyList(), repository, locale, lifecycle) {
        var catalog = withContext(Dispatchers.IO) { repository.cached(locale) }
        var lastAttempt = 0L
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = HomeEventArtParser.active(catalog?.events.orEmpty(), LocalDate.now())
                val now = System.currentTimeMillis()
                if ((catalog == null || now - catalog!!.fetchedAt >= 6 * 60 * 60 * 1000L) && now - lastAttempt >= 60 * 60 * 1000L) {
                    lastAttempt = now
                    try { catalog = repository.refresh(locale) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { android.util.Log.w("HomeEventArt", "Keeping cached event art", error) }
                    value = HomeEventArtParser.active(catalog?.events.orEmpty(), LocalDate.now())
                }
                delay(60_000)
            }
        }
    }
    val appearance = LocalAppAppearance.current
    DisposableEffect(Unit) { onDispose { carousel.count = 0; carousel.imageHeight = 0f } }
    LaunchedEffect(events) { carousel.count = events.size; carousel.index = 0; carousel.lastManual = 0L }
    LaunchedEffect(lifecycle, events) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { carousel.index = 0; awaitCancellation() }
    }
    LaunchedEffect(events, appearance.eventAutoRotate, appearance.eventIntervalSeconds, carousel.lastManual, lifecycle) {
        if (events.size <= 1 || !appearance.eventAutoRotate) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val remainingPause = if (carousel.lastManual == 0L) 0L else 30_000L - (android.os.SystemClock.elapsedRealtime() - carousel.lastManual)
                delay(maxOf(appearance.eventIntervalSeconds * 1000L, remainingPause))
                carousel.move(1, manual = false)
            }
        }
    }
    val selected = events.getOrNull(carousel.index) ?: events.firstOrNull() ?: return
    Box(modifier) {
        Crossfade(selected, animationSpec = tween(600), label = "eventArt") { art ->
            HomeEventArtwork(art, Modifier.fillMaxSize()) { carousel.imageHeight = it }
        }
        if (events.size > 1) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            Row(Modifier.align(Alignment.TopCenter)
                .padding(top = with(density) { carousel.imageHeight.toDp() }.minus(12.dp).coerceAtLeast(0.dp))
                .width((events.size * 24 - 4).coerceAtMost(180).dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(events.size) { index ->
                    Box(Modifier.weight(1f).height(2.dp)
                        .background(Color.White.copy(alpha = if (index == carousel.index) .85f else .25f), CircleShape))
                }
            }
        }
    }
}

@Composable
private fun HomeEventArtwork(selected: HomeEventArt, modifier: Modifier, onHeight: (Float) -> Unit) {
    val context = LocalContext.current.applicationContext
    val appearance = LocalAppAppearance.current
    val dark = appearance.dark || appearance.homeLayout == HomeLayout.ATMOSPHERIC
    val shade = if (dark) Color(0xFF151B32) else appearance.background.first()
    var loaded by remember(selected.image) { mutableStateOf(false) }
    var aspectRatio by remember(selected.image) { mutableFloatStateOf(16f / 9f) }
    var edgeColor by remember(selected.image) { mutableStateOf(shade) }
    val request = remember(selected.image, context) {
        ImageRequest.Builder(context).data(selected.image).allowHardware(false).build()
    }
    BoxWithConstraints(modifier) {
        // Keep the whole landscape artwork at the top instead of cropping it to a portrait screen.
        val imageFraction = ((maxWidth / aspectRatio) / maxHeight).coerceIn(.01f, 1f)
        if (loaded) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to edgeColor, imageFraction to edgeColor,
            1f to lerp(edgeColor, shade, .85f))))
        Box(Modifier.fillMaxWidth().aspectRatio(aspectRatio).onSizeChanged { onHeight(it.height.toFloat()) }.align(Alignment.TopCenter)) {
            AsyncImage(request, selected.title, Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                onSuccess = { state ->
                    val drawable = state.result.drawable
                    if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0)
                        aspectRatio = drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight
                    val bitmap = (drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                        // A few samples from the lower edge create a matching continuation, once per load.
                        var red = 0L; var green = 0L; var blue = 0L
                        repeat(32) { index ->
                            val pixel = bitmap.getPixel((index * bitmap.width / 32).coerceAtMost(bitmap.width - 1),
                                (bitmap.height * .96f).toInt().coerceAtMost(bitmap.height - 1))
                            red += android.graphics.Color.red(pixel)
                            green += android.graphics.Color.green(pixel)
                            blue += android.graphics.Color.blue(pixel)
                        }
                        edgeColor = lerp(Color(android.graphics.Color.rgb((red / 32).toInt(),
                            (green / 32).toInt(), (blue / 32).toInt())), shade, .20f)
                    }
                    loaded = true
                }, onError = { loaded = false })
            if (loaded) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                0f to shade.copy(alpha = .15f), .72f to Color.Transparent, 1f to edgeColor)))
        }
    }
}