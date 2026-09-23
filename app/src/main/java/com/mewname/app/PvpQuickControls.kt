package com.mewname.app

import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.mewname.app.domain.PvpCalculationOptions

internal fun toggledReviewMode(current: ReviewIvMode, target: ReviewIvMode): ReviewIvMode =
    if (current == target) ReviewIvMode.NORMAL else target

@Composable
internal fun PvpQuickControls(
    ivMode: ReviewIvMode, options: PvpCalculationOptions,
    onModeChange: (ReviewIvMode) -> Unit, onOptionsChange: (PvpCalculationOptions) -> Unit
) {
    val language = appLanguage()
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically) {
        PvpQuickIcon("shadow.png", ReviewIvMode.SHADOW.localizedLabel(language), ivMode == ReviewIvMode.SHADOW,
            { onModeChange(toggledReviewMode(ivMode, ReviewIvMode.SHADOW)) })
        PvpQuickIcon("purified.png", ReviewIvMode.PURIFIED.localizedLabel(language), ivMode == ReviewIvMode.PURIFIED,
            { onModeChange(toggledReviewMode(ivMode, ReviewIvMode.PURIFIED)) })
        PvpQuickIcon("level_40.png", lt(language, "Nível máximo 40", "Maximum level 40", "Nivel máximo 40"), options.maxLevel == 40,
            { onOptionsChange(options.copy(maxLevel = 40)) }, radio = true)
        PvpQuickIcon("level_50.png", lt(language, "Nível máximo 50", "Maximum level 50", "Nivel máximo 50"), options.maxLevel == 50,
            { onOptionsChange(options.copy(maxLevel = 50)) }, radio = true)
        val limit = options.effectiveMaxLevel.toInt()
        PvpQuickIcon("best_buddy.png", lt(language, "Melhor companheiro (+1 nível). Limite atual: $limit",
            "Best Buddy (+1 level). Current limit: $limit", "Mejor compañero (+1 nivel). Límite actual: $limit"), options.bestBuddy,
            { onOptionsChange(options.copy(bestBuddy = !options.bestBuddy)) })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PvpQuickIcon(asset: String, label: String, active: Boolean, onClick: () -> Unit, radio: Boolean = false) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(10.dp)
    val icon = remember(asset) {
        context.assets.open("pvp/controls/" + asset).use { input ->
            val bitmap = requireNotNull(BitmapFactory.decodeStream(input))
            // Normalize visible bounds, so transparent padding does not change icon height.
            var left = bitmap.width
            var top = bitmap.height
            var right = -1
            var bottom = -1
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                if ((bitmap.getPixel(x, y) ushr 24) > 16) {
                    left = minOf(left, x); top = minOf(top, y)
                    right = maxOf(right, x); bottom = maxOf(bottom, y)
                }
            }
            if (right >= left && bottom >= top)
                Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1).asImageBitmap()
            else bitmap.asImageBitmap()
        }
    }
    Box(Modifier.size(36.dp).clip(shape)
        .background(if (active) Color(0xFFB6CFEE).copy(alpha = 0.65f) else Color.Transparent)
        .border(0.75.dp, if (active) Color(0xFF83A6D4) else Color.Transparent, shape)
        .combinedClickable(role = if (radio) Role.RadioButton else Role.Checkbox,
            onClick = onClick, onLongClickLabel = label,
            onLongClick = { Toast.makeText(context, label, Toast.LENGTH_SHORT).show() })
        .semantics {
            contentDescription = label
            if (radio) selected = active else toggleableState = if (active)
                androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off
        }, contentAlignment = Alignment.Center) {
        Image(icon, null, Modifier.height(26.dp).aspectRatio(icon.width.toFloat() / icon.height),
            colorFilter = if (asset == "best_buddy.png")
                androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFFB37A20)) else null)
    }
}
