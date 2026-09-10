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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
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
internal val GlassAccent = Color(0xFF587BDB)

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
    Surface(
        modifier = modifier.fillMaxWidth().padding(vertical = GlassBlockMargin),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF5F4FF).copy(alpha = 0.45f),
        contentColor = Color(0xFF283047),
        border = BorderStroke(0.5.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(
            Color.White.copy(alpha = 0.45f), Color(0xFFB9CDFB).copy(alpha = 0.18f), Color.White.copy(alpha = 0.30f)
        )))
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
                            drawLine(Color(0xFFDADDEE), Offset(x, 10.dp.toPx()), Offset(x, size.height - 10.dp.toPx()), 1.dp.toPx())
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
            .clip(RoundedCornerShape(50)).background(Color(0xFFDCE5FB).copy(alpha = 0.36f))
            .selectableGroup().padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { index, label ->
            Box(
                Modifier.weight(1f).heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (index == selected) GlassAccent else Color.Transparent)
                    .selectable(index == selected, role = Role.Tab, onClick = { onSelected(index) })
                    .padding(horizontal = 5.dp, vertical = 4.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = if (index == selected) Color.White else Color(0xFF435781))
            }
        }
    }
}

@Composable
internal fun GlassGenderSegments(items: List<WeightedToggleItem>) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(GlassFieldCorner))
        .background(Color(0xFFE3E8F7).copy(alpha = 0.5f))) {
        items.forEach { item ->
            Box(Modifier.weight(1f).height(GlassFieldHeight)
                .background(if (item.selected) GlassAccent else Color.Transparent)
                .toggleable(item.selected, role = Role.Checkbox, onValueChange = { item.onClick() }),
                contentAlignment = Alignment.Center) {
                Text(item.label, fontSize = 18.sp,
                    color = if (item.selected) Color.White else Color(0xFF48516A))
            }
        }
    }
}

@Composable
internal fun GlassSwitchField(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.height(GlassFieldHeight)
        .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Normal)
        Box(Modifier.width(40.dp).height(24.dp).clip(RoundedCornerShape(50))
            .background(if (checked) GlassAccent else Color(0xFFA8ADBF)).padding(1.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.size(22.dp).shadow(1.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50)).background(Color(0xFFFAFAFF)))
        }
    }
}
/** Blur only the captured game backdrop, never the controls drawn above it. */
@Composable
internal fun GlassReviewBackdrop(bitmap: android.graphics.Bitmap?, enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    val canBlur = android.os.Build.VERSION.SDK_INT >= 31 && bitmap != null && !bitmap.isRecycled
    Box(Modifier.fillMaxWidth()) {
        if (canBlur && bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alignment = Alignment.BottomCenter,
                modifier = Modifier.matchParentSize().blur(18.dp)
            )
        }
        Box(Modifier.matchParentSize().background(
            androidx.compose.ui.graphics.Brush.linearGradient(listOf(
                Color(0xFFF4F4FF).copy(alpha = if (canBlur) 0.56f else 0.78f),
                Color(0xFFBFCFF8).copy(alpha = if (canBlur) 0.44f else 0.72f)
            ))
        ))
        content()
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
    Surface(modifier.fillMaxWidth().padding(vertical = GlassBlockMargin),
        shape = RoundedCornerShape(12.dp), color = Color(0xFFF5F4FF).copy(alpha = 0.45f),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f))) {
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

internal fun glassFieldColor(selected: Boolean) =
    if (selected) Color(0xFFDCE5FC).copy(alpha = 0.92f) else Color(0xFFF8F7FF).copy(alpha = 0.78f)

internal fun glassFieldBorder(selected: Boolean) = BorderStroke(
    0.5.dp, if (selected) GlassAccent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.25f)
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
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = GlassAccent,
        primaryContainer = Color(0xFFDCE5FC), onPrimary = Color.White)) {
        Card(modifier = modifier.clickable { }, shape = RoundedCornerShape(18.dp),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.35f)),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
            GlassReviewBackdrop(LocalGlassCapture.current, true) {
                // Keep text legible even over a busy captured screen.
                Column(Modifier.background(Color(0xFFF4F4FF).copy(alpha = 0.30f)), content = content)
            }
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