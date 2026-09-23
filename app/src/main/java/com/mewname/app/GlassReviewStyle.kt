package com.mewname.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier


import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val LocalGlassCapture = staticCompositionLocalOf<android.graphics.Bitmap?> { null }
internal val LocalGlassReviewStyle = staticCompositionLocalOf { false }
internal val LocalGlassSectionContent = staticCompositionLocalOf { false }
internal val LocalGlassFieldGroup = staticCompositionLocalOf { false }
internal val GlassBlockInset = 2.dp
internal val GlassBlockMargin = 1.5.dp
internal val GlassLabelHeight = 16.dp
internal val GlassFieldHeight = 28.dp
internal val GlassFieldCorner = 6.dp


/** One shared surface per row; individual controls retain their original callbacks. */
@Composable
internal fun GlassFieldRow(
    modifier: Modifier = Modifier,
    weights: List<Float> = emptyList(),
    legacySpacing: androidx.compose.ui.unit.Dp = 8.dp,
    legacyAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit
) {
    if (!LocalGlassReviewStyle.current || LocalGlassSectionContent.current) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(legacySpacing), verticalAlignment = legacyAlignment, content = content)
        return
    }
    val dividerColor = LocalAppAppearance.current.border
    AppSectionCard(
        modifier = modifier.fillMaxWidth().padding(vertical = GlassBlockMargin),
        shape = RoundedCornerShape(12.dp)
    ) {
        CompositionLocalProvider(LocalGlassFieldGroup provides true) {
            Row(
                Modifier.fillMaxWidth().drawBehind {
                    if (weights.size > 1) {
                        val total = weights.sum()
                        var x = 10.dp.toPx()
                        val gap = 8.dp.toPx()
                        val available = size.width - 20.dp.toPx() - gap * (weights.size - 1)
                        weights.dropLast(1).forEach {
                            x += it / total * available + gap / 2
                            drawLine(dividerColor, Offset(x, 10.dp.toPx()), Offset(x, size.height - 10.dp.toPx()), 1.dp.toPx())
                            x += gap / 2
                        }
                    }
                }.padding(horizontal = 10.dp, vertical = GlassBlockInset),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

@Composable
internal fun GlassTabs(labels: List<String>, selected: Int, onSelected: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 3.5.dp, bottom = 0.dp)
            .clip(RoundedCornerShape(50)).background(appControlFill())
            .selectableGroup().padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { index, label ->
            Box(
                Modifier.weight(1f).heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (index == selected) appControlFill(true) else Color.Transparent)
                    .selectable(index == selected, role = Role.Tab, onClick = { onSelected(index) })
                    .padding(horizontal = 5.dp, vertical = 4.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = appControlInk())
            }
        }
    }
}

@Composable
internal fun GlassGenderSegments(items: List<WeightedToggleItem>) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(GlassFieldCorner))
        .background(appControlFill())) {
        items.forEach { item ->
            Box(Modifier.weight(1f).height(GlassFieldHeight)
                .background(if (item.selected) appControlFill(true) else Color.Transparent)
                .toggleable(item.selected, role = Role.Checkbox, onValueChange = { item.onClick() }),
                contentAlignment = Alignment.Center) {
                Text(item.label, fontSize = 18.sp,
                    color = appControlInk())
            }
        }
    }
}

/** Use the same background palette as the app; captured images remain OCR/debug input only. */
@Composable
internal fun GlassReviewBackdrop(
    @Suppress("UNUSED_PARAMETER") bitmap: android.graphics.Bitmap?,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }
    val appearance = LocalAppAppearance.current
    CompositionLocalProvider(LocalContentColor provides appearance.text) {
        Box(Modifier.fillMaxWidth().background(
            androidx.compose.ui.graphics.Brush.verticalGradient(appearance.background)
        )) { content() }
    }
}
@Composable
internal fun GlassSection(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    if (!LocalGlassReviewStyle.current || LocalGlassSectionContent.current) {
        Column(modifier, verticalArrangement = verticalArrangement, content = content)
        return
    }
    AppSectionCard(modifier.fillMaxWidth().padding(vertical = GlassBlockMargin),
        shape = RoundedCornerShape(12.dp)) {
        CompositionLocalProvider(LocalGlassSectionContent provides true) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = GlassBlockInset), verticalArrangement = verticalArrangement, content = content)
        }
    }
}

/** The arrow overlays its own end slot; it does not push the value off centre. */
@Composable
internal fun GlassFieldValue(value: String, trailing: (@Composable () -> Unit)? = null, small: Boolean = false) {
    Box(Modifier.fillMaxWidth().height(GlassFieldHeight).padding(horizontal = 2.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center) {
        Text(value, modifier = Modifier.fillMaxWidth().padding(horizontal = if (trailing != null) 14.dp else 2.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontSize = if (small) 11.sp else 12.sp,
            fontWeight = if (value.isNotBlank() && value != "-") FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        if (trailing != null) Box(Modifier.align(Alignment.CenterEnd).width(14.dp), contentAlignment = Alignment.Center) { trailing() }
    }
}
@Composable
internal fun Modifier.glassFieldSize(): Modifier =
    if (LocalGlassReviewStyle.current) height(GlassFieldHeight) else this

@Composable
internal fun glassFieldColor(selected: Boolean) = appControlFill(selected)

@Composable
internal fun glassFieldBorder(selected: Boolean) = BorderStroke(
    0.75.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    else LocalAppAppearance.current.border
)
@Composable
internal fun ReviewModalCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(),
    content: @Composable ColumnScope.() -> Unit
) {
    if (!LocalGlassReviewStyle.current) {
        Card(modifier = modifier, colors = colors, content = content)
        return
    }
    Card(modifier = modifier.clickable { }, shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.75.dp, LocalAppAppearance.current.border),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent,
            contentColor = LocalAppAppearance.current.text)) {
        GlassReviewBackdrop(LocalGlassCapture.current, true) {
            Column(content = content)
        }
    }
}
@Composable
internal fun ReviewModalHeader(title: String, onDismiss: () -> Unit) {
    if (!LocalGlassReviewStyle.current) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onDismiss) { Text(lt(appLanguage(), "Fechar", "Close", "Cerrar")) }
        }
        return
    }
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        Text(title, Modifier.fillMaxWidth().padding(horizontal = 48.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd).size(48.dp)) {
            Icon(Icons.Default.Close, contentDescription = lt(appLanguage(), "Fechar", "Close", "Cerrar"),
                modifier = Modifier.size(20.dp))
        }
    }
}