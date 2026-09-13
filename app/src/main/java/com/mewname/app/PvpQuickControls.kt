package com.mewname.app

import android.widget.Toast
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
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.size(48.dp).clip(shape)
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
        AssetImageIcon("pvp/controls/$asset", null, Modifier.size(30.dp),
            tint = if (asset == "best_buddy.png") Color(0xFFB37A20) else null)
    }
}
